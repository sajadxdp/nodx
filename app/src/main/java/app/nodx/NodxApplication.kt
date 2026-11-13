package app.nodx

import android.app.Application
import app.nodx.data.ChatDb
import app.nodx.data.ChatRepository
import app.nodx.data.ModelCatalog
import app.nodx.data.ModelDownloader
import app.nodx.data.SettingsStore
import app.nodx.data.cloud.CloudEngine
import app.nodx.data.local.DeviceProfile
import app.nodx.data.local.LlamaCppEngine
import app.nodx.domain.ChatEngine
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Manual dependency container; the app is small enough to not need a
 * framework. Engines route to real backends per mode. */
class AppContainer(app: Application) {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .build()

    val settings: SettingsStore = SettingsStore(app)
    val chatRepository: ChatRepository = ChatRepository(ChatDb(app))
    val cloudEngine: ChatEngine = CloudEngine(http)
    val localEngine: LlamaCppEngine = LlamaCppEngine(app)
    val deviceProfile: DeviceProfile = DeviceProfile(app)
    val modelDownloader = ModelDownloader(http, app)
    val catalog = ModelCatalog(app)

    /** Cross-screen request: Sessions → Chat "open this session id". */
    val openSessionRequest = MutableStateFlow<String?>(null)
}

class NodxApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
