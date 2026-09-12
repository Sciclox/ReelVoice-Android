package com.reelvoice.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.reelvoice.app.R
import com.reelvoice.app.accessibility.ReelVoiceAccessibilityService
import com.reelvoice.app.overlay.FloatingBubbleService
import com.reelvoice.app.ui.MainActivity
import com.reelvoice.app.voice.VoiceCommand
import com.reelvoice.app.voice.VoiceRecognitionManager

/**
 * Servicio en Primer Plano (Foreground Service) que mantiene la escucha del micrófono
 * activa e ininterrumpida mientras navegas en TikTok, Instagram o Facebook.
 */
class ReelVoiceForegroundService : Service() {

    companion object {
        private const val TAG = "ReelVoiceFGService"
        const val CHANNEL_ID = "reel_voice_channel"
        const val NOTIFICATION_ID = 2001

        const val ACTION_START = "ACTION_START_FOREGROUND"
        const val ACTION_STOP = "ACTION_STOP_FOREGROUND"
        const val ACTION_PAUSE_MIC = "ACTION_PAUSE_MIC"
        const val ACTION_RESUME_MIC = "ACTION_RESUME_MIC"
        const val ACTION_UPDATE_SETTINGS = "ACTION_UPDATE_SETTINGS"
        const val EXTRA_REQUIRE_WAKE_WORD = "EXTRA_REQUIRE_WAKE_WORD"
        const val EXTRA_CUSTOM_WAKE_WORD = "EXTRA_CUSTOM_WAKE_WORD"

        var isServiceRunning = false
            private set

        var lastDetectedCommand: VoiceCommand = VoiceCommand.UNKNOWN
            private set
    }

    private var voiceManager: VoiceRecognitionManager? = null
    private var isMicActive = true

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initVoiceRecognition()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                isServiceRunning = true
                startForegroundWithNotification()
                voiceManager?.startListening()
                // Iniciar burbuja flotante si hay permiso
                startFloatingBubble()
            }
            ACTION_STOP -> {
                stopSelf()
            }
            ACTION_PAUSE_MIC -> {
                isMicActive = false
                voiceManager?.pauseListening()
                updateNotification()
            }
            ACTION_RESUME_MIC -> {
                isMicActive = true
                voiceManager?.resumeListening()
                updateNotification()
            }
            ACTION_UPDATE_SETTINGS -> {
                val requireWake = intent.getBooleanExtra(EXTRA_REQUIRE_WAKE_WORD, true)
                val customWord = intent.getStringExtra(EXTRA_CUSTOM_WAKE_WORD)
                voiceManager?.requireWakeWord = requireWake
                if (!customWord.isNullOrBlank()) {
                    voiceManager?.customWakeWord = customWord
                }
                Log.d(TAG, "Configuración actualizada: requireWakeWord = $requireWake, customWakeWord = $customWord")
            }
        }
        return START_STICKY
    }

    private fun initVoiceRecognition() {
        val prefs = getSharedPreferences("reel_voice_prefs", Context.MODE_PRIVATE)
        val requireWake = prefs.getBoolean("pref_wake_word", false)
        val customWord = prefs.getString("pref_custom_wake_word", "reel") ?: "reel"

        voiceManager = VoiceRecognitionManager(
            context = this,
            onCommandDetected = { cmd -> handleCommand(cmd) },
            onStatusChanged = { isListening, msg ->
                Log.d(TAG, "Estado de voz: $msg (Escuchando: $isListening)")
            }
        ).apply {
            requireWakeWord = requireWake
            customWakeWord = customWord
        }
    }

    private fun handleCommand(command: VoiceCommand) {
        lastDetectedCommand = command
        Log.i(TAG, "Ejecutando comando de voz: $command")

        // 1. Dar feedback visual a la burbuja flotante y vibración sutil
        FloatingBubbleService.updateBubbleCommand(this, command)
        triggerHapticFeedback()

        // 2. Ejecutar gesto a través del servicio de accesibilidad
        val accessibility = ReelVoiceAccessibilityService.instance
        if (accessibility == null) {
            Log.w(TAG, "El servicio de accesibilidad no está activo. No se pudo despachar el gesto.")
            return
        }

        when (command) {
            VoiceCommand.NEXT -> accessibility.scrollNext()
            VoiceCommand.PREVIOUS -> accessibility.scrollPrevious()
            VoiceCommand.LIKE -> accessibility.sendLike()
            VoiceCommand.PAUSE -> accessibility.togglePause()
            VoiceCommand.UNKNOWN -> {}
        }
    }

    private fun triggerHapticFeedback() {
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(android.os.VibrationEffect.createOneShot(40, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(40)
            }
        } catch (e: Exception) {
            // Ignorar excepciones de vibrador
        }
    }

    private fun startFloatingBubble() {
        try {
            val bubbleIntent = Intent(this, FloatingBubbleService::class.java).apply {
                action = FloatingBubbleService.ACTION_START
            }
            startService(bubbleIntent)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo iniciar la burbuja flotante: ${e.message}")
        }
    }

    private fun startForegroundWithNotification() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pOpenApp = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleMicIntent = Intent(this, ReelVoiceForegroundService::class.java).apply {
            action = if (isMicActive) ACTION_PAUSE_MIC else ACTION_RESUME_MIC
        }
        val pToggleMic = PendingIntent.getService(
            this, 1, toggleMicIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, ReelVoiceForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val pStop = PendingIntent.getService(
            this, 2, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val micActionText = if (isMicActive) {
            getString(R.string.notif_action_pause_mic)
        } else {
            getString(R.string.notif_action_resume_mic)
        }

        val contentText = if (isMicActive) {
            getString(R.string.notif_text)
        } else {
            "Micrófono en pausa. Toca para reanudar."
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentIntent(pOpenApp)
            .setOngoing(true)
            .addAction(0, micActionText, pToggleMic)
            .addAction(0, getString(R.string.notif_action_stop), pStop)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        voiceManager?.stopListening()

        // Detener burbuja flotante
        val stopBubble = Intent(this, FloatingBubbleService::class.java).apply {
            action = FloatingBubbleService.ACTION_STOP
        }
        startService(stopBubble)

        Log.i(TAG, "ReelVoice Foreground Service finalizado.")
    }
}
