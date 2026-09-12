package com.reelvoice.app.voice

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
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
    VOLUME_UP,
    VOLUME_DOWN,
    MUTE,
    COMMENTS,
    FAVORITE,
    UNKNOWN
}

/**
 * Gestor del motor de reconocimiento de voz continuo de Google SpeechRecognizer.
 * Configurado con soporte On-Device (en el chip), ventanas de latencia inmediata (750ms)
 * y enrutamiento Bluetooth SCO para micrófonos de auriculares inalámbricos.
 */
class VoiceRecognitionManager(
    private val context: Context,
    private val onCommandDetected: (VoiceCommand) -> Unit,
    private val onStatusChanged: (isListening: Boolean, message: String) -> Unit
) {

    companion object {
        private const val TAG = "VoiceRecognitionMgr"
        private const val COMMAND_COOLDOWN_MS = 1000L // 1s para evitar dobles saltos
        private const val SILENCE_COMPLETE_MS = 750L // Respuesta ágil: 750ms tras finalizar de hablar
        private const val SILENCE_POSSIBLY_COMPLETE_MS = 750L
        private const val SILENCE_MINIMUM_MS = 1200L
    }

    var requireWakeWord: Boolean = false
    var customWakeWord: String = "reel"
    var enableBluetoothSco: Boolean = true
    private var wakeWordWindowUntil: Long = 0L

    private var speechRecognizer: SpeechRecognizer? = null
    private var isRunning = false
    private var isPausedByUser = false
    private var lastCommandTimestamp = 0L
    private var isCommandExecutedInCurrentSession = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        val defaultLocale = Locale.getDefault().toLanguageTag()
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, defaultLocale)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, defaultLocale)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)

        // Ventanas de silencio ágiles para respuesta instantánea (< 500-750ms)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE_COMPLETE_MS)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE_POSSIBLY_COMPLETE_MS)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, SILENCE_MINIMUM_MS)

        // Compatibilidad con versiones específicas de la app de Google
        putExtra("android.speech.extras.SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS", SILENCE_COMPLETE_MS)
        putExtra("android.speech.extras.SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS", SILENCE_POSSIBLY_COMPLETE_MS)
        putExtra("android.speech.extras.SPEECH_INPUT_MINIMUM_LENGTH_MILLIS", SILENCE_MINIMUM_MS)
    }

    private fun setupBluetoothSco(enable: Boolean) {
        if (!enableBluetoothSco && enable) return
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            if (enable) {
                if (audioManager.isBluetoothScoAvailableOffCall && !audioManager.isBluetoothScoOn) {
                    audioManager.startBluetoothSco()
                    audioManager.isBluetoothScoOn = true
                    Log.i(TAG, "Bluetooth SCO activado para audífonos inalámbricos")
                }
            } else {
                if (audioManager.isBluetoothScoOn) {
                    audioManager.isBluetoothScoOn = false
                    audioManager.stopBluetoothSco()
                    Log.i(TAG, "Bluetooth SCO detenido")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Excepción al configurar Bluetooth SCO: ${e.message}")
        }
    }

    private fun muteSystemSounds(mute: Boolean) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val direction = if (mute) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE
                audioManager.adjustStreamVolume(AudioManager.STREAM_SYSTEM, direction, 0)
                audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, direction, 0)
            } else {
                @Suppress("DEPRECATION")
                audioManager.setStreamMute(AudioManager.STREAM_SYSTEM, mute)
                @Suppress("DEPRECATION")
                audioManager.setStreamMute(AudioManager.STREAM_NOTIFICATION, mute)
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo ajustar volumen: ${e.message}")
        }
    }

    fun startListening() {
        if (isRunning) return
        isRunning = true
        isPausedByUser = false
        setupBluetoothSco(true)
        muteSystemSounds(true)

        mainHandler.post {
            initAndStartRecognizer()
        }
    }

    fun pauseListening() {
        isPausedByUser = true
        setupBluetoothSco(false)
        stopInternal()
        muteSystemSounds(false)
        onStatusChanged(false, "Micrófono en pausa")
    }

    fun resumeListening() {
        if (!isRunning) {
            startListening()
        } else {
            isPausedByUser = false
            setupBluetoothSco(true)
            muteSystemSounds(true)
            mainHandler.post {
                initAndStartRecognizer()
            }
        }
    }

    fun stopListening() {
        isRunning = false
        isPausedByUser = false
        setupBluetoothSco(false)
        mainHandler.post {
            stopInternal()
            muteSystemSounds(false)
            onStatusChanged(false, "Detenido")
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
                speechRecognizer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                    Log.i(TAG, "Iniciando OnDeviceSpeechRecognizer (Procesamiento local en el chip, latencia cero)")
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                } else {
                    Log.i(TAG, "Iniciando SpeechRecognizer estándar")
                    SpeechRecognizer.createSpeechRecognizer(context)
                }.apply {
                    setRecognitionListener(createRecognitionListener())
                }
            }
            speechRecognizer?.startListening(recognizerIntent)
            onStatusChanged(true, "Escuchando comandos...")
        } catch (e: Exception) {
            Log.e(TAG, "Error al iniciar SpeechRecognizer: ${e.message}")
            stopInternal()
            scheduleRestart(400)
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
                isCommandExecutedInCurrentSession = false
                onStatusChanged(true, "Escuchando activamente")
            }

            override fun onBeginningOfSpeech() {
                Log.d(TAG, "Voz detectada")
                isCommandExecutedInCurrentSession = false
            }

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                Log.d(TAG, "Fin de fragmento de voz")
            }

            override fun onError(error: Int) {
                Log.d(TAG, "Evento onError del reconocedor: $error")
                isCommandExecutedInCurrentSession = false
                // Reanudar con delay adaptativo para evitar RECOGNIZER_BUSY
                val restartDelay = when (error) {
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT, SpeechRecognizer.ERROR_NO_MATCH -> 150L
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT -> 350L
                    else -> 200L
                }
                scheduleRestart(restartDelay)
            }

            override fun onResults(results: Bundle?) {
                processMatches(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION))
                isCommandExecutedInCurrentSession = false
                scheduleRestart(120L)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                processMatches(partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION))
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    private fun processMatches(matches: ArrayList<String>?) {
        if (matches.isNullOrEmpty()) return

        // Si ya se ejecutó un comando en esta frase hablada (ej: en partialResults), no volver a disparar en onResults
        if (isCommandExecutedInCurrentSession) {
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastCommandTimestamp < COMMAND_COOLDOWN_MS) {
            return // Evita comandos repetidos dentro de la ventana de cooldown
        }

        for (phrase in matches) {
            val normalized = normalizeText(phrase)
            Log.d(TAG, "Frase escuchada: '$phrase' -> Normalizada: '$normalized'")

            val command = parseCommand(normalized)
            if (command != VoiceCommand.UNKNOWN) {
                lastCommandTimestamp = now
                isCommandExecutedInCurrentSession = true
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

    private fun calculateLevenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j

        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // Eliminación
                    dp[i][j - 1] + 1,      // Inserción
                    dp[i - 1][j - 1] + cost // Sustitución
                )
            }
        }
        return dp[m][n]
    }

    private fun isFuzzyMatch(heardWord: String, target: String): Boolean {
        if (heardWord == target) return true
        if (target.length >= 3 && (heardWord.contains(target) || target.contains(heardWord))) return true

        val maxAllowedDistance = when {
            target.length <= 3 -> if (heardWord.length == target.length) 1 else 0
            target.length <= 6 -> 1
            else -> 2
        }

        return calculateLevenshteinDistance(heardWord, target) <= maxAllowedDistance
    }

    private fun parseCommand(text: String): VoiceCommand {
        val now = System.currentTimeMillis()
        val wakeTarget = normalizeText(customWakeWord)
        val words = text.split(" ").filter { it.isNotBlank() }

        var hasWakeWord = false
        if (wakeTarget.isNotBlank()) {
            if (text.contains(wakeTarget) ||
                (wakeTarget == "reel" && (text.contains("ril") || text.contains("riel") || text.contains("real") || text.contains("rin"))) ||
                (wakeTarget == "tiktok" && (text.contains("tik tok") || text.contains("tictoc") || text.contains("titoc") || text.contains("tito"))) ||
                (wakeTarget == "oye" && (text.contains("oie") || text.contains("olle") || text.contains("ole") || text.contains("hey"))) ||
                (wakeTarget == "video" && (text.contains("bideo") || text.contains("vido"))) ||
                text.contains("oye $wakeTarget") ||
                text.contains("hey $wakeTarget") ||
                text.contains("ok $wakeTarget")
            ) {
                hasWakeWord = true
            } else {
                // Coincidencia fonética difusa (Fuzzy matching) sobre palabras individuales
                for (w in words) {
                    if (isFuzzyMatch(w, wakeTarget)) {
                        hasWakeWord = true
                        break
                    }
                }
                // Coincidencia sobre pares contiguos (ej: 'tik tok' para 'tiktok')
                if (!hasWakeWord && words.size >= 2) {
                    for (i in 0 until words.size - 1) {
                        val joined = "${words[i]}${words[i + 1]}"
                        if (joined == wakeTarget || isFuzzyMatch(joined, wakeTarget)) {
                            hasWakeWord = true
                            break
                        }
                    }
                }
            }
        }

        if (hasWakeWord) {
            // Abre una ventana de escucha de 3.5 segundos si la palabra clave se dijo
            wakeWordWindowUntil = now + 3500L
        }

        // Si el Modo Seguro está activado y no hay palabra clave ni estamos en la ventana de escucha:
        if (requireWakeWord && !hasWakeWord && now > wakeWordWindowUntil) {
            return VoiceCommand.UNKNOWN
        }

        // 1. Detección prioritaria por subfrases compuestas (evita que "dale" en "dale like" salte a NEXT)
        val compoundCmd = when {
            text.contains("dale like") || text.contains("dar like") || text.contains("me gusta") ||
            text.contains("pon like") || text.contains("deja tu like") || text.contains("deja like") -> VoiceCommand.LIKE

            text.contains("siguiente video") || text.contains("proximo video") || text.contains("pasa al otro") ||
            text.contains("pasa el video") || text.contains("pasa al siguiente") || text.contains("salta el video") -> VoiceCommand.NEXT

            text.contains("video anterior") || text.contains("vuelve atras") || text.contains("ve atras") ||
            text.contains("al anterior") || text.contains("el anterior") -> VoiceCommand.PREVIOUS

            text.contains("pon pausa") || text.contains("ponle play") || text.contains("quita la pausa") ||
            text.contains("dale play") || text.contains("dale pausa") || text.contains("pausa el video") -> VoiceCommand.PAUSE

            // Comandos de Volumen
            text.contains("sube volumen") || text.contains("subir volumen") || text.contains("mas volumen") ||
            text.contains("aumenta volumen") || text.contains("subele al volumen") || text.contains("sube el volumen") -> VoiceCommand.VOLUME_UP

            text.contains("baja volumen") || text.contains("bajar volumen") || text.contains("menos volumen") ||
            text.contains("disminuye volumen") || text.contains("bajale al volumen") || text.contains("baja el volumen") -> VoiceCommand.VOLUME_DOWN

            text.contains("quitar sonido") || text.contains("apaga sonido") || text.contains("sin sonido") ||
            text.contains("pon silencio") || text.contains("quita el sonido") -> VoiceCommand.MUTE

            // Comandos de Comentarios y Favoritos
            text.contains("ver comentarios") || text.contains("abrir comentarios") || text.contains("abre comentarios") ||
            text.contains("abre los comentarios") || text.contains("ver los comentarios") -> VoiceCommand.COMMENTS

            text.contains("guardar video") || text.contains("guarda video") || text.contains("anadir a favoritos") ||
            text.contains("pon en favoritos") || text.contains("guarda el video") || text.contains("guardar en favoritos") -> VoiceCommand.FAVORITE

            else -> VoiceCommand.UNKNOWN
        }

        if (compoundCmd != VoiceCommand.UNKNOWN) {
            wakeWordWindowUntil = 0L
            return compoundCmd
        }

        // 2. Detección rápida por palabras individuales (incluye variantes fonéticas a distancia)
        for (w in words) {
            if (isFuzzyMatch(w, wakeTarget) || (wakeTarget == "reel" && (w == "ril" || w == "riel" || w == "real" || w == "rin"))) continue // Ignorar la palabra clave

            when (w) {
                // Siguiente
                "baja", "bajar", "bajale", "baje", "bajo", "vaja", "vaya", "aja", "baha", "maja", "sig",
                "siguiente", "next", "otro", "otra", "pasa", "pasar", "pasalo", "pasala", "pasale",
                "cambia", "cambiar", "dale", "abajo", "avanza", "avanzar", "desliza", "deslizar", "continua", "continuar" -> {
                    wakeWordWindowUntil = 0L
                    return VoiceCommand.NEXT
                }

                // Anterior
                "sube", "subir", "subele", "suba", "suve", "tube", "supe", "shube", "sub", "atras",
                "anterior", "arriba", "regresa", "regresar", "retrocede", "retroceder", "vuelve", "volver", "back", "ant" -> {
                    wakeWordWindowUntil = 0L
                    return VoiceCommand.PREVIOUS
                }

                // Like (incluyendo fonética común como 'laik', 'laic')
                "like", "laik", "laic", "layk", "lay", "leik", "megusta", "corazon", "fav", "love", "encanta" -> {
                    wakeWordWindowUntil = 0L
                    return VoiceCommand.LIKE
                }

                // Pausa / Play
                "pausa", "pausar", "pausalo", "pausale", "para", "parar", "stop", "alto", "espera", "quieto", "detente", "play", "reproduce", "sigue" -> {
                    wakeWordWindowUntil = 0L
                    return VoiceCommand.PAUSE
                }

                // Comentarios
                "comentarios", "comentario" -> {
                    wakeWordWindowUntil = 0L
                    return VoiceCommand.COMMENTS
                }

                // Favoritos / Guardar
                "favorito", "favoritos", "guardar", "guarda" -> {
                    wakeWordWindowUntil = 0L
                    return VoiceCommand.FAVORITE
                }

                // Silencio / Mute
                "silencio", "mute" -> {
                    wakeWordWindowUntil = 0L
                    return VoiceCommand.MUTE
                }
            }
        }

        return VoiceCommand.UNKNOWN
    }
}
