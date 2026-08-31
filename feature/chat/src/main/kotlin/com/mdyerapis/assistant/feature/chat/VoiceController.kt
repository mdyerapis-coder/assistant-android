package com.mdyerapis.assistant.feature.chat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * On-device voice I/O (phase 09). Wraps [SpeechRecognizer] (STT) and
 * [TextToSpeech] (TTS); audio never leaves the device — no server-side
 * STT, no wake-word, no always-listening.
 *
 * The TTS engine initializes asynchronously; [speak] is a no-op until it
 * is ready. Call [destroy] from a DisposableEffect to release both
 * engines.
 */
class VoiceController(context: Context) {

    private val appContext = context.applicationContext

    var isListening by mutableStateOf(false)
        private set

    var ttsEnabled by mutableStateOf(false)
        private set

    var isTtsReady by mutableStateOf(false)
        private set

    private var recognizer: SpeechRecognizer? = null
    private var onResult: ((String) -> Unit)? = null

    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.getDefault())
                isTtsReady =
                    result != TextToSpeech.LANG_MISSING_DATA &&
                        result != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }
    }

    /** Start listening; recognized text is delivered via [onResult]. */
    fun startListening(onResult: (String) -> Unit) {
        if (isListening) return
        this.onResult = onResult
        val sr = recognizer ?: SpeechRecognizer.createSpeechRecognizer(appContext).also {
            recognizer = it
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        sr.setRecognitionListener(listener)
        isListening = true
        try {
            sr.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start speech recognition", e)
            isListening = false
        }
    }

    fun stopListening() {
        recognizer?.stopListening()
        isListening = false
    }

    /** Speak [text] if TTS is enabled and ready. */
    fun speak(text: String) {
        if (!ttsEnabled || !isTtsReady) return
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "assistant-${System.nanoTime()}")
    }

    /** Enable/disable spoken replies; stops any in-flight utterance. */
    fun updateTtsEnabled(enabled: Boolean) {
        ttsEnabled = enabled
        if (!enabled) tts?.stop()
    }

    /** Release recognizer + TTS. Safe to call multiple times. */
    fun destroy() {
        recognizer?.let {
            it.stopListening()
            it.destroy()
        }
        recognizer = null
        onResult = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        isListening = false
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            isListening = false
        }

        override fun onError(error: Int) {
            Log.w(TAG, "Speech recognition error: $error")
            isListening = false
        }

        override fun onResults(results: Bundle?) {
            isListening = false
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
            if (!text.isNullOrEmpty()) onResult?.invoke(text)
        }

        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    companion object {
        private const val TAG = "VoiceController"
    }
}
