<div align="center">

# Axora

**AI image & text generation in your pocket — powered by Groq and Cloudflare Workers AI**

**English** · [Русский](README.ru.md)

[![Android CI](https://github.com/denvisss/AxoraAI/actions/workflows/android.yml/badge.svg?style=for-the-badge)](https://github.com/denvisss/AxoraAI/actions/workflows/android.yml)
[![Release](https://img.shields.io/github/v/release/denvisss/AxoraAI?style=for-the-badge&label=Release&color=2ea44f)](https://github.com/denvisss/AxoraAI/releases/latest)
[![License: MIT](https://img.shields.io/github/license/denvisss/AxoraAI?style=for-the-badge&color=blue)](LICENSE)
[![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/about/versions/oreo)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)

</div>

---

Axora is an Android client for prompt-based media generation: images and text are available today, video and audio are planned for upcoming updates. Generation runs on two free-tier services — [Groq](https://groq.com) for chat and [Cloudflare Workers AI](https://developers.cloudflare.com/workers-ai/) for images. Both free plans require only a registration, no card. The interface ships in Russian and English: Russian is the default, the language is selectable in settings.

> Axora is an independent open-source project, not affiliated with Groq or Cloudflare. The services provide only the compute APIs and key issuance.

## ✨ Features

- **Separate chats per generation type.** Tabs for Image, Text, Video and Audio, each with its own history stream. Video and Audio are marked "Soon" and unavailable until release.
- **Image generation.** FLUX.2 Klein 4B on Cloudflare Workers AI (free tier), resolutions up to 1536×864. Aspect ratios 1:1, 3:4, 4:3 and 16:9. Instruction-based editing of an existing image (image-to-image) — the source is downscaled on-device and passed as a reference.
- **Text generation.** Groq free tier with the context of the tab's recent messages. Answers stream in real time and render Markdown (headings, lists, code, quotes). The model is selectable in settings: GPT-OSS 120B (smartest), GPT-OSS 20B (fastest) or Llama 3.3 70B.
- **Image editing with a reference.** Attach your own photo to the chat (paperclip button or "Share → Axora" from any gallery app) and describe what to change — FLUX.2 Klein re-draws it. Quick presets ("Similar", "Style") and an edit action right in the image viewer cover "make something similar" too.
- **Prompt templates.** Create your own reusable templates with `[placeholders]`, fill them in with a dialog and send.
- **History search.** Full-text search (FTS) across all tabs — find any message by a couple of words.
- **Starter prompt ideas.** The empty screen shows ready-made example prompts for the active tab, tinted in its accent color; one tap fills the input.
- **Voice input.** Russian, hold-to-talk semantics — hold to speak, release to send (`SpeechRecognizer`); with TalkBack the microphone acts as a toggle.
- **Automatic prompt translation.** Cyrillic prompts are translated to English via Groq before media generation (can be disabled).
- **Image viewer.** Full-screen viewing with swiping through all chat images, zoom up to 5× and panning; download and share right from the viewer.
- **Image gallery.** All generated images in one screen (the photo-library icon on the Image tab); tapping a thumbnail opens the full-screen viewer with save and share.
- **Working with results.** Save to gallery, share, copy text, auto-save new media (can be disabled), regenerate from the original prompt ("Generate again").
- **Chat export.** Share a tab's conversation as plain text via any app — media messages are included as short markers, errors are excluded. Find it in Settings → Backup → "Share chat".
- **Generation control.** Cancel the current generation via a button in the progress indicator; partial files are removed. Offline indicator.
- **History management.** Per-tab Room storage: edit and delete messages, retry on error, clear a chat, wipe all generated files while keeping the history.
- **Theming.** Material 3, light / dark / follow-the-system themes with the choice persisted.
- **Interface language.** Russian by default regardless of the system language; switch to English in settings, choice persisted.
- **Updates from GitHub.** Background release auto-check at most once per 24 hours; the update offer appears only in settings. The "App update" item downloads the APK with progress and SHA-256 verification, then installs it over the current version. After each update a "What's new" dialog shows the release notes.
- **Usage statistics.** Images and chat messages generated today / in the last 7 days / in total.
- **Backup.** Export history, templates and settings (API keys and media files excluded) into a JSON file; import it back on another device.
- **Proxy support.** Settings → "Proxy server": optionally route all app traffic through a proxy. A free public proxy list is built in — it reloads automatically every 5 minutes, every server is ping-checked automatically, latency is shown in green (<300 ms), yellow (<1 s) or red, unreachable servers are hidden and the list is sorted by speed, with SOCKS5 selected by default. You can also connect your own server (SOCKS5/SOCKS4/HTTP/HTTPS).
- **Feedback.** The "Contact the developer" item in settings opens the project's GitHub Discussions thread: bug reports, suggestions and questions.
- **Support the author.** The "Support the author" item in About opens the author's Boosty donation page.

## 🧠 Services & free tiers

| Type | Model | Provider | Free-tier allowance |
|---|---|---|---|
| Image | FLUX.2 Klein 4B | Cloudflare Workers AI | 10,000 Neurons/day ≈ 90 images |
| Text & translation | GPT-OSS 120B | Groq | ≈ 1,000 messages/day |

Both allowances reset daily (Cloudflare at 00:00 UTC). The app never sends prompts anywhere else.

## 🔑 Setup

The app works with your own free keys and talks to the providers directly (an optional proxy can be enabled in settings — see Features):

1. **Groq** (chat): create a key at [console.groq.com/keys](https://console.groq.com/keys).
2. **Cloudflare** (images): sign up at [dash.cloudflare.com/sign-up](https://dash.cloudflare.com/sign-up), open **Workers AI → Use REST API**, copy the **Account ID** and create a **Workers AI API token** ([token page](https://dash.cloudflare.com/profile/api-tokens) → *Create Token* → *Workers AI* template).
3. In the app open **Settings → Connect services** and paste the credentials — each provider card has direct buttons to the registration pages.

All three values are stored encrypted on the device (see Key security).

## 📥 Download

Grab the latest APK from [Releases](https://github.com/denvisss/AxoraAI/releases/latest) (Android 8.0+). It installs over the previous version — the signature is preserved and your data stays intact. The app can also check for updates on its own (see Features).

## ⚙️ How it works

1. `MainActivity` sets up `ChatViewModel` and opens `ChatScreen` (Image tab by default).
2. Prompt input (text or voice) → `ChatViewModel.sendMessage()` persists the user message and starts generation.
3. For images: optional ru→en translation → `ChatRepository.generateContent()` sends a multipart request to the Cloudflare Workers AI REST endpoint (`@cf/black-forest-labs/flux-2-klein-4b`) and decodes the base64 result → the file is saved to `files/generated/` → the assistant message lands in Room → with auto-save enabled it is copied to the gallery.
4. For text: `ChatRepository.generateTextStreamed()` calls Groq's OpenAI-compatible chat-completions endpoint with `stream: true` and renders the answer token by token.
5. Editing: `ChatRepository.editImage()` downscales the source below 512×512, sends it as `input_image_0` with the instruction as the prompt, and returns the edited image.
6. Each tab's history is read as a `Flow` from Room; the UI updates reactively.

## 🔐 Key security

- Keys are stored in `EncryptedSharedPreferences` (AES256-GCM/SIV) and excluded from backups.
- They are sent only in the `Authorization: Bearer …` header; the header is redacted in logs (`redactHeader`).
- Keys are absent from source code, resources and build files; the repository contains only test placeholders.
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

Architecture: MVVM with a single `ChatUiState`; `ChatRepository` is the interface, `DefaultChatRepository` the implementation (provider routing, keys, files); `SettingsRepository` handles settings and keys, `MediaStorage` tracks and cleans up generated files. DI is manual via `NetworkModule` and `AxoraApplication`. ViewModel strings are resolved through `StringResolver`, so the ViewModel is testable on the JVM without Android. All network and disk operations run off the main thread (`Dispatchers.IO`). The Room schema is exported to `app/schemas`; migrations are versioned.

`compileSdk 37`, `targetSdk 36`, `minSdk 26`.

## 📁 Project structure

```
app/src/main/java/com/axora/app/
├── AxoraApplication.kt        # DI: repositories, database, network
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt     # Room database + migrations
│   │   ├── dao/ChatDao.kt     # history queries and media cleanup
│   │   └── entity/ChatMessageEntity.kt, ChatMessagesFts.kt
│   ├── network/               # CloudflareApiService (Klein multipart), TextApiService (Groq), TranslationClient
│   └── repository/            # ChatRepository (interface), DefaultChatRepository, SettingsRepository, MediaStorage, TemplateStore, UpdateManager
├── di/NetworkModule.kt        # OkHttp/Retrofit, Authorization redaction
├── domain/model/              # ChatMessage, ChatUiState, GenerationType, ImageFormat
├── ui/
│   ├── MainActivity.kt        # theme, chat/settings/gallery navigation
│   ├── ChatScreen.kt          # chat, input, voice, viewer, offline banner, export
│   ├── GalleryScreen.kt       # grid of all generated images
│   ├── components/            # ChatComponents (tabs, bubbles, players), ImageViewer (zoom/pager), MarkdownText
│   ├── settings/SettingsScreen.kt    # settings and the "Connect services" and "Proxy" submenus
│   ├── theme/                 # Color.kt, Theme.kt (light/dark)
│   └── viewmodel/             # ChatViewModel, SettingsViewModel, TemplateViewModel, factories, StringResolver
└── util/                      # SaveImageUtil (MediaStore/FileProvider), VoiceInputHelper, ImageSpec, MarkdownParser, ImageImport
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

Coverage: domain models, `ImageSpec` (sizes/formats), network services (`CloudflareApiService`, `TextApiService`, `TranslationClient` — OkHttp fake interceptor, no real requests), `ChatViewModel` (fake repository: generation, errors, cancellation, regeneration, text context). CI (GitHub Actions) builds the debug APK and runs tests and lint on every push and PR to `main`.

## 📱 In-app setup

1. Settings → **Connect services** → the Groq card: tap **Get a free key**, create the key, paste it.
2. The Cloudflare card: tap **Create account** / **Create token**, copy the **Account ID** (Workers AI → Use REST API) and the token, paste both.
3. Done — chat and image generation start working. Each card shows a connected/setup-required status.
4. Main settings: theme (light / dark / system), app language (Russian by default), text model, auto-save to gallery, automatic prompt translation, prompt templates, usage statistics, storage cleanup, proxy server (free list or your own), backup export / import, app update (auto-check every 24 hours), "Share the app", "Contact the developer", "Support the author" (Boosty), "About".

## 📄 License & attribution

The project is distributed under the **MIT** license (see [LICENSE](LICENSE)).

Mandatory attribution terms: any redistribution, fork or inclusion of the code into third-party products must preserve the copyright notice and credit the author and the original project:

- **Author:** denvisss
- **Original project:** Axora (AxoraAI repository)

Attribution is also duplicated in the app: Settings → About.

## ⚠️ Non-affiliation disclaimer

Axora is not legally or organizationally affiliated with Groq or Cloudflare. Names and trademarks belong to their respective owners. Interaction is limited to the services' public APIs; access keys are issued and verified on the Groq and Cloudflare side.
