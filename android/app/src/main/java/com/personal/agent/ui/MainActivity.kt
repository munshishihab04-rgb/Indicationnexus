package com.personal.agent.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.personal.agent.core.AgentForegroundService
import com.personal.agent.core.AgentWorkScheduler
import com.personal.agent.databinding.ActivityMainBinding
import com.personal.agent.device.DeviceManager
import com.personal.agent.permissions.PermissionChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Setup UI — shows permission state, module status, and agent controls.
 *
 * On first launch:
 *   1. Generates device UUID and install UUID (stored in EncryptedSharedPreferences).
 *   2. Calls POST /v1/device/register with a setup token from BuildConfig.
 *   3. Starts the foreground service and schedules all workers.
 *
 * On subsequent launches: skips enrollment and only refreshes the status view.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val permissionChecker by lazy { PermissionChecker(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupButtons()
        handleFirstLaunch()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    // ─── First-launch enrollment ───────────────────────────────────────────────

    private fun handleFirstLaunch() {
        val alreadyEnrolled = DeviceManager.getApiKey(this) != null
        if (alreadyEnrolled) return

        lifecycleScope.launch {
            binding.tvEnrollmentStatus.visibility = View.VISIBLE
            binding.tvEnrollmentStatus.text = "Registrazione dispositivo…"

            val enrolled = withContext(Dispatchers.IO) {
                DeviceManager.register(
                    context = this@MainActivity,
                    setupToken = com.personal.agent.BuildConfig.SETUP_TOKEN
                )
            }

            if (enrolled) {
                binding.tvEnrollmentStatus.text = "✅ Dispositivo registrato"
                AgentForegroundService.start(this@MainActivity)
                AgentWorkScheduler.scheduleAll(this@MainActivity)
            } else {
                binding.tvEnrollmentStatus.text = "❌ Registrazione fallita — controlla server URL"
                Toast.makeText(
                    this@MainActivity,
                    "Enrollment fallito. Verifica SERVER_BASE_URL in BuildConfig.",
                    Toast.LENGTH_LONG
                ).show()
            }
            refreshStatus()
        }
    }

    // ─── UI setup ─────────────────────────────────────────────────────────────

    private fun setupButtons() {
        binding.btnStartAgent.setOnClickListener {
            AgentForegroundService.start(this)
            AgentWorkScheduler.scheduleAll(this)
            refreshStatus()
        }
        binding.btnStopAgent.setOnClickListener {
            AgentForegroundService.stop(this)
            refreshStatus()
        }
        binding.btnOpenNotificationAccess.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        binding.btnOpenAccessibilityAccess.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        binding.btnOpenBatteryOpt.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = android.net.Uri.parse("package:$packageName")
                }
            )
        }
    }

    // ─── Status refresh ───────────────────────────────────────────────────────

    private fun refreshStatus() {
        lifecycleScope.launch {
            val perms = permissionChecker.checkAll()
            val deviceId = withContext(Dispatchers.IO) {
                DeviceManager.getOrCreateDeviceId(this@MainActivity)
            }
            val enrolled = DeviceManager.getApiKey(this@MainActivity) != null

            // Enrollment row
            binding.tvEnrollmentStatus.visibility = View.VISIBLE
            binding.tvEnrollmentStatus.text = if (enrolled)
                "✅ Enrolled — $deviceId"
            else
                "❌ Non registrato"

            // Notifications
            val notifOk = perms["post_notifications"] == true
            binding.tvStatusNotifications.text =
                if (notifOk) "✅ Permesso notifiche" else "❌ Permesso notifiche"

            // Notification listener
            val listenerOk = perms["notification_listener"] == true
            binding.tvStatusNotificationListener.text =
                if (listenerOk) "✅ Notification Listener" else "❌ Notification Listener"
            binding.btnOpenNotificationAccess.visibility =
                if (listenerOk) View.GONE else View.VISIBLE

            // Accessibility
            val a11yOk = perms["accessibility"] == true
            binding.tvStatusAccessibility.text =
                if (a11yOk) "✅ Accessibility" else "❌ Accessibility"
            binding.btnOpenAccessibilityAccess.visibility =
                if (a11yOk) View.GONE else View.VISIBLE

            // Battery optimization
            val batteryOk = perms["battery_optimization"] == true
            binding.tvStatusBattery.text =
                if (batteryOk) "✅ Ottimizzazione batteria esclusa"
                else "❌ Ottimizzazione batteria attiva (agent può essere killato)"
            binding.btnOpenBatteryOpt.visibility =
                if (batteryOk) View.GONE else View.VISIBLE
        }
    }
}
