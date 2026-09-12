package com.reelvoice.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.reelvoice.app.R
import com.reelvoice.app.accessibility.ReelVoiceAccessibilityService
import com.reelvoice.app.databinding.ActivityMainBinding
import com.reelvoice.app.service.ReelVoiceForegroundService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val handler = Handler(Looper.getMainLooper())
    private var statusRunnable: Runnable? = null

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "Permiso de micrófono concedido", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "El micrófono es necesario para escuchar comandos", Toast.LENGTH_LONG).show()
        }
        updateUiState()
    }

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { updateUiState() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        requestNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        updateUiState()
        startStatusPolling()
    }

    override fun onPause() {
        super.onPause()
        statusRunnable?.let { handler.removeCallbacks(it) }
    }

    private fun setupListeners() {
        binding.btnPermAccessibility.setOnClickListener {
            openAccessibilitySettings()
        }

        binding.btnPermOverlay.setOnClickListener {
            openOverlaySettings()
        }

        binding.btnPermMic.setOnClickListener {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        binding.btnToggleMasterService.setOnClickListener {
            toggleMasterService()
        }

        val prefs = getSharedPreferences("reel_voice_prefs", Context.MODE_PRIVATE)
        val initialWake = prefs.getBoolean("pref_wake_word", true)
        binding.switchWakeWord.isChecked = initialWake
        updateWakeWordHint(initialWake)

        binding.switchWakeWord.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("pref_wake_word", isChecked).apply()
            updateWakeWordHint(isChecked)

            if (ReelVoiceForegroundService.isServiceRunning) {
                val updateIntent = Intent(this, ReelVoiceForegroundService::class.java).apply {
                    action = ReelVoiceForegroundService.ACTION_UPDATE_SETTINGS
                    putExtra(ReelVoiceForegroundService.EXTRA_REQUIRE_WAKE_WORD, isChecked)
                }
                startService(updateIntent)
            }
        }
    }

    private fun updateWakeWordHint(isWakeWordActive: Boolean) {
        if (isWakeWordActive) {
            binding.tvWakeWordHint.text = getString(R.string.wake_word_active_hint)
            binding.tvWakeWordHint.setTextColor(getColor(R.color.neon_cyan))
        } else {
            binding.tvWakeWordHint.text = getString(R.string.wake_word_direct_hint)
            binding.tvWakeWordHint.setTextColor(getColor(R.color.neon_yellow))
        }
    }

    private fun updateUiState() {
        val hasMic = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }

        val hasAccessibility = ReelVoiceAccessibilityService.isServiceActive

        // Actualizar tarjeta de accesibilidad
        if (hasAccessibility) {
            binding.btnPermAccessibility.text = getString(R.string.status_granted)
            binding.btnPermAccessibility.isEnabled = false
            binding.btnPermAccessibility.setTextColor(getColor(R.color.neon_green))
        } else {
            binding.btnPermAccessibility.text = getString(R.string.btn_grant)
            binding.btnPermAccessibility.isEnabled = true
            binding.btnPermAccessibility.setTextColor(getColor(R.color.neon_cyan))
        }

        // Actualizar tarjeta de overlay
        if (hasOverlay) {
            binding.btnPermOverlay.text = getString(R.string.status_granted)
            binding.btnPermOverlay.isEnabled = false
            binding.btnPermOverlay.setTextColor(getColor(R.color.neon_green))
        } else {
            binding.btnPermOverlay.text = getString(R.string.btn_grant)
            binding.btnPermOverlay.isEnabled = true
            binding.btnPermOverlay.setTextColor(getColor(R.color.neon_purple))
        }

        // Actualizar tarjeta de micrófono
        if (hasMic) {
            binding.btnPermMic.text = getString(R.string.status_granted)
            binding.btnPermMic.isEnabled = false
            binding.btnPermMic.setTextColor(getColor(R.color.neon_green))
        } else {
            binding.btnPermMic.text = getString(R.string.btn_grant)
            binding.btnPermMic.isEnabled = true
            binding.btnPermMic.setTextColor(getColor(R.color.neon_green))
        }

        // Estado del servicio
        val isRunning = ReelVoiceForegroundService.isServiceRunning
        if (isRunning) {
            binding.statusIndicatorDot.setBackgroundResource(R.drawable.bg_bubble_active)
            binding.tvServiceStatus.text = getString(R.string.status_active)
            binding.tvServiceStatus.setTextColor(getColor(R.color.neon_cyan))
            binding.btnToggleMasterService.text = getString(R.string.btn_stop_service)
        } else {
            binding.statusIndicatorDot.setBackgroundResource(R.drawable.bg_bubble_muted)
            binding.tvServiceStatus.text = getString(R.string.status_inactive)
            binding.tvServiceStatus.setTextColor(getColor(R.color.text_primary))
            binding.btnToggleMasterService.text = getString(R.string.btn_start_service)
        }

        val lastCmd = ReelVoiceForegroundService.lastDetectedCommand
        binding.tvLastCommandDetected.text = "Último comando: ${lastCmd.name}"
    }

    private fun toggleMasterService() {
        if (ReelVoiceForegroundService.isServiceRunning) {
            val stopIntent = Intent(this, ReelVoiceForegroundService::class.java).apply {
                action = ReelVoiceForegroundService.ACTION_STOP
            }
            startService(stopIntent)
            handler.postDelayed({ updateUiState() }, 300)
            return
        }

        // Validar permisos antes de arrancar
        if (!ReelVoiceAccessibilityService.isServiceActive) {
            Toast.makeText(this, getString(R.string.toast_accessibility_needed), Toast.LENGTH_LONG).show()
            openAccessibilitySettings()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, getString(R.string.toast_overlay_needed), Toast.LENGTH_LONG).show()
            openOverlaySettings()
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        val startIntent = Intent(this, ReelVoiceForegroundService::class.java).apply {
            action = ReelVoiceForegroundService.ACTION_START
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(startIntent)
        } else {
            startService(startIntent)
        }

        handler.postDelayed({ updateUiState() }, 300)
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
        Toast.makeText(this, "Busca 'ReelVoice' y activa el interruptor", Toast.LENGTH_LONG).show()
    }

    private fun openOverlaySettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun startStatusPolling() {
        statusRunnable = object : Runnable {
            override fun run() {
                updateUiState()
                handler.postDelayed(this, 1500)
            }
        }
        handler.postDelayed(statusRunnable!!, 1500)
    }
}
