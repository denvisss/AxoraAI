package com.imageai.app.util

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

class VoiceInputHelper(private val activity: Activity) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var listener: VoiceInputListener? = null
    private val hasSpeechRecognizer: Boolean by lazy {
        SpeechRecognizer.isRecognitionAvailable(activity)
    }

    interface VoiceInputListener {
        fun onRecordingStarted()
        fun onSpeechResult(text: String)
        fun onError(message: String)
        fun onRecordingStopped()
    }

    fun initialize(listener: VoiceInputListener) {
        this.listener = listener

        if (!hasSpeechRecognizer) {
            listener.onError("Распознавание речи недоступно на этом устройстве")
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(activity).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    listener?.onRecordingStarted()
                }

                override fun onBeginningOfSpeech() {
                    // Микрофон активен
                }

                override fun onRmsChanged(rmsdB: Float) {
                    // Уровень громкости
                }

                override fun onBufferReceived(buffer: ByteArray?) {
                    // Буфер распознавания
                }

                override fun onEndOfSpeech() {
                    listener?.onRecordingStopped()
                }

                override fun onError(error: Int) {
                    val message = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Ошибка аудио: не удалось записать звук"
                        SpeechRecognizer.ERROR_CLIENT -> "Ошибка клиента: проверьте разрешения"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Нет разрешения на запись аудио"
                        SpeechRecognizer.ERROR_NETWORK -> "Ошибка сети: проверьте подключение"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Превышено время ожидания сети"
                        SpeechRecognizer.ERROR_NO_MATCH -> "Распознавание речи: не удалось распознать речь"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Сервис распознавания занят"
                        SpeechRecognizer.ERROR_SERVER -> "Ошибка сервера"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Речь не обнаружена"
                        else -> "Неизвестная ошибка: $error"
                    }
                    listener?.onError(message)
                }

                override fun onResults(results: Bundle?) {
                    results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.let { matches ->
                        if (matches.isNotEmpty()) {
                            listener?.onSpeechResult(matches[0])
                        } else {
                            listener?.onError("Речь не распознана")
                        }
                    } ?: listener?.onError("Не удалось получить результат распознавания")
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    // Частичные результаты
                }

                override fun onEvent(eventType: Int, params: Bundle?) {
                    // Дополнительные события
                }
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Говорите...")
        }

        speechRecognizer?.startListening(intent)
    }

    fun startListening() {
        if (speechRecognizer != null && listener != null) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Говорите...")
            }
            speechRecognizer?.startListening(intent)
        }
    }

    fun cancel() {
        speechRecognizer?.stopListening()
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
        listener = null
    }

    fun isAvailable(): Boolean = hasSpeechRecognizer
}
