<div align="center">

# Axora

**AI image & text generation in your pocket — powered by [Pollinations.ai](https://pollinations.ai)**

**English** · [Русский](README.ru.md)

[![Android CI](https://github.com/denvisss/AxoraAI/actions/workflows/android.yml/badge.svg?style=for-the-badge)](https://github.com/denvisss/AxoraAI/actions/workflows/android.yml)
[![Made with Pollinations](https://raw.githubusercontent.com/pollinations/pollinations/main/packages/ui/src/brand/badge-made-with.svg)](https://pollinations.ai/?ref=badge)
[![Release](https://img.shields.io/github/v/release/denvisss/AxoraAI?style=for-the-badge&label=Release&color=2ea44f)](https://github.com/denvisss/AxoraAI/releases/latest)
[![License: MIT](https://img.shields.io/github/license/denvisss/AxoraAI?style=for-the-badge&color=blue)](LICENSE)
[![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/about/versions/oreo)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)

</div>

---

Axora is an Android client for prompt-based media generation: images and text are available today, video and audio are planned for upcoming updates. Generation runs through the public [pollinations.ai](https://pollinations.ai) API. The interface ships in Russian and English: Russian is the default, the language is selectable in settings.

> Axora is an independent open-source project, not affiliated with Pollinations.ai and not an official product of it. The service provides only the compute API and access-key issuance.

## ✨ Features

- **Separate chats per generation type.** Tabs for Image, Text, Video and Audio, each with its own history stream. Video and Audio are marked "Soon" and unavailable until release.
- **Image generation.** Free keyless mode (Flux, up to 768×768) and keyed high-resolution modes (up to 1536×1536). Aspect ratios 1:1, 3:4 and 4:3 work in both modes. Instruction-based editing of an existing image (image-to-image).
- **Text generation.** A keyless chat model (rate-limited) or a keyed model at no charge; the model sees the context of the tab's recent messages.
- **Voice input.** Russian, hold-to-talk semantics — hold to speak, release to send (`SpeechRecognizer`); with TalkBack the microphone acts as a toggle.
- **Automatic prompt translation.** Cyrillic prompts are translated to English before media generation (can be disabled).
- **Image viewer.** Full-screen viewing with swiping through all chat images, zoom up to 5× and panning; download and share right from the viewer.
- **Working with results.** Save to gallery, share, copy text, play video/audio in chat, auto-save new media (can be disabled), regenerate from the original prompt ("Generate again").
- **Generation control.** Cancel the current generation via a button in the progress indicator; partial files are removed. Offline indicator.
- **History management.** Per-tab Room storage: edit and delete messages, retry on error, clear a chat, wipe all generated files while keeping the history.
- **Theming.** Material 3, light and dark themes with the choice persisted.
- **Interface language.** Russian by default regardless of the system language; switch to English in settings, choice persisted.
- **Updates from GitHub.** Background release auto-check at most once per 24 hours; the update offer appears only in settings. The "App update" item downloads the APK with progress and SHA-256 verification, then installs it over the current version.
- **Feedback.** The "Contact the developer" item in settings opens the project's GitHub Discussions thread: bug reports, suggestions and questions.

## 🧠 Models & pricing

| Type | Model | Key | Resolution | Cost |
|---|---|---|---|---|
| Image | Flux (classic) | not required | up to 768×768 | free |
| Image | FLUX.2 Klein 4B | required | up to 1536×1536 | 0.005 pollen / image |
| Image | FLUX.1 Kontext Pro | required | 1024×1024 | 0.03 pollen / image |
| Image | FLUX.2 Pro | required + balance | up to 1536×1536 | 0.01125 pollen / MP |
| Text | GPT-5.4 Nano (`openai`) | not required | — | free, 1 request / 45 s |
| Text | Qwen3Guard 8B | required | — | free |

Image editing requires a key and spends the balance of the selected model. Without a key the app automatically falls back to the free mode.

## 📥 Download

Grab the latest APK from [Releases](https://github.com/denvisss/AxoraAI/releases/latest) (Android 8.0+). It installs over the previous version — the signature is preserved and your data stays intact. The app can also check for updates on its own (see Features).

## ⚙️ How it works

1. `MainActivity` sets up `ChatViewModel` and opens `ChatScreen` (Image tab by default).
2. Prompt input (text or voice) → `ChatViewModel.sendMessage()` persists the user message and starts generation.
3. For media: optional ru→en translation → `ChatRepository.generateContent()` picks the endpoint by model and key presence (keyless classic or `gen.pollinations.ai` with a key) → the file is saved to `files/generated/` → the assistant message lands in Room → with auto-save enabled it is copied to the gallery.
4. For text: `ChatRepository.generateText()` calls chat-completions (keyless or keyed).
5. Editing: `ChatRepository.editImage()` sends the source as a base64 data-URI to `POST /v1/images/edits`.
6. Each tab's history is read as a `Flow` from Room; the UI updates reactively.

## 🔐 Key security

- The key is stored in `EncryptedSharedPreferences` (AES256-GCM/SIV) and excluded from backups.
- It is sent only in the `Authorization: Bearer …` header; the header is redacted in logs (`redactHeader`).
- The key is absent from source code, resources and build files; the repository contains only a test placeholder.
- `local.properties` and build artifacts are excluded from VCS (`.gitignore`).

## 🛠 Tech stack

| Component | Version |
|---|---|
| Kotlin (built-in, AGP) | 2.2.10 |
| Android Gradle Plugin | 9.5.1 |
| Gradle | 9.6.0 |
| KSP | 2.3.14 |
| Jetpack Compose (BOM) | 2026.09.00 |
| Material 3 + icons-extended | from BOM |
| Room | 2.8.5 |
| Retrofit / Gson | 3.0.0 |
| OkHttp (+ logging) | 5.5.1 |
| Coil 3 | 3.4.0 |
| Coroutines | 1.11.0 |
| Lifecycle / ViewModel | 2.11.0 |
| security-crypto | 1.1.0 |

Architecture: MVVM with a single `ChatUiState`; `ChatRepository` is the interface, `DefaultChatRepository` the implementation (model routing, key, files); `SettingsRepository` handles settings and the key, `MediaStorage` tracks and cleans up generated files. DI is manual via `NetworkModule` and `AxoraApplication`. ViewModel strings are resolved through `StringResolver`, so the ViewModel is testable on the JVM without Android. All network and disk operations run off the main thread (`Dispatchers.IO`). The Room schema is exported to `app/schemas`; migrations are versioned.

`compileSdk 37`, `targetSdk 36`, `minSdk 26`.

## 📁 Project structure

```
app/src/main/java/com/axora/app/
├── AxoraApplication.kt        # DI: repositories, database, network
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt     # Room database + migrations
│   │   ├── dao/ChatDao.kt     # history queries and media cleanup
│   │   └── entity/ChatMessageEntity.kt
│   ├── network/               # Retrofit services (image/text/video/audio), TranslationClient, ImageEditBody (streaming base64)
│   └── repository/            # ChatRepository (interface), DefaultChatRepository, SettingsRepository, MediaStorage, UpdateManager
├── di/NetworkModule.kt        # OkHttp/Retrofit, Authorization redaction
├── domain/model/              # ChatMessage, ChatUiState, GenerationType, ImageFormat
├── ui/
│   ├── MainActivity.kt        # theme, chat/settings navigation
│   ├── ChatScreen.kt          # chat, input, voice, viewer, offline banner
│   ├── components/            # ChatComponents (tabs, bubbles, players), ImageViewer (zoom/pager)
│   ├── settings/SettingsScreen.kt    # settings and the "Connection" submenu
│   ├── theme/                 # Color.kt, Theme.kt (light/dark)
│   └── viewmodel/             # ChatViewModel, SettingsViewModel, factories, StringResolver
└── util/                      # SaveImageUtil (MediaStore/FileProvider), VoiceInputHelper, ImageSpec
```

## 🧰 Requirements & build

- JDK 17+
- Android SDK: compileSdk 37 (install via SDK Manager)
- `local.properties` with `sdk.dir` (created by Android Studio or manually; not committed to VCS)

Debug build:

```bash
./gradlew assembleDebug        # Linux/macOS
gradlew.bat assembleDebug      # Windows
```

Release (R8 + shrinkResources):

```bash
./gradlew assembleRelease
```

Install on a device/emulator:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## ✅ Tests & static analysis

```bash
./gradlew testDebugUnitTest    # unit tests (JUnit4 + coroutines-test)
./gradlew lintDebug            # Android lint
```

Coverage: domain models, `ImageSpec` (sizes/formats), network services and `TranslationClient` (OkHttp fake interceptor, no real requests), `ChatViewModel` (fake repository: generation, errors, cancellation, regeneration, text context). CI (GitHub Actions) builds the debug APK and runs tests and lint on every push and PR to `main`.

## 📱 In-app setup

1. Get a key (`sk_…`) at [enter.pollinations.ai/keys](https://enter.pollinations.ai/keys).
2. Settings → Connection → paste the key and pick the image and text models.
3. Free models work without a key; the key field is disabled when only free models are selected.
4. Main settings: theme, app language (Russian by default), auto-save to gallery, automatic prompt translation, storage cleanup, app update (auto-check every 24 hours), "Share the app", "Contact the developer", "About".

## 📄 License & attribution

The project is distributed under the **MIT** license (see [LICENSE](LICENSE)).

Mandatory attribution terms: any redistribution, fork or inclusion of the code into third-party products must preserve the copyright notice and credit the author and the original project:

- **Author:** denvisss
- **Original project:** Axora (AxoraAI repository)

Attribution is also duplicated in the app: Settings → About.

## ⚠️ Non-affiliation disclaimer

Axora is not legally or organizationally affiliated with Pollinations.ai. Names and trademarks belong to their respective owners. Interaction is limited to the service's public API; access keys are issued and verified on the Pollinations.ai side.