package com.reelvoice.app.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.IOException
import java.util.Locale

enum class VoiceCommand {
    NEXT,
    PREVIOUS,
    LIKE,
    PAUSE,
    UNKNOWN
}

/**
 * Gestor del motor de reconocimiento de voz offline continuo por hardware (AudioRecord + Vosk).
 * Mantiene el canal de audio abierto de manera ininterrumpida (como WhatsApp), garantizando:
 * 1. Indicador verde de privacidad fijo y sin parpadeos en Android 12+.
 * 2. Cero pitidos ("Bim") del sistema.
 * 3. Escucha continua sin timeouts por silencio.
 * 4. Procesamiento 100% local y privado con soporte para palabras clave personalizadas (Fuzzy matching).
 */
class VoiceRecognitionManager(
    private val context: Context,
    private val onCommandDetected: (VoiceCommand) -> Unit,
    private val onStatusChanged: (isListening: Boolean, message: String) -> Unit
) {

    companion object {
        private const val TAG = "VoiceRecognitionMgr"
        private const val SAMPLE_RATE = 16000.0f
        private const val COMMAND_COOLDOWN_MS = 1200L // Evita comandos repetidos y dobles saltos
    }

    var requireWakeWord: Boolean = true
    var customWakeWord: String = "reel"
    private var wakeWordWindowUntil: Long = 0L

    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var isModelLoading = false
    private var isRunning = false
    private var isPausedByUser = false
    private var lastCommandTimestamp = 0L
    private val mainHandler = Handler(Looper.getMainLooper())

    fun startListening() {
        if (isRunning) return
        isRunning = true
        isPausedByUser = false

        if (model == null) {
            loadModelAndStart()
        } else {
            startSpeechService()
        }
    }

    fun pauseListening() {
        isPausedByUser = true
        stopSpeechServiceInternal()
        onStatusChanged(false, "Micrófono en pausa")
    }

    fun resumeListening() {
        if (!isRunning) {
            startListening()
        } else {
            isPausedByUser = false
            if (model != null) {
                startSpeechService()
            } else {
                loadModelAndStart()
            }
        }
    }

    fun stopListening() {
        isRunning = false
        isPausedByUser = false
        stopSpeechServiceInternal()
        onStatusChanged(false, "Detenido")
    }

    private fun loadModelAndStart() {
        if (isModelLoading) return
        isModelLoading = true
        onStatusChanged(false, "Iniciando motor offline...")

        StorageService.unpack(
            context,
            "model-es",
            "model",
            { loadedModel: Model ->
                mainHandler.post {
                    model = loadedModel
                    isModelLoading = false
                    Log.i(TAG, "Modelo acústico offline cargado correctamente.")
                    if (isRunning && !isPausedByUser) {
                        startSpeechService()
                    }
                }
            },
            { exception: IOException ->
                mainHandler.post {
                    isModelLoading = false
                    Log.e(TAG, "Error al desempaquetar el modelo Vosk: ${exception.message}", exception)
                    onStatusChanged(false, "Error al cargar modelo")
                }
            }
        )
    }

    private fun startSpeechService() {
        val currentModel = model ?: run {
            loadModelAndStart()
            return
        }

        try {
            stopSpeechServiceInternal()
            val recognizer = Recognizer(currentModel, SAMPLE_RATE)
            speechService = SpeechService(recognizer, SAMPLE_RATE).apply {
                startListening(createRecognitionListener())
            }
            onStatusChanged(true, "Escuchando continuamente")
            Log.i(TAG, "SpeechService de Vosk iniciado con AudioRecord a ${SAMPLE_RATE.toInt()}Hz.")
        } catch (e: Exception) {
            Log.e(TAG, "Error al iniciar SpeechService: ${e.message}", e)
            onStatusChanged(false, "Error de micrófono: ${e.message}")
        }
    }

    private fun stopSpeechServiceInternal() {
        try {
            speechService?.stop()
            speechService?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "Excepción al detener SpeechService: ${e.message}")
        } finally {
            speechService = null
        }
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onPartialResult(hypothesis: String?) {
                hypothesis?.let { processVoskJson(it, isPartial = true) }
            }

            override fun onResult(hypothesis: String?) {
                hypothesis?.let { processVoskJson(it, isPartial = false) }
            }

            override fun onFinalResult(hypothesis: String?) {
                hypothesis?.let { processVoskJson(it, isPartial = false) }
            }

            override fun onError(exception: Exception?) {
                Log.e(TAG, "Error en reconocimiento Vosk: ${exception?.message}", exception)
            }

            override fun onTimeout() {
                Log.d(TAG, "Vosk recognizer timeout")
            }
        }
    }

    private fun processVoskJson(jsonString: String, isPartial: Boolean) {
        try {
            val json = JSONObject(jsonString)
            val phrase = if (isPartial) {
                json.optString("partial", "")
            } else {
                json.optString("text", "")
            }

            if (phrase.isNotBlank()) {
                handleRecognizedPhrase(phrase)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parseando hipótesis de Vosk: $jsonString")
        }
    }

    private fun handleRecognizedPhrase(phrase: String) {
        val now = System.currentTimeMillis()
        if (now - lastCommandTimestamp < COMMAND_COOLDOWN_MS) {
            return
        }

        val normalized = normalizeText(phrase)
        val command = parseCommand(normalized)

        if (command != VoiceCommand.UNKNOWN) {
            lastCommandTimestamp = now
            Log.i(TAG, "Comando reconocido: $command a partir de: '$phrase'")
            
            mainHandler.post {
                onCommandDetected(command)
                // Reinicia el buffer del reconocedor para evitar repetir la misma orden
                try {
                    speechService?.reset()
                } catch (e: Exception) {
                    Log.w(TAG, "Error al resetear reconocedor: ${e.message}")
                }
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

        // 1. Detección rápida por palabras individuales (incluye variantes fonéticas a distancia)
        for (w in words) {
            if (isFuzzyMatch(w, wakeTarget) || (wakeTarget == "reel" && (w == "ril" || w == "riel" || w == "real"))) continue // Ignorar la palabra clave

            when (w) {
                // Siguiente
                "baja", "bajar", "baje", "bajo", "vaja", "vaya", "aja", "baha", "maja", "sig",
                "siguiente", "next", "otro", "otra", "pasa", "pasar", "cambia", "cambiar", "dale", "abajo", "avanza" -> {
                    wakeWordWindowUntil = 0L
                    return VoiceCommand.NEXT
                }

                // Anterior
                "sube", "subir", "suba", "suve", "tube", "supe", "shube", "sub", "atras",
                "anterior", "arriba", "regresa", "regresar", "retrocede", "retroceder", "vuelve", "volver", "back", "ant" -> {
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
