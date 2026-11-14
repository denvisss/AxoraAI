package com.axora.app.util

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.axora.app.R

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
                            listener.onError(activity.getString(R.string.voice_err_permission))
                            return
                        }
                        val message = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> activity.getString(R.string.voice_err_audio)
                            SpeechRecognizer.ERROR_CLIENT -> activity.getString(R.string.voice_err_client)
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> activity.getString(R.string.voice_err_permissions)
                            SpeechRecognizer.ERROR_NETWORK -> activity.getString(R.string.voice_err_network)
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> activity.getString(R.string.voice_err_network_timeout)
                            SpeechRecognizer.ERROR_NO_MATCH -> activity.getString(R.string.voice_err_no_match)
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> activity.getString(R.string.voice_err_busy)
                            SpeechRecognizer.ERROR_SERVER -> activity.getString(R.string.voice_err_server)
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> activity.getString(R.string.voice_err_speech_timeout)
                            else -> activity.getString(R.string.voice_err_generic, error)
                        }
                        listener.onError(message)
                    }

                    override fun onResults(results: Bundle?) {
                        started = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            listener.onSpeechResult(matches[0])
                        } else {
                            listener.onError(activity.getString(R.string.voice_err_no_match))
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
            listener?.onError(activity.getString(R.string.voice_err_permission))
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
            listener?.onError(activity.getString(R.string.voice_err_start, e.message.orEmpty()))
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