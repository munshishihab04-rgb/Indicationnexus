package com.personal.agent.permissions

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.personal.agent.accessibility.AgentAccessibilityService
import com.personal.agent.notifications.AgentNotificationListener

/**
 * Checks all permission states required by agent modules.
 * Does not request permissions — only reads current state.
 * Modules use this to determine if they can start.
 */
class PermissionChecker(private val context: Context) {

    fun checkAll(): Map<String, Boolean> = mapOf(
        "notifications"           to checkNotifications(),
        "notification_listener"   to checkNotificationListener(),
        "accessibility"           to checkAccessibility(),
        "battery_optimization"    to checkBatteryOptimization(),
        "read_media_images"       to checkPermission(android.Manifest.permission.READ_MEDIA_IMAGES),
        "read_media_video"        to checkPermission(android.Manifest.permission.READ_MEDIA_VIDEO),
        "post_notifications"      to checkPermission(android.Manifest.permission.POST_NOTIFICATIONS),
    )

    fun checkNotifications(): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.areNotificationsEnabled()
    }

    fun checkNotificationListener(): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver, "enabled_notification_listeners"
        ) ?: return false
        val cn = ComponentName(context, AgentNotificationListener::class.java)
        return flat.contains(cn.flattenToString())
    }

    fun checkAccessibility(): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val cn = ComponentName(context, AgentAccessibilityService::class.java)
        return enabled.contains(cn.flattenToString())
    }

    fun checkBatteryOptimization(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun checkPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
}
