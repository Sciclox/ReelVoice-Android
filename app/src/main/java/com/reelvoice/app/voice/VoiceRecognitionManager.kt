package com.reelvoice.app.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

enum class VoiceCommand {
    NEXT,
    PREVIOUS,
    LIKE,
    PAUSE,
    UNKNOWN
}

/**
 * Gestor del motor de reconocimiento de voz local continuo.
 * Escucha en segundo plano y analiza palabras clave en tiempo real.
 */
class VoiceRecognitionManager(
    private val context: Context,
    private val onCommandDetected: (VoiceCommand) -> Unit,
    private val onStatusChanged: (isListening: Boolean, message: String) -> Unit
) {

    companion object {
        private const val TAG = "VoiceRecognitionMgr"
        private const val COMMAND_COOLDOWN_MS = 900L // Evita disparos duplicados
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var isRunning = false
    private var isPausedByUser = false
    private var lastCommandTimestamp = 0L
    private val mainHandler = Handler(Looper.getMainLooper())

    private val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("es", "ES").toString())
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es-ES")
        putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "es-ES")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
    }

    fun startListening() {
        if (isRunning) return
        isRunning = true
        isPausedByUser = false

        mainHandler.post {
            initAndStartRecognizer()
        }
    }

    fun pauseListening() {
        isPausedByUser = true
        stopInternal()
        onStatusChanged(false, "Micrófono en pausa")
    }

    fun resumeListening() {
        if (!isRunning) {
            startListening()
        } else {
            isPausedByUser = false
            mainHandler.post {
                initAndStartRecognizer()
            }
        }
    }

    fun stopListening() {
        isRunning = false
        isPausedByUser = false
        mainHandler.post {
            stopInternal()
        }
    }

    private fun initAndStartRecognizer() {
        if (!isRunning || isPausedByUser) return

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(TAG, "SpeechRecognizer no está disponible en este dispositivo.")
            onStatusChanged(false, "Reconocimiento no disponible")
            return
        }

        try {
            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(createRecognitionListener())
                }
            }
            speechRecognizer?.startListening(recognizerIntent)
            onStatusChanged(true, "Escuchando comandos...")
        } catch (e: Exception) {
            Log.e(TAG, "Error al iniciar SpeechRecognizer: ${e.message}")
            scheduleRestart(1000)
        }
    }

    private fun stopInternal() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Excepción al detener SpeechRecognizer: ${e.message}")
        } finally {
            speechRecognizer = null
        }
    }

    private fun scheduleRestart(delayMs: Long) {
        if (!isRunning || isPausedByUser) return

        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({
            if (isRunning && !isPausedByUser) {
                stopInternal()
                initAndStartRecognizer()
            }
        }, delayMs)
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "Listo para escuchar")
            }

            override fun onBeginningOfSpeech() {
                Log.d(TAG, "Voz detectada")
            }

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                Log.d(TAG, "Fin del fragmento de voz")
            }

            override fun onError(error: Int) {
                Log.d(TAG, "Código de evento del recognizer: $error")
                // Códigos normales de timeout o no-match reinician la escucha en bucle
                scheduleRestart(250)
            }

            override fun onResults(results: Bundle?) {
                processMatches(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION))
                scheduleRestart(150)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                processMatches(partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION))
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    private fun processMatches(matches: ArrayList<String>?) {
        if (matches.isNullOrEmpty()) return

        val now = System.currentTimeMillis()
        if (now - lastCommandTimestamp < COMMAND_COOLDOWN_MS) {
            return // Evita comandos repetidos por partial + final results
        }

        for (phrase in matches) {
            val normalized = phrase.lowercase(Locale.ROOT).trim()
            Log.d(TAG, "Frase escuchada: '$normalized'")

            val command = parseCommand(normalized)
            if (command != VoiceCommand.UNKNOWN) {
                lastCommandTimestamp = now
                onCommandDetected(command)
                break
            }
        }
    }

    private fun parseCommand(text: String): VoiceCommand {
        return when {
            // Siguiente Reel
            text.contains("siguiente") ||
            text.contains("baja") ||
            text.contains("next") ||
            text.contains("abajo") ||
            text.contains("otro") ||
            text.contains("avanza") ||
            text.contains("desliza") -> VoiceCommand.NEXT

            // Reel Anterior
            text.contains("atrás") ||
            text.contains("atras") ||
            text.contains("sube") ||
            text.contains("anterior") ||
            text.contains("arriba") ||
            text.contains("back") ||
            text.contains("regresa") -> VoiceCommand.PREVIOUS

            // Dar Like
            text.contains("like") ||
            text.contains("me gusta") ||
            text.contains("megusta") ||
            text.contains("corazón") ||
            text.contains("corazon") -> VoiceCommand.LIKE

            // Pausa / Play
            text.contains("pausa") ||
            text.contains("para") ||
            text.contains("parar") ||
            text.contains("alto") ||
            text.contains("play") ||
            text.contains("stop") ||
            text.contains("reproduce") -> VoiceCommand.PAUSE

            else -> VoiceCommand.UNKNOWN
        }
    }
}
