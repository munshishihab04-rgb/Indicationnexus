package com.personal.agent.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.personal.agent.R
import com.personal.agent.core.AgentForegroundService
import com.personal.agent.databinding.ActivityMainBinding
import com.personal.agent.permissions.PermissionChecker
import kotlinx.coroutines.launch

/**
 * Setup UI — shows permission state, module status, and agent controls.
 * The agent itself runs headlessly; this screen is informational/control only.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val permissionChecker by lazy { PermissionChecker(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupButtons()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun setupButtons() {
        binding.btnStartAgent.setOnClickListener {
            AgentForegroundService.start(this)
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
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = android.net.Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }

    private fun refreshStatus() {
        lifecycleScope.launch {
            val perms = permissionChecker.checkAll()

            // Notifications
            val notifGranted = perms["notifications"] == true
            binding.tvStatusNotifications.text = if (notifGranted) "✅ Notifiche" else "❌ Notifiche"
            binding.btnOpenNotificationAccess.visibility =
                if (notifGranted) View.GONE else View.VISIBLE

            // Notification listener
            val listenerGranted = perms["notification_listener"] == true
            binding.tvStatusNotificationListener.text =
                if (listenerGranted) "✅ Notification Listener" else "❌ Notification Listener"
            binding.btnOpenNotificationAccess.visibility =
                if (listenerGranted) View.GONE else View.VISIBLE

            // Accessibility
            val a11yGranted = perms["accessibility"] == true
            binding.tvStatusAccessibility.text =
                if (a11yGranted) "✅ Accessibility" else "❌ Accessibility"
            binding.btnOpenAccessibilityAccess.visibility =
                if (a11yGranted) View.GONE else View.VISIBLE

            // Battery optimization
            val batteryGranted = perms["battery_optimization"] == true
            binding.tvStatusBattery.text =
                if (batteryGranted) "✅ Ottimizzazione batteria esclusa" else "❌ Ottimizzazione batteria attiva"
            binding.btnOpenBatteryOpt.visibility =
                if (batteryGranted) View.GONE else View.VISIBLE
        }
    }
}
