package app.nodx.ui.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nodx.AppContainer
import app.nodx.data.CatalogModel
import app.nodx.data.DownloadState
import app.nodx.data.DownloadedModel
import app.nodx.data.local.GgufMetadata
import app.nodx.data.EngineModeSetting
import app.nodx.domain.LoadRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ModelsUiState(
    val catalog: List<CatalogModel> = emptyList(),
    val downloaded: List<DownloadedModel> = emptyList(),
    val downloads: Map<String, DownloadState> = emptyMap(),
    val loadedPath: String? = null,
    val isLoadingModel: Boolean = false,
    val loadProgress: Double = 0.0,
    val deviceInfo: String = "",
    val error: String? = null,
)

class ModelsViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(ModelsUiState())
    val state: StateFlow<ModelsUiState> = _state.asStateFlow()

    init {
        refresh()
        observe()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                catalog = container.catalog.forDevice(
                    container.deviceProfile.maxModelGb + 2.0,
                ),
                downloaded = container.chatRepository.downloadedModels(),
                deviceInfo = container.deviceProfile.run {
                    "${tier.label} tier · ${"%.1f".format(totalRamGb)} GB RAM"
                },
            )
        }
    }

    private fun observe() {
        viewModelScope.launch {
            container.modelDownloader.states.collect { dl ->
                _state.value = _state.value.copy(downloads = dl)
            }
        }
        viewModelScope.launch {
            // Register finished downloads in the DB with parsed GGUF metadata.
            // Parse off the main thread; a corrupt file must never crash the app.
            container.modelDownloader.states.collect { dl ->
                dl.forEach { (id, st) ->
                    if (st is DownloadState.Done && !registered.contains(id)) {
                        registered.add(id)
                        val entry = container.catalog.models.firstOrNull { it.id == id }
                        if (entry != null) {
                            val meta = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                runCatching { GgufMetadata.read(st.path) }.getOrNull()
                            }
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                container.chatRepository.upsertDownloadedModel(
                                    DownloadedModel(
                                        id = entry.id,
                                        filename = entry.filename,
                                        sizeBytes = java.io.File(st.path).length(),
                                        contextLength = meta?.contextLength,
                                        chatTemplate = meta?.chatTemplate,
                                        addedAt = System.currentTimeMillis(),
                                    )
                                )
                            }
                            refresh()
                        }
                    }
                }
            }
        }
    }

    private val registered = mutableSetOf<String>()

    fun download(model: CatalogModel) {
        viewModelScope.launch { container.modelDownloader.download(model) }
    }

    fun pause(id: String) = container.modelDownloader.pause(id)

    fun delete(model: DownloadedModel) {
        viewModelScope.launch {
            if (container.localEngine.loadedModelPath?.endsWith(model.filename) == true) {
                container.localEngine.unload()
            }
            container.modelDownloader.delete(model.filename)
            container.chatRepository.deleteDownloadedModel(model.id)
            refresh()
        }
    }

    /** After a model is loaded: flip engine mode to Local so chat uses it. */
    fun useInChat() {
        viewModelScope.launch { container.settings.setEngineMode(EngineModeSetting.LOCAL) }
    }

    /** Loads (or unloads) a downloaded model into the local engine. */
    fun toggleLoad(model: DownloadedModel) {
        val engine = container.localEngine
        if (engine.loadedModelPath?.endsWith(model.filename) == true) {
            engine.unload()
            _state.value = _state.value.copy(loadedPath = null, isLoadingModel = false)
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingModel = true, loadProgress = 0.0, error = null)
            try {
                engine.load(
                    LoadRequest.Local(
                        modelPath = container.modelDownloader.pathFor(model.filename).absolutePath,
                        contextLength = 0, // engine picks from metadata + device tier
                    )
                )
                _state.value = _state.value.copy(
                    loadedPath = engine.loadedModelPath,
                    isLoadingModel = false,
                )
                if (engine.isReady) useInChat()
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoadingModel = false,
                    error = e.message ?: e.toString(),
                )
            }
        }
    }
}
