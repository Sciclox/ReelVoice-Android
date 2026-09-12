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
        private const val COMMAND_COOLDOWN_MS = 550L // Cooldown ágil para navegación rápida
    }

    var requireWakeWord: Boolean = true
    private var wakeWordWindowUntil: Long = 0L

    private var speechRecognizer: SpeechRecognizer? = null
    private var isRunning = false
    private var isPausedByUser = false
    private var lastCommandTimestamp = 0L
    private val mainHandler = Handler(Looper.getMainLooper())

    private val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        val defaultLocale = Locale.getDefault().toLanguageTag()
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, defaultLocale)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, defaultLocale)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 350L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 350L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 150L)
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
            val normalized = normalizeText(phrase)
            Log.d(TAG, "Frase escuchada: '$phrase' -> Normalizada: '$normalized'")

            val command = parseCommand(normalized)
            if (command != VoiceCommand.UNKNOWN) {
                lastCommandTimestamp = now
                onCommandDetected(command)
                break
            }
        }
    }

    private fun normalizeText(input: String): String {
        val temp = java.text.Normalizer.normalize(input.lowercase(Locale.ROOT), java.text.Normalizer.Form.NFD)
        val clean = Regex("\\p{InCombiningDiacriticalMarks}+").replace(temp, "")
        return clean.replace(Regex("[^a-z0-9\\s]"), " ").trim()
    }

    private fun parseCommand(text: String): VoiceCommand {
        val now = System.currentTimeMillis()
        val hasWakeWord = text.contains("reel") ||
                text.contains("ril") ||
                text.contains("oye reel") ||
                text.contains("hey reel") ||
                text.contains("ok reel")

        if (hasWakeWord) {
            // Abre una ventana de escucha de 3.5 segundos si la palabra clave se dijo
            wakeWordWindowUntil = now + 3500L
        }

        // Si el Modo Seguro está activado y no hay palabra clave ni estamos en la ventana de escucha:
        if (requireWakeWord && !hasWakeWord && now > wakeWordWindowUntil) {
            return VoiceCommand.UNKNOWN
        }

        val words = text.split(" ").filter { it.isNotBlank() }

        // 1. Detección rápida por palabras individuales
        for (w in words) {
            if (w == "reel" || w == "ril") continue // Ignorar la palabra clave

            when (w) {
                // Siguiente
                "baja", "bajar", "baje", "sig", "siguiente", "next", "otro", "otra", "pasa", "pasar",
                "cambia", "cambiar", "dale", "abajo", "avanza" -> {
                    wakeWordWindowUntil = 0L
                    return VoiceCommand.NEXT
                }

                // Anterior
                "sube", "subir", "suba", "atras", "anterior", "arriba", "regresa", "regresar",
                "retrocede", "retroceder", "vuelve", "volver", "back", "ant" -> {
                    wakeWordWindowUntil = 0L
                    return VoiceCommand.PREVIOUS
                }

                // Like (incluyendo fonética común como 'laik', 'laic')
                "like", "laik", "laic", "layk", "lay", "leik", "megusta", "corazon", "fav", "love" -> {
                    wakeWordWindowUntil = 0L
                    return VoiceCommand.LIKE
                }

                // Pausa / Play
                "pausa", "pausar", "para", "parar", "stop", "alto", "espera", "quieto", "detente", "play" -> {
                    wakeWordWindowUntil = 0L
                    return VoiceCommand.PAUSE
                }
            }
        }

        // 2. Detección por subfrases compuestas
        val cmd = when {
            text.contains("me gusta") || text.contains("dar like") -> VoiceCommand.LIKE
            text.contains("siguiente video") || text.contains("proximo video") || text.contains("pasa al otro") -> VoiceCommand.NEXT
            text.contains("video anterior") || text.contains("vuelve atras") || text.contains("ve atras") -> VoiceCommand.PREVIOUS
            text.contains("pon pausa") || text.contains("ponle play") || text.contains("quita la pausa") -> VoiceCommand.PAUSE
            else -> VoiceCommand.UNKNOWN
        }

        if (cmd != VoiceCommand.UNKNOWN) {
            wakeWordWindowUntil = 0L
        }

        return cmd
    }
}
