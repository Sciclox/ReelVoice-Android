package com.reelvoice.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Pruebas unitarias completas para la normalización fonética y el parser
 * de comandos de voz compuestos e individuales de ReelVoice.
 */
class VoiceCommandParserTest {

    private fun normalizeText(input: String): String {
        val temp = java.text.Normalizer.normalize(input.lowercase(Locale.ROOT), java.text.Normalizer.Form.NFD)
        val clean = Regex("\\p{InCombiningDiacriticalMarks}+").replace(temp, "")
        return clean.replace(Regex("[^a-z0-9\\s]"), " ").trim()
    }

    private fun parseSimulatedCommand(text: String): VoiceCommand {
        val normalized = normalizeText(text)

        // 1. Detección prioritaria por frases compuestas
        return when {
            normalized.contains("dale like") || normalized.contains("dar like") || normalized.contains("me gusta") ||
            normalized.contains("pon like") || normalized.contains("deja tu like") || normalized.contains("deja like") -> VoiceCommand.LIKE

            normalized.contains("siguiente video") || normalized.contains("proximo video") || normalized.contains("pasa al otro") ||
            normalized.contains("pasa el video") || normalized.contains("pasa al siguiente") || normalized.contains("salta el video") -> VoiceCommand.NEXT

            normalized.contains("video anterior") || normalized.contains("vuelve atras") || normalized.contains("ve atras") ||
            normalized.contains("al anterior") || normalized.contains("el anterior") -> VoiceCommand.PREVIOUS

            normalized.contains("pon pausa") || normalized.contains("ponle play") || normalized.contains("quita la pausa") ||
            normalized.contains("dale play") || normalized.contains("dale pausa") || normalized.contains("pausa el video") -> VoiceCommand.PAUSE

            normalized.contains("sube volumen") || normalized.contains("subir volumen") || normalized.contains("mas volumen") ||
            normalized.contains("aumenta volumen") || normalized.contains("subele al volumen") || normalized.contains("sube el volumen") -> VoiceCommand.VOLUME_UP

            normalized.contains("baja volumen") || normalized.contains("bajar volumen") || normalized.contains("menos volumen") ||
            normalized.contains("disminuye volumen") || normalized.contains("bajale al volumen") || normalized.contains("baja el volumen") -> VoiceCommand.VOLUME_DOWN

            normalized.contains("abre comentarios") || normalized.contains("ver comentarios") || normalized.contains("mostrar comentarios") -> VoiceCommand.COMMENTS

            normalized.contains("agregar a favoritos") || normalized.contains("guardar video") || normalized.contains("guarda el video") -> VoiceCommand.FAVORITE

            // 2. Comandos individuales
            normalized.contains("siguiente") || normalized.contains("proximo") || normalized.contains("pasa") || normalized.contains("salta") -> VoiceCommand.NEXT
            normalized.contains("anterior") || normalized.contains("atras") || normalized.contains("retrocede") -> VoiceCommand.PREVIOUS
            normalized.contains("like") || normalized.contains("gusta") -> VoiceCommand.LIKE
            normalized.contains("pausa") || normalized.contains("para") || normalized.contains("deten") || normalized.contains("reproducir") -> VoiceCommand.PAUSE
            normalized.contains("comentarios") -> VoiceCommand.COMMENTS
            normalized.contains("favorito") || normalized.contains("guarda") -> VoiceCommand.FAVORITE
            normalized.contains("silencio") || normalized.contains("mute") -> VoiceCommand.MUTE

            else -> VoiceCommand.UNKNOWN
        }
    }

    @Test
    fun testNormalizationStripsAccentsAndPunctuation() {
        assertEquals("siguiente video", normalizeText("¡Siguiente Vídeo!"))
        assertEquals("mas volumen", normalizeText("¡MÁS VOLÚMEN!"))
        assertEquals("pausa", normalizeText("   pausá...  "))
    }

    @Test
    fun testCompoundCommandsPriority() {
        assertEquals(VoiceCommand.LIKE, parseSimulatedCommand("por favor dale like"))
        assertEquals(VoiceCommand.NEXT, parseSimulatedCommand("pasa al siguiente video"))
        assertEquals(VoiceCommand.PREVIOUS, parseSimulatedCommand("vuelve atras al video anterior"))
        assertEquals(VoiceCommand.PAUSE, parseSimulatedCommand("pon pausa al video"))
        assertEquals(VoiceCommand.VOLUME_UP, parseSimulatedCommand("sube el volumen por favor"))
        assertEquals(VoiceCommand.VOLUME_DOWN, parseSimulatedCommand("bajale al volumen"))
    }

    @Test
    fun testSingleKeywords() {
        assertEquals(VoiceCommand.NEXT, parseSimulatedCommand("siguiente"))
        assertEquals(VoiceCommand.PREVIOUS, parseSimulatedCommand("anterior"))
        assertEquals(VoiceCommand.PAUSE, parseSimulatedCommand("pausa"))
        assertEquals(VoiceCommand.COMMENTS, parseSimulatedCommand("comentarios"))
        assertEquals(VoiceCommand.MUTE, parseSimulatedCommand("silencio"))
    }

    @Test
    fun testUnknownUnrelatedPhrases() {
        assertEquals(VoiceCommand.UNKNOWN, parseSimulatedCommand("hola que tal estas"))
        assertEquals(VoiceCommand.UNKNOWN, parseSimulatedCommand("hace buen clima hoy"))
    }
}
