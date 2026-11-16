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

- ✅ **Chat** — streaming, cancelable, full-history context; messages as
  full-width cards with role headers (You / Assistant); failures as bordered
  blocks with Retry; Regenerate; copy on long-press; light markdown (bold,
  inline code, fenced blocks); live stats line (engine · tok/s · context fill);
  persisted sessions; a pinned-to-bottom transcript that yields to your
  scroll and resumes
- ✅ **Composer** — engine chip (switch Local/Cloud from the chat itself) +
  live model chip (loaded GGUF filename or provider model) above the input;
  circular send that becomes a red stop while streaming
- ✅ **Empty state** — a swimming sea scene (ASCII fish and hearts in brand
  colors) around a centered greeting and a "What's on your mind?" input
- ✅ **Cloud engines** — OpenAI, OpenRouter, DeepSeek, Kimi, **Anthropic**
  (Messages API), **Google** (streamGenerateContent SSE), custom
  OpenAI-compatible endpoints; fixture-tested parsers for all three protocols
- ✅ **Local engine** — GGUF models on-device via llama.cpp; device-tier
  auto-config (context size, threads); GGUF header metadata parsing;
  runtime-selected per-Arm-generation CPU backends (16 KB-page aligned,
  soname-resolved so they load under `extractNativeLibs=false` packaging)
- ✅ **Model catalog & downloader** — curated list filtered by device RAM,
  HTTP-Range resume, pause, live speed/ETA
- ✅ **Sessions** — quiet cards with relative times, rename (dialog), delete
  with confirmation, opening restores the full transcript
- ✅ **Settings** — grouped-card layout: provider/keys/model, system prompt,
  temperature, max tokens, engine mode, theme, dynamic color, plus an
  **About** section (version, developer, GitHub, issue tracker)
- ✅ **Releases** — signed APKs (arm64-v8a, armeabi-v7a, x86_64, universal)
  published via GitHub Releases with CI building all four
- ⏳ **Later** — vision (mmproj), Vulkan backend (NDK shader limits)

## Install

Grab a signed APK from
[Releases](https://github.com/sajadxdp/nodx/releases/latest) — arm64-v8a
covers most phones (the Galaxy-class target), universal fits everything
back to API 28.

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

## Design

The UI language follows [dzianisv/opencode-mobile]: flat neutral surfaces
(`#F4F4F6` page with white cards in light, `#0A0B10` page with `#1A1B23`
cards in dark), hairline separators, accent-only color, full-width message
cards with role headers, and a composer toolbar with engine/model chips.
The brand violet (`#883DEA` / `#B073F5`) and the adaptive launcher icon
(minimal stroke-drawn **N** inside Google's 66 dp adaptive-icon safe zone)
are nodx's own. Material You dynamic color remains an opt-in.

## Build

```bash
./gradlew assembleRelease   # signed release APKs (4 ABIs)
./gradlew test              # unit tests (SSE, GGUF, templates, engine)
```

Requirements: JDK 17, Android SDK 36, NDK (bundled CMake 3.22.1).

First run: pick a provider in **Settings → Cloud provider**, paste an API
key — or go offline-first in **Models**, download a GGUF (device-tier
filtered), tap **Load**, and switch engines from the composer's
**Local/Cloud chip** — no trip to Settings needed.

## Notes

- Local inference targets Arm devices (minSdk 28). The Vulkan backend is
  compiled out until the NDK's shader compiler catches up; CPU backends
  cover every Arm generation and are picked at runtime.
- Runtime-loaded backend modules resolve by soname (the APK keeps native
  libraries inside the APK; `nativeLibraryDir` is empty under
  `extractNativeLibs=false`) and failures log dlerror instead of failing
  silently.
- `llama/` keeps the `com.write4me.llama_flutter_android` namespace because
  JNI symbols in `jni_wrapper.cpp` bind to that exact class path.

[PrivateLM]: https://github.com/orailnoor/cross-platform-llm-client
[dzianisv/opencode-mobile]: https://github.com/dzianisv/opencode-mobile

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
