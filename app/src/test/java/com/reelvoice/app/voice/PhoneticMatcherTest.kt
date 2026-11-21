package com.reelvoice.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas unitarias para los algoritmos de coincidencia fonética y distancia de Levenshtein
 * utilizados por VoiceRecognitionManager.
 */
class PhoneticMatcherTest {

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
        val maxAllowedDistance = when {
            target.length <= 3 -> 0
            target.length <= 5 -> 1
            else -> 2
        }
        return calculateLevenshteinDistance(heardWord, target) <= maxAllowedDistance
    }

    @Test
    fun testExactMatches() {
        assertEquals(0, calculateLevenshteinDistance("siguiente", "siguiente"))
        assertEquals(0, calculateLevenshteinDistance("pausa", "pausa"))
        assertTrue(isFuzzyMatch("reel", "reel"))
    }

    @Test
    fun testFuzzyWakeWords() {
        // Variaciones comunes por acento o ruido: "riel", "rilo", "reels"
        assertTrue(isFuzzyMatch("riel", "reel"))
        assertTrue(isFuzzyMatch("reels", "reel"))
    }

    @Test
    fun testLevenshteinThresholds() {
        // 1 sustitución de distancia
        assertEquals(1, calculateLevenshteinDistance("tiktak", "tiktok"))
        // 2 operaciones de distancia
        assertEquals(2, calculateLevenshteinDistance("laik", "like"))
        assertEquals(2, calculateLevenshteinDistance("reelsx", "reel"))
    }
}
