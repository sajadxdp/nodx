# nodx

A native Android LLM chat client: on-device GGUF inference (llama.cpp) plus
cloud providers, built with Kotlin + Jetpack Compose + Material You.

Ported from a Flutter reference client ([PrivateLM], MIT) to a fully native
stack; the llama.cpp JNI engine and chat-template registry were carried over
and de-Fluttered.

## What's inside

| Layer | Choice |
| --- | --- |
| UI | Jetpack Compose, Material 3, dynamic color (Material You) |
| State | ViewModel + StateFlow (no DI framework; manual container) |
| Persistence | SQLite (sessions/messages/models) + DataStore (settings) |
| Cloud | OkHttp SSE streaming, OpenAI-compatible providers |
| Local | llama.cpp via JNI (`:llama` module), per-Arm-generation CPU backends |

## Feature status

- ✅ **Chat** — streaming, cancelable, full-history context, error bubbles with
  Retry, Regenerate (drops the last reply and re-asks), copy on long-press,
  light markdown (bold, inline code, fenced blocks), live stats line
  (engine · tok/s · context fill), persisted sessions
- ✅ **Cloud engines** — OpenAI, OpenRouter, DeepSeek, Kimi, **Anthropic**
  (Messages API), **Google** (streamGenerateContent SSE), custom
  OpenAI-compatible endpoints; fixture-tested parsers for all three protocols
- ✅ **Local engine** — GGUF models on-device via llama.cpp; device-tier
  auto-config (context size, threads); GGUF header metadata parsing; loading a
  model auto-switches chat to Local
- ✅ **Model catalog & downloader** — curated list filtered by device RAM,
  HTTP-Range resume, pause, live speed/ETA
- ✅ **Sessions** — rename (dialog), delete with confirmation, relative times,
  opening restores the full transcript
- ✅ **Settings** — provider/keys/model, system prompt, temperature, max
  tokens, engine mode, theme
- ⏳ **Later** — vision (mmproj), Vulkan backend (NDK shader limits), release
  signing

## Architecture

```
app/
├── domain/          # pure Kotlin: ChatEngine contract, models
├── data/
│   ├── local/       # LlamaCppEngine, GGUF reader, device profile
│   ├── cloud/       # CloudEngine (OpenAI-compat SSE)
│   ├── ChatDb.kt    # SQLite repository
│   ├── SettingsStore.kt
│   ├── ModelCatalog.kt / ModelDownloader.kt
├── ui/              # Compose screens: chat, sessions, models, settings
llama/               # llama.cpp module: JNI wrapper + chat templates
                     # (namespace kept: JNI symbol binding)
```

The core contract — every backend implements `ChatEngine`:

```kotlin
interface ChatEngine {
    suspend fun load(request: LoadRequest)
    val isReady: Boolean
    fun generate(request: GenerateRequest): Flow<TokenEvent>
    fun cancel()
    val stats: EngineStats?
}
```

## Build

```bash
./gradlew assembleDebug     # debug APK
./gradlew test              # unit tests (SSE, GGUF, templates)
```

Requirements: JDK 17, Android SDK 36, NDK (bundled CMake 3.22.1).

First run: pick a provider in **Settings → Cloud provider**, paste an API
key — or go offline-first in **Models**, download a GGUF (device-tier
filtered), tap **Load**, and switch **Engine mode → Local**.

## Notes

- Local inference targets arm64 devices (minSdk 28). The Vulkan backend is
  compiled out until the NDK's shader compiler catches up; CPU backends
  cover every Arm generation and are picked at runtime.
- `llama/` keeps the `com.write4me.llama_flutter_android` namespace because
  JNI symbols in `jni_wrapper.cpp` bind to that exact class path.

[PrivateLM]: https://github.com/orailnoor/cross-platform-llm-client

## Brand

The identity comes from the logo: a violet-tinted near-black tile with a
chiseled white **N** and a violet accent (`#883DEA`). The app ships with that
scheme as its default theme — dark surfaces step down from `#17171F` to
`#0A0B10`, the accent drives primaries in both light and dark. Material You
(wallpaper-derived) dynamic color is available as an opt-in in
**Settings → Appearance → Dynamic color** (Android 12+).

## Release

Releases are signed APKs published on [GitHub Releases](https://github.com/sajadxdp/nodx/releases).
Signing uses a local `keystore.properties` (gitignored) pointing at a PKCS12
keystore — **back up that keystore**: it is the app's update identity; losing
it means a new signature and uninstall/reinstall for every user.
