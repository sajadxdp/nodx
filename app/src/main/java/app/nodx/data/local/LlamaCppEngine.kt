package app.nodx.data.local

import android.content.Context
import app.nodx.domain.ChatEngine
import app.nodx.domain.EngineRole
import app.nodx.domain.EngineStats
import app.nodx.domain.GenerateRequest
import app.nodx.domain.LoadRequest
import app.nodx.domain.TokenEvent
import com.write4me.llama_flutter_android.ChatTemplateManager
import com.write4me.llama_flutter_android.LlamaFlutterAndroidPlugin as Bridge
import com.write4me.llama_flutter_android.TemplateChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * On-device GGUF engine over the ported llama.cpp JNI bridge. Native calls
 * run on one dedicated thread (the wrapper keeps global model state);
 * generations serialize behind a mutex.
 */
class LlamaCppEngine(private val context: Context) : ChatEngine {

    private val nativeThread = Executors.newSingleThreadExecutor { r ->
        Thread(r, "llama-native").apply { priority = Thread.MAX_PRIORITY - 1 }
    }
    private val loadMutex = Mutex()
    private val ready = AtomicBoolean(false)
    private val cancelRequested = AtomicBoolean(false)

    @Volatile private var gpuInfo: GpuInfo? = null
    @Volatile private var contextSize: Int = 2048
    @Volatile private var loadedPath: String? = null
    @Volatile private var tokensThisGeneration = 0
    @Volatile private var generationStartMs = 0L

    /** 0.0–1.0 while a model loads (best-effort UI signal). */
    @Volatile var loadProgress: Double = 0.0
        private set

    override val isReady: Boolean get() = ready.get()
    val gpuName: String? get() = gpuInfo?.name
    val loadedModelPath: String? get() = loadedPath

    override suspend fun load(request: LoadRequest) {
        check(request is LoadRequest.Local) { "LlamaCppEngine only accepts LocalLoadRequest" }
        val req = request
        loadMutex.withLock {
            runNative {
                val device = DeviceProfile(context)

                // Backends + optional Vulkan probe
                val gpuAvailable = Bridge.initBackends(libDir, allowGpu = true)
                if (gpuAvailable) {
                    Bridge.detectGpu().let { parseGpuInfo(it) }?.let { gpuInfo = it }
                }

                // Resolve parameters: explicit > GGUF metadata > device tier
                val meta = GgufMetadata.read(req.modelPath)
                val ctx = when {
                    req.contextLength > 0 -> req.contextLength
                    meta?.contextLength != null ->
                        meta.contextLength.toInt().coerceAtMost(device.tier.recommendedContext)
                    else -> device.tier.recommendedContext
                }
                val threads = if (req.threads > 0) req.threads else device.tier.recommendedThreads
                val gpuLayers = when {
                    req.gpuLayers >= 0 -> req.gpuLayers.toLong()
                    gpuInfo != null -> gpuInfo!!.recommendedLayers.toLong()
                    else -> 0L
                }

                Bridge.loadModel(req.modelPath, threads.toLong(), ctx.toLong(), gpuLayers) { p ->
                    (p as? Double)?.let { loadProgress = it }
                    null
                }
                contextSize = Bridge.contextSize()
                loadedPath = req.modelPath
                loadProgress = 1.0
                ready.set(true)
            }
        }
    }

    fun unload() {
        runNative { Bridge.freeModel() }
        ready.set(false)
        loadedPath = null
        loadProgress = 0.0
    }

    override fun generate(request: GenerateRequest): Flow<TokenEvent> = channelFlow {
        if (!ready.get()) {
            send(TokenEvent.Failed(IllegalStateException("No model loaded")))
            return@channelFlow
        }
        cancelRequested.set(false)
        tokensThisGeneration = 0
        generationStartMs = System.currentTimeMillis()

        val template = ChatTemplateManager.detectTemplate(loadedPath ?: "")
        val prompt = template.format(
            request.messages.map {
                TemplateChatMessage(
                    role = when (it.role) {
                        EngineRole.system -> "system"
                        EngineRole.user -> "user"
                        EngineRole.assistant -> "assistant"
                    },
                    content = it.text,
                )
            }
        )

        try {
            withContext(Dispatchers.IO) {
                Bridge.generate(
                    prompt = prompt,
                    imagePath = null,
                    maxTokens = request.maxTokens.toLong(),
                    temperature = request.temperature,
                    topP = 0.95,
                    topK = 40,
                    minP = 0.05,
                    typicalP = 1.0,
                    repeatPenalty = 1.1,
                    frequencyPenalty = 0.0,
                    presencePenalty = 0.0,
                    repeatLastN = 64,
                    mirostat = 0,
                    mirostatTau = 5.0,
                    mirostatEta = 0.1,
                    seed = -1,
                    penalizeNewline = false,
                ) { token ->
                    if (cancelRequested.get()) java.lang.Boolean.TRUE // stop signal
                    else {
                        val piece = token as? String
                        if (!piece.isNullOrEmpty()) {
                            trySend(TokenEvent.Delta(piece))
                            tokensThisGeneration++
                        }
                        null
                    }
                }
            }
            if (!cancelRequested.get()) send(TokenEvent.Done())
        } catch (e: Exception) {
            if (!cancelRequested.get()) send(TokenEvent.Failed(e))
        }
        // channelFlow closes the channel when the block returns.
    }

    override fun cancel() {
        cancelRequested.set(true)
        // Flips the wrapper's stop flag; safe from any thread.
        runCatching { nativeThread.submit(java.util.concurrent.Callable { Bridge.stop() }).get() }
    }

    override val stats: EngineStats?
        get() {
            if (!ready.get()) return null
            val used = runCatching {
                nativeThread.submit(java.util.concurrent.Callable { Bridge.tokensUsed() }).get()
            }.getOrNull() ?: return null
            val elapsed = (System.currentTimeMillis() - generationStartMs) / 1000.0
            val tps = if (elapsed > 0.2 && tokensThisGeneration > 0)
                tokensThisGeneration / elapsed else 0.0
            return EngineStats(tps, used, contextSize)
        }

    /** Directory holding the runtime-loaded backend .so files. */
    var libDir: String = ""
        get() = field.ifEmpty {
            runCatching {
                context.applicationInfo.nativeLibraryDir
            }.getOrNull().orEmpty()
        }

    private fun <T> runNative(block: () -> T): T =
        runCatching { nativeThread.submit(block).get() }
            .getOrElse { throw it.cause ?: it }
}
