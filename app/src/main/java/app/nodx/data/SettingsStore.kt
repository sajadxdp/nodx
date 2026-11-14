package app.nodx.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.nodx.domain.LoadRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "nodx_settings")

/** Cloud providers with OpenAI-compatible chat endpoints, plus Anthropic and
 * Google which get their own adapters in later phases. */
enum class Provider(val label: String, val defaultBaseUrl: String, val defaultModel: String) {
    OPENAI("OpenAI", "https://api.openai.com/v1", "gpt-5.2"),
    OPENROUTER("OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-4o-mini"),
    DEEPSEEK("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat"),
    KIMI("Kimi (Moonshot)", "https://api.moonshot.ai/v1", "kimi-k2.6"),
    ANTHROPIC("Anthropic", "https://api.anthropic.com/v1", "claude-sonnet-4-6"),
    GOOGLE("Google", "https://generativelanguage.googleapis.com/v1beta", "gemini-2.5-flash"),
    CUSTOM("Custom (OpenAI-compatible)", "", "");
}

enum class ThemeSetting { LIGHT, DARK, SYSTEM }

enum class EngineModeSetting { LOCAL, CLOUD }

data class Settings(
    val provider: Provider = Provider.OPENAI,
    val baseUrl: String = Provider.OPENAI.defaultBaseUrl,
    val apiKey: String = "",
    val model: String = Provider.OPENAI.defaultModel,
    val theme: ThemeSetting = ThemeSetting.DARK,
    val dynamicColor: Boolean = false,
    val engineMode: EngineModeSetting = EngineModeSetting.CLOUD,
    val systemPrompt: String = "",
    val temperature: Double = 0.7,
    val maxTokens: Int = 1024,
)

/** DataStore-backed typed settings. Keys never leave the device except to
 * the provider endpoint during generation. */
class SettingsStore(private val context: Context) {

    private object Keys {
        val provider = stringPreferencesKey("provider")
        val baseUrl = stringPreferencesKey("base_url")
        val apiKey = stringPreferencesKey("api_key")
        val model = stringPreferencesKey("model")
        val theme = stringPreferencesKey("theme")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val engineMode = stringPreferencesKey("engine_mode")
        val systemPrompt = stringPreferencesKey("system_prompt")
        val temperature = stringPreferencesKey("temperature")
        val maxTokens = stringPreferencesKey("max_tokens")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        val provider = p[Keys.provider]?.let { stored ->
            Provider.entries.firstOrNull { it.name == stored }
        } ?: Provider.OPENAI
        Settings(
            provider = provider,
            baseUrl = p[Keys.baseUrl] ?: provider.defaultBaseUrl,
            apiKey = p[Keys.apiKey] ?: "",
            model = p[Keys.model] ?: provider.defaultModel,
            theme = p[Keys.theme]?.let { ThemeSetting.valueOf(it) } ?: ThemeSetting.DARK,
            dynamicColor = p[Keys.dynamicColor] ?: false,
            engineMode = p[Keys.engineMode]?.let { EngineModeSetting.valueOf(it) }
                ?: EngineModeSetting.CLOUD,
            systemPrompt = p[Keys.systemPrompt] ?: "",
            temperature = (p[Keys.temperature]?.toDoubleOrNull()) ?: 0.7,
            maxTokens = (p[Keys.maxTokens]?.toIntOrNull()) ?: 1024,
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setProvider(provider: Provider) {
        context.dataStore.edit { p ->
            p[Keys.provider] = provider.name
            p[Keys.baseUrl] = provider.defaultBaseUrl
            p[Keys.model] = provider.defaultModel
        }
    }

    suspend fun setBaseUrl(url: String) = context.dataStore.edit { it[Keys.baseUrl] = url.trim() }
    suspend fun setApiKey(key: String) = context.dataStore.edit { it[Keys.apiKey] = key.trim() }
    suspend fun setModel(model: String) = context.dataStore.edit { it[Keys.model] = model.trim() }
    suspend fun setTheme(theme: ThemeSetting) = context.dataStore.edit { it[Keys.theme] = theme.name }
    suspend fun setDynamicColor(on: Boolean) = context.dataStore.edit { it[Keys.dynamicColor] = on }
    suspend fun setEngineMode(mode: EngineModeSetting) =
        context.dataStore.edit { it[Keys.engineMode] = mode.name }

    suspend fun setSystemPrompt(prompt: String) =
        context.dataStore.edit { it[Keys.systemPrompt] = prompt }

    suspend fun setTemperature(value: Double) =
        context.dataStore.edit { it[Keys.temperature] = value.toString() }

    suspend fun setMaxTokens(value: Int) =
        context.dataStore.edit { it[Keys.maxTokens] = value.toString() }

    /** Cloud load request built from current settings; null when not configured. */
    suspend fun cloudRequest(): LoadRequest.Cloud? {
        val s = current()
        if (s.apiKey.isBlank() || s.model.isBlank()) return null
        val baseUrl = if (s.provider == Provider.CUSTOM && s.baseUrl.isBlank()) return null
        else s.baseUrl
        return LoadRequest.Cloud(
            provider = s.provider.name,
            baseUrl = baseUrl,
            apiKey = s.apiKey,
            model = s.model,
        )
    }
}
