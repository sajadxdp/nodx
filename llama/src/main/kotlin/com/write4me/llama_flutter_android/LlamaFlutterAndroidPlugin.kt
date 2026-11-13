package com.write4me.llama_flutter_android

/**
 * Thin JVM bridge over libllama_jni.so. Ported from the Flutter plugin of the
 * same name; the JNI symbols in jni_wrapper.cpp bind to this exact class
 * name and package, so both are kept even though the Flutter glue is gone.
 *
 * All native calls are blocking and must run off the main thread. The
 * generate call streams tokens by invoking [tokenCallback] with String
 * pieces; return null from the callback to continue generating.
 */
object LlamaFlutterAndroidPlugin {

    init {
        System.loadLibrary("llama_jni")
    }

    /** Loads CPU (and, when allowed, Vulkan) backends from [libDir].
     * Returns true when a GPU backend is available. */
    fun initBackends(libDir: String, allowGpu: Boolean): Boolean =
        nativeInitBackends(libDir, allowGpu)

    /** GPU capabilities as JSON: name, vram, recommendedLayers. */
    fun detectGpu(): String = nativeDetectGpu()

    /** Loads a GGUF model. [progressCallback] receives Double 0.0–1.0. */
    fun loadModel(
        path: String,
        nThreads: Long,
        ctxSize: Long,
        nGpuLayers: Long,
        progressCallback: (Any?) -> Any?,
    ): Unit = nativeLoadModel(path, nThreads, ctxSize, nGpuLayers, progressCallback)

    /** Loads a vision projector. Returns null on success, else an error. */
    fun loadMmproj(path: String, nThreads: Long): String? = nativeLoadMmproj(path, nThreads)

    /** Runs one blocking generation. [tokenCallback] receives String pieces. */
    fun generate(
        prompt: String,
        imagePath: String?,
        maxTokens: Long,
        temperature: Double,
        topP: Double,
        topK: Long,
        minP: Double,
        typicalP: Double,
        repeatPenalty: Double,
        frequencyPenalty: Double,
        presencePenalty: Double,
        repeatLastN: Long,
        mirostat: Long,
        mirostatTau: Double,
        mirostatEta: Double,
        seed: Long,
        penalizeNewline: Boolean,
        tokenCallback: (Any?) -> Any?,
    ): Unit = nativeGenerate(
        prompt, imagePath, maxTokens, temperature, topP, topK, minP, typicalP,
        repeatPenalty, frequencyPenalty, presencePenalty, repeatLastN,
        mirostat, mirostatTau, mirostatEta, seed, penalizeNewline, tokenCallback,
    )

    fun stop(): Unit = nativeStop()
    fun freeModel(): Unit = nativeFreeModel()
    fun tokensUsed(): Int = nativeGetTokensUsed()
    fun contextSize(): Int = nativeGetContextSize()
    fun clearContext(): Unit = nativeClearContext()

    // ── JNI entry points (signatures fixed by jni_wrapper.cpp) ──

    private external fun nativeInitBackends(libDir: String, allowGpu: Boolean): Boolean
    private external fun nativeDetectGpu(): String
    private external fun nativeLoadModel(
        path: String, nThreads: Long, ctxSize: Long, nGpuLayers: Long,
        progressCallback: Any,
    )

    private external fun nativeLoadMmproj(path: String, nThreads: Long): String?
    private external fun nativeGenerate(
        prompt: String, imagePath: String?, maxTokens: Long,
        temperature: Double, topP: Double, topK: Long, minP: Double, typicalP: Double,
        repeatPenalty: Double, frequencyPenalty: Double, presencePenalty: Double,
        repeatLastN: Long, mirostat: Long, mirostatTau: Double, mirostatEta: Double,
        seed: Long, penalizeNewline: Boolean, tokenCallback: Any,
    )

    private external fun nativeStop()
    private external fun nativeFreeModel()
    private external fun nativeGetTokensUsed(): Int
    private external fun nativeGetContextSize(): Int
    private external fun nativeClearContext()
}
