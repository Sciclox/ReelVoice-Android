package com.reelvoice.app.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.reelvoice.app.R
import com.reelvoice.app.service.ReelVoiceForegroundService
import com.reelvoice.app.voice.VoiceCommand
import kotlin.math.abs

/**
 * Servicio que administra el botón/burbuja flotante magnética
 * superpuesta en pantalla sobre TikTok e Instagram.
 */
class FloatingBubbleService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START_BUBBLE"
        const val ACTION_STOP = "ACTION_STOP_BUBBLE"
        const val ACTION_UPDATE_STATE = "ACTION_UPDATE_BUBBLE_STATE"
        const val EXTRA_IS_LISTENING = "EXTRA_IS_LISTENING"
        const val EXTRA_COMMAND_TYPE = "EXTRA_COMMAND_TYPE"

        private var instance: FloatingBubbleService? = null

        fun updateBubbleCommand(context: Context, command: VoiceCommand) {
            val intent = Intent(context, FloatingBubbleService::class.java).apply {
                action = ACTION_UPDATE_STATE
                putExtra(EXTRA_COMMAND_TYPE, command.name)
            }
            context.startService(intent)
        }
    }

    private var windowManager: WindowManager? = null
    private var bubbleView: View? = null
    private lateinit var layoutParams: WindowManager.LayoutParams

    private var isMicActive = true

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        initFloatingBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopSelf()
            ACTION_UPDATE_STATE -> {
                val isListening = intent.getBooleanExtra(EXTRA_IS_LISTENING, isMicActive)
                val cmdType = intent.getStringExtra(EXTRA_COMMAND_TYPE)
                if (cmdType != null) {
                    showCommandFeedback()
                } else {
                    updateMicAppearance(isListening)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun initFloatingBubble() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(this)
        bubbleView = inflater.inflate(R.layout.floating_bubble, null)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 30
            y = 350
        }

        setupBubbleTouchInteractions()

        try {
            windowManager?.addView(bubbleView, layoutParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupBubbleTouchInteractions() {
        val root = bubbleView?.findViewById<View>(R.id.bubbleRoot) ?: return

        root.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isClick = false

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = layoutParams.x
                        initialY = layoutParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isClick = true
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = (event.rawX - initialTouchX).toInt()
                        val deltaY = (event.rawY - initialTouchY).toInt()

                        if (abs(deltaX) > 10 || abs(deltaY) > 10) {
                            isClick = false
                        }

                        layoutParams.x = initialX + deltaX
                        layoutParams.y = initialY + deltaY
                        windowManager?.updateViewLayout(bubbleView, layoutParams)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isClick) {
                            toggleMicrophoneState()
                        } else {
                            snapToNearestEdge()
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun toggleMicrophoneState() {
        isMicActive = !isMicActive
        updateMicAppearance(isMicActive)

        // Enviar evento a ReelVoiceForegroundService
        val intent = Intent(this, ReelVoiceForegroundService::class.java).apply {
            action = if (isMicActive) {
                ReelVoiceForegroundService.ACTION_RESUME_MIC
            } else {
                ReelVoiceForegroundService.ACTION_PAUSE_MIC
            }
        }
        startService(intent)
    }

    private fun updateMicAppearance(active: Boolean) {
        isMicActive = active
        val ivIcon = bubbleView?.findViewById<ImageView>(R.id.ivBubbleIcon)
        val container = bubbleView?.findViewById<View>(R.id.bubbleContainer)
        val halo = bubbleView?.findViewById<View>(R.id.haloView)

        if (active) {
            ivIcon?.setImageResource(R.drawable.ic_mic)
            container?.setBackgroundResource(R.drawable.bg_bubble_active)
            halo?.setBackgroundResource(R.drawable.bg_bubble_active)
            halo?.animate()?.alpha(0.25f)?.setDuration(200)?.start()
        } else {
            ivIcon?.setImageResource(R.drawable.ic_mic_off)
            container?.setBackgroundResource(R.drawable.bg_bubble_muted)
            halo?.setBackgroundResource(R.drawable.bg_bubble_muted)
            halo?.animate()?.alpha(0.1f)?.setDuration(200)?.start()
        }
    }

    private fun showCommandFeedback() {
        val indicator = bubbleView?.findViewById<View>(R.id.indicatorDot)
        val container = bubbleView?.findViewById<View>(R.id.bubbleContainer)

        indicator?.visibility = View.VISIBLE
        container?.animate()
            ?.scaleX(1.15f)
            ?.scaleY(1.15f)
            ?.setDuration(120)
            ?.withEndAction {
                container.animate()
                    ?.scaleX(1.0f)
                    ?.scaleY(1.0f)
                    ?.setDuration(120)
                    ?.start()
                indicator?.postDelayed({ indicator.visibility = View.GONE }, 600)
            }
            ?.start()
    }

    private fun snapToNearestEdge() {
        val screenWidth = resources.displayMetrics.widthPixels
        val middle = screenWidth / 2
        layoutParams.x = if (layoutParams.x < middle) 20 else screenWidth - 160
        windowManager?.updateViewLayout(bubbleView, layoutParams)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        if (bubbleView != null) {
            try {
                windowManager?.removeView(bubbleView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            bubbleView = null
        }
    }
}
