<div align="center">

# Axora

**Генерация изображений и текста по промпту в вашем кармане — на базе Groq и Cloudflare Workers AI**

[English](README.md) · **Русский**

[![Android CI](https://github.com/denvisss/AxoraAI/actions/workflows/android.yml/badge.svg?style=for-the-badge)](https://github.com/denvisss/AxoraAI/actions/workflows/android.yml)
[![Release](https://img.shields.io/github/v/release/denvisss/AxoraAI?style=for-the-badge&label=Release&color=2ea44f)](https://github.com/denvisss/AxoraAI/releases/latest)
[![License: MIT](https://img.shields.io/github/license/denvisss/AxoraAI?style=for-the-badge&color=blue)](LICENSE)
[![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/about/versions/oreo)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)

</div>

---

Axora — Android-клиент для генерации медиа по текстовому промпту: изображения и текст доступны сейчас, видео и аудио запланированы в следующих обновлениях. Генерация выполняется на двух сервисах с бесплатными тарифами — [Groq](https://groq.com) для чата и [Cloudflare Workers AI](https://developers.cloudflare.com/workers-ai/) для изображений. Оба бесплатных плана требуют только регистрации, без карты. Интерфейс на русском и английском: русский по умолчанию, выбор языка в настройках.

> Axora — независимый open-source проект, не аффилированный с Groq и Cloudflare. Сервисы предоставляют только вычислительные API и выпуск ключей доступа.

## ✨ Возможности

- **Раздельные чаты по типам генерации.** Вкладки «Изображение», «Текст», «Видео» и «Аудио»; у каждой свой поток истории. Видео и аудио помечены «Скоро» и недоступны до релиза.
- **Генерация изображений.** FLUX.2 Klein 4B на Cloudflare Workers AI (бесплатный тариф), до 1152 px по длинной стороне. Форматы кадра 1:1, 3:4 и 4:3. Редактирование готового изображения по инструкции (image-to-image) — исходник масштабируется на устройстве и передаётся как референс.
- **Генерация текста.** `openai/gpt-oss-120b` на Groq (бесплатный тариф) с контекстом последних сообщений вкладки.
- **Голосовой ввод.** Русский язык, семантика «удерживай — говори, отпусти — отправить» (`SpeechRecognizer`); для TalkBack микрофон работает как переключатель.
- **Автоперевод промптов.** Кириллические запросы переводятся на английский через Groq перед медиа-генерацией (отключаемо).
- **Просмотрщик изображений.** Полноэкранный просмотр с листанием всех изображений чата, зумом до 5× и панорамированием; скачивание и «Поделиться» прямо из просмотрщика.
- **Работа с результатом.** Сохранение в галерею, «Поделиться», копирование текста, автосохранение новых медиа (отключаемо), повторная генерация по исходному промпту («Сгенерировать ещё»).
- **Управление генерацией.** Отмена текущей генерации кнопкой в индикаторе прогресса; частичные файлы удаляются. Индикатор отсутствия интернета.
- **Управление историей.** Room-хранилище по вкладкам: редактирование и удаление сообщений, повтор при ошибке, очистка чата, очистка всех сгенерированных файлов с сохранением истории.
- **Оформление.** Material 3, светлая и тёмная темы с сохранением выбора.
- **Язык интерфейса.** Русский по умолчанию независимо от языка системы; переключение на английский в настройках с сохранением выбора.
- **Обновления из GitHub.** Фоновая автопроверка релизов не чаще раза в 24 часа; предложение обновиться появляется только в настройках. Пункт «Обновление приложения» скачивает APK с прогрессом и сверкой SHA-256 и устанавливает его обновлением поверх текущей версии.
- **Обратная связь.** Пункт «Написать разработчику» в настройках открывает ветку обсуждения проекта в GitHub Discussions: сообщения об ошибках, предложения и вопросы.

## 🧠 Сервисы и бесплатные тарифы

| Тип | Модель | Провайдер | Бесплатный лимит |
|---|---|---|---|
| Изображение | FLUX.2 Klein 4B | Cloudflare Workers AI | 10 000 Neurons/день ≈ 90 изображений |
| Текст и перевод | GPT-OSS 120B | Groq | ≈ 1000 сообщений/день |

Оба лимита обновляются ежедневно (у Cloudflare — в 00:00 UTC). Приложение не отправляет промпты никуда больше.

## 🔑 Настройка

Приложение работает с вашими бесплатными ключами — встроенного сервиса или прокси у него нет:

1. **Groq** (чат): создайте ключ на [console.groq.com/keys](https://console.groq.com/keys).
2. **Cloudflare** (изображения): зарегистрируйтесь на [dash.cloudflare.com/sign-up](https://dash.cloudflare.com/sign-up), откройте **Workers AI → Use REST API**, скопируйте **Account ID** и создайте **Workers AI API token** ([страница токенов](https://dash.cloudflare.com/profile/api-tokens) → *Create Token* → шаблон *Workers AI*).
3. В приложении откройте **Настройки → Подключение сервисов** и вставьте данные — у каждой карточки провайдера есть прямые кнопки на страницы регистрации.

Все три значения хранятся на устройстве в зашифрованном виде (см. «Безопасность ключей»).

## 📥 Загрузка

Свежий APK — в разделе [Releases](https://github.com/denvisss/AxoraAI/releases/latest) (Android 8.0+). Устанавливается поверх предыдущей версии: подпись сохраняется, данные не теряются. Приложение также умеет проверять обновления само (см. «Возможности»).

## ⚙️ Порядок работы

1. `MainActivity` поднимает `ChatViewModel` и открывает `ChatScreen` (вкладка «Изображение» по умолчанию).
2. Ввод промпта (текст или голос) → `ChatViewModel.sendMessage()` сохраняет пользовательское сообщение и запускает генерацию.
3. Для изображений: опциональный перевод ru→en → `ChatRepository.generateContent()` отправляет multipart-запрос к REST-эндпоинту Cloudflare Workers AI (`@cf/black-forest-labs/flux-2-klein-4b`) и декодирует base64-результат → файл сохраняется в `files/generated/` → ассистентское сообщение попадает в Room → при включённом автосохранении копируется в галерею.
4. Для текста: `ChatRepository.generateText()` обращается к OpenAI-совместимому chat-completions эндпоинту Groq.
5. Редактирование: `ChatRepository.editImage()` уменьшает исходник до менее чем 512×512, отправляет его как `input_image_0` с инструкцией в качестве промпта и возвращает отредактированное изображение.
6. История каждой вкладки читается как `Flow` из Room; UI реактивно обновляется.

## 🔐 Безопасность ключей

- Ключи хранятся в `EncryptedSharedPreferences` (AES256-GCM/SIV) и не попадают в бэкап.
- Передаются только в заголовке `Authorization: Bearer …`; в логах заголовок редактируется (`redactHeader`).
- Ключи отсутствуют в исходном коде, ресурсах и сборочных файлах; в репозитории только тестовые placeholder-значения.
- `local.properties` и артефакты сборки исключены из VCS (`.gitignore`).

## 🛠 Технологии

| Компонент | Версия |
|---|---|
| Kotlin (built-in, AGP) | 2.2.10 |
| Android Gradle Plugin | 9.5.1 |
| Gradle | 9.6.0 |
| KSP | 2.3.14 |
| Jetpack Compose (BOM) | 2026.09.00 |
| Material 3 + icons-extended | из BOM |
| Room | 2.8.5 |
| Retrofit / Gson | 3.0.0 |
| OkHttp (+ logging) | 5.5.1 |
| Coil 3 | 3.4.0 |
| Coroutines | 1.11.0 |
| Lifecycle / ViewModel | 2.11.0 |
| security-crypto | 1.1.0 |

Архитектура: MVVM с единым `ChatUiState`; `ChatRepository` — интерфейс, `DefaultChatRepository` — реализация (маршрутизация провайдеров, ключи, файлы); `SettingsRepository` — настройки и ключи, `MediaStorage` — учёт и очистка сгенерированных файлов. DI вручную через `NetworkModule` и `AxoraApplication`. Строки во ViewModel резолвятся через `StringResolver`, поэтому ViewModel тестируется на JVM без Android. Все сетевые и дисковые операции вне main-потока (`Dispatchers.IO`). Схема Room экспортируется в `app/schemas`, миграции версионируются.

`compileSdk 37`, `targetSdk 36`, `minSdk 26`.

## 📁 Структура проекта

```
app/src/main/java/com/axora/app/
├── AxoraApplication.kt        # DI: репозитории, БД, сеть
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt     # Room-БД + миграции
│   │   ├── dao/ChatDao.kt     # запросы истории и очистки медиа
│   │   └── entity/ChatMessageEntity.kt
│   ├── network/               # CloudflareApiService (multipart Klein), TextApiService (Groq), TranslationClient
│   └── repository/            # ChatRepository (интерфейс), DefaultChatRepository, SettingsRepository, MediaStorage, UpdateManager
├── di/NetworkModule.kt        # OkHttp/Retrofit, редакция Authorization
├── domain/model/              # ChatMessage, ChatUiState, GenerationType, ImageFormat
├── ui/
│   ├── MainActivity.kt        # тема, навигация чат/настройки
│   ├── ChatScreen.kt          # чат, ввод, голос, просмотрщик, офлайн-баннер
│   ├── components/            # ChatComponents (вкладки, баблы, плееры), ImageViewer (зум/пейджер)
│   ├── settings/SettingsScreen.kt    # настройки и подменю «Подключение сервисов»
│   ├── theme/                 # Color.kt, Theme.kt (light/dark)
│   └── viewmodel/             # ChatViewModel, SettingsViewModel, фабрики, StringResolver
└── util/                      # SaveImageUtil (MediaStore/FileProvider), VoiceInputHelper, ImageSpec
```

## 🧰 Требования и сборка

- JDK 17+
- Android SDK: compileSdk 37 (устанавливается через SDK Manager)
- `local.properties` с `sdk.dir` (создаётся Android Studio либо вручную; в VCS не попадает)

Отладочная сборка:

```bash
./gradlew assembleDebug        # Linux/macOS
gradlew.bat assembleDebug      # Windows
```

Release (R8 + shrinkResources):

```bash
./gradlew assembleRelease
```

Установка на устройство/эмулятор:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## ✅ Тесты и статический анализ

```bash
./gradlew testDebugUnitTest    # unit-тесты (JUnit4 + coroutines-test)
./gradlew lintDebug            # Android lint
```

Покрытие: доменные модели, `ImageSpec` (размеры/форматы), сетевые сервисы (`CloudflareApiService`, `TextApiService`, `TranslationClient` — fake-interceptor OkHttp, без реальных запросов), `ChatViewModel` (fake-репозиторий: генерация, ошибки, отмена, регенерация, контекст текста). CI (GitHub Actions) собирает debug-APK и прогоняет тесты и lint на каждый push и PR в `main`.

## 📱 Настройка в приложении

1. Настройки → **Подключение сервисов** → карточка Groq: нажмите **Получить бесплатный ключ**, создайте ключ и вставьте его.
2. Карточка Cloudflare: нажмите **Создать аккаунт** / **Создать токен**, скопируйте **Account ID** (Workers AI → Use REST API) и токен, вставьте оба значения.
3. Готово — чат и генерация изображений заработают. У каждой карточки свой статус «Подключено»/«Требуется настройка».
4. Главные настройки: тема, язык приложения (русский по умолчанию), автосохранение в галерею, автоперевод промптов, очистка памяти, обновление приложения (автопроверка раз в 24 часа), «Поделиться приложением», «Написать разработчику», «О приложении».

## 📄 Лицензия и атрибуция

Проект распространяется под лицензией **MIT** (см. [LICENSE](LICENSE)).

Условия обязательной атрибуции: при любом распространении, форке или включении кода в сторонние продукты необходимо сохранять уведомление об авторских правах и указывать автора и исходный проект:

- **Автор:** denvisss
- **Исходный проект:** Axora (репозиторий AxoraAI)

Атрибуция также продублирована в приложении: «Настройки» → «О приложении».

## ⚠️ Отказ от аффилиации

Axora не связан с Groq и Cloudflare юридически или организационно. Названия и торговые марки принадлежат их владельцам. Взаимодействие ограничивается публичными API сервисов; ключи доступа выпускаются и проверяются на стороне Groq и Cloudflare.
