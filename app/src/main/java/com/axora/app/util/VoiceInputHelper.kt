package com.axora.app.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class VoiceInputHelper(private val activity: Activity) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var listener: VoiceInputListener? = null
    private var started = false
    private val hasSpeechRecognizer: Boolean by lazy {
        SpeechRecognizer.isRecognitionAvailable(activity)
    }

    interface VoiceInputListener {
        fun onRecordingStarted()
        fun onSpeechResult(text: String)
        fun onError(message: String)
        fun onRecordingStopped()
    }

    private fun hasAudioPermission(): Boolean {
        return activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    fun initialize(listener: VoiceInputListener) {
        this.listener = listener

        if (!hasSpeechRecognizer) return

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(activity).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        listener.onRecordingStarted()
                    }

                    override fun onBeginningOfSpeech() {}

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        listener.onRecordingStopped()
                    }

                    override fun onError(error: Int) {
                        started = false
                        if (!hasAudioPermission()) {
                            listener.onError("Нет разрешения на запись звука")
                            return
                        }
                        val message = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Ошибка аудио"
                            SpeechRecognizer.ERROR_CLIENT -> "Ошибка: устройство не может начать запись. Попробуйте ещё раз."
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Нет разрешения на запись"
                            SpeechRecognizer.ERROR_NETWORK -> "Ошибка сети"
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Превышено время ожидания"
                            SpeechRecognizer.ERROR_NO_MATCH -> "Речь не распознана. Попробуйте ещё раз"
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Сервис распознавания занят"
                            SpeechRecognizer.ERROR_SERVER -> "Ошибка сервера распознавания"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Речь не обнаружена. Скажите что-нибудь"
                            else -> "Ошибка распознавания: $error"
                        }
                        listener.onError(message)
                    }

                    override fun onResults(results: Bundle?) {
                        started = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            listener.onSpeechResult(matches[0])
                        } else {
                            listener.onError("Речь не распознана. Попробуйте ещё раз")
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        }
    }

    fun startListening() {
        if (!hasAudioPermission()) {
            listener?.onError("Нет разрешения на запись звука")
            return
        }
        val recognizer = speechRecognizer ?: return
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            }
            started = true
            recognizer.startListening(intent)
        } catch (e: Exception) {
            started = false
            listener?.onError("Не удалось запустить распознавание: ${e.message}")
        }
    }

    /** Останавливает запись и доставляет результат через onResults. */
    fun stopListening() {
        if (!started) return
        started = false
        speechRecognizer?.stopListening()
    }

    fun destroy() {
        started = false
        speechRecognizer?.destroy()
        speechRecognizer = null
        listener = null
    }

    fun isAvailable(): Boolean = hasSpeechRecognizer && hasAudioPermission()
}
