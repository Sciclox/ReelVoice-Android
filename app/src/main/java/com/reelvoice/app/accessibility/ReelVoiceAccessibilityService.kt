package com.reelvoice.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import java.lang.ref.WeakReference

/**
 * Servicio de Accesibilidad oficial para simular gestos táctiles (Scroll, Like, Pausa)
 * directamente sobre Instagram Reels, TikTok, YouTube Shorts y Facebook Reels.
 */
class ReelVoiceAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "ReelVoiceAccess"
        private var instanceRef: WeakReference<ReelVoiceAccessibilityService>? = null

        /**
         * Verifica si el servicio de accesibilidad está activo y enlazado.
         */
        val isServiceActive: Boolean
            get() = instanceRef?.get() != null

        /**
         * Instancia activa del servicio para despachar gestos.
         */
        val instance: ReelVoiceAccessibilityService?
            get() = instanceRef?.get()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instanceRef = WeakReference(this)
        Log.i(TAG, "ReelVoice Accessibility Service conectado y listo para gestos.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No necesitamos procesar eventos de ventana para mantener rendimiento óptimo.
    }

    override fun onInterrupt() {
        Log.w(TAG, "ReelVoice Accessibility Service interrumpido.")
    }

    override fun onDestroy() {
        super.onDestroy()
        instanceRef = null
        Log.i(TAG, "ReelVoice Accessibility Service destruido.")
    }

    /**
     * Simula un deslizamiento hacia arriba (Swipe Up) para pasar al siguiente Reel.
     * Calibrado a 280ms y 44% de pantalla para evitar sobre-inercia (doble salto en ViewPager).
     */
    fun scrollNext(): Boolean {
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels.toFloat()
        val height = metrics.heightPixels.toFloat()

        val startX = width * 0.5f
        val startY = height * 0.72f
        val endX = width * 0.5f
        val endY = height * 0.28f

        return performSwipe(startX, startY, endX, endY, duration = 280)
    }

    /**
     * Simula un deslizamiento hacia abajo (Swipe Down) para volver al Reel anterior.
     * Inicia a 35% de la altura para no tocar las pestañas o buscador superior de TikTok/Instagram.
     */
    fun scrollPrevious(): Boolean {
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels.toFloat()
        val height = metrics.heightPixels.toFloat()

        val startX = width * 0.5f
        val startY = height * 0.35f
        val endX = width * 0.5f
        val endY = height * 0.79f

        return performSwipe(startX, startY, endX, endY, duration = 280)
    }

    /**
     * Simula un doble toque (Double Tap) real en el centro de la pantalla para dar "Like".
     * Ambos trazos tienen willContinue = false para que el dedo se levante entre toques.
     */
    fun sendLike(): Boolean {
        val metrics = resources.displayMetrics
        val centerX = metrics.widthPixels * 0.5f
        val centerY = metrics.heightPixels * 0.5f

        val path1 = Path().apply { moveTo(centerX, centerY) }
        val path2 = Path().apply { moveTo(centerX, centerY) }

        val stroke1 = GestureDescription.StrokeDescription(path1, 0, 45, false)
        val stroke2 = GestureDescription.StrokeDescription(path2, 110, 45, false)

        val gesture = GestureDescription.Builder()
            .addStroke(stroke1)
            .addStroke(stroke2)
            .build()

        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Like (doble tap) completado con éxito.")
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Like (doble tap) cancelado.")
            }
        }, null)
    }

    /**
     * Simula un toque simple en el centro de la pantalla para Pausar / Reanudar el video.
     */
    fun togglePause(): Boolean {
        val metrics = resources.displayMetrics
        val centerX = metrics.widthPixels * 0.5f
        val centerY = metrics.heightPixels * 0.5f

        val path = Path().apply { moveTo(centerX, centerY) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50, false)

        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Tap de pausa/play completado.")
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Tap de pausa/play cancelado.")
            }
        }, null)
    }

    private fun performSwipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        duration: Long
    ): Boolean {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

        val stroke = GestureDescription.StrokeDescription(path, 0, duration)
        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Gesto de swipe completado.")
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Gesto de swipe cancelado.")
            }
        }, null)
    }
}
