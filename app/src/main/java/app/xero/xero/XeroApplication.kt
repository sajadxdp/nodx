package app.xero.xero

import android.app.Application
import app.xero.xero.data.ChatDb
import app.xero.xero.data.ChatRepository
import app.xero.xero.data.SettingsStore
import app.xero.xero.data.cloud.CloudEngine
import app.xero.xero.domain.ChatEngine
import app.xero.xero.domain.FakeEngine
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Manual dependency container; the app is small enough to not need a
 * framework. Swap points: engines route to real backends per mode. */
class AppContainer(app: Application) {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .build()

    val settings: SettingsStore = SettingsStore(app)
    val chatRepository: ChatRepository = ChatRepository(ChatDb(app))
    val cloudEngine: ChatEngine = CloudEngine(http)
    val localEngine: ChatEngine = FakeEngine() // Phase 3: llama.cpp engine
    val fakeEngine: ChatEngine = FakeEngine()

    /** Cross-screen request: Sessions → Chat "open this session id". */
    val openSessionRequest = MutableStateFlow<String?>(null)
}

class XeroApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
