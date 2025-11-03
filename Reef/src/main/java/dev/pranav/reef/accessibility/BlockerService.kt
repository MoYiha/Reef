package dev.pranav.reef.accessibility

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.pranav.reef.AppUsageActivity
import dev.pranav.reef.R
import dev.pranav.reef.util.AppLimits
import dev.pranav.reef.util.CHANNEL_ID
import dev.pranav.reef.util.NotificationHelper
import dev.pranav.reef.util.NotificationHelper.createNotificationChannel
import dev.pranav.reef.util.RoutineLimits
import dev.pranav.reef.util.RoutineManager
import dev.pranav.reef.util.Whitelist
import dev.pranav.reef.util.isPrefsInitialized
import dev.pranav.reef.util.prefs

@SuppressLint("AccessibilityPolicy")
class BlockerService : AccessibilityService() {
    private val notificationId = 2
    private var lastCheckTime = 0L
    private val checkInterval = 30000L

    override fun onServiceConnected() {
        if (!isPrefsInitialized) {
            prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        }

        createNotificationChannel()

        // Load routine limits when the service connects
        RoutineLimits.loadRoutineLimits()
        Log.d("BlockerService", "Service connected and routine limits loaded")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED ||
            event.eventType == AccessibilityEvent.TYPE_TOUCH_INTERACTION_START
        ) {
            val packageName = event.packageName?.toString() ?: return

            if (packageName == "dev.pranav.reef") {
                return
            }

            if (Whitelist.isWhitelisted(packageName)) {
                return
            }

            if (prefs.getBoolean("focus_mode", false)) {
                Log.d("BlockerService", "Blocking $packageName in focus mode")
                performGlobalAction(GLOBAL_ACTION_HOME)
                showBlockingNotification(packageName)
                return
            }

            val hasRoutineLimit = RoutineLimits.hasRoutineLimit(packageName)
            val hasRegularLimit = AppLimits.hasLimit(packageName)

            val currentTime = System.currentTimeMillis()
            val shouldCheckReminders = (currentTime - lastCheckTime) > checkInterval

            if (hasRoutineLimit) {
                val usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
                val routineUsageTime =
                    RoutineLimits.getRoutineUsageTime(packageName, usageStatsManager)
                val routineLimit = RoutineLimits.getRoutineLimit(packageName)

                if (shouldCheckReminders) {
                    val timeUntilLimit = routineLimit - routineUsageTime
                    if (timeUntilLimit in 1..600000 && !RoutineLimits.hasRoutineReminderBeenSent(
                            packageName
                        )
                    ) {
                        Log.d(
                            "BlockerService",
                            "Sending reminder for $packageName - ${timeUntilLimit / 60000} minutes remaining"
                        )
                        NotificationHelper.showReminderNotification(
                            this,
                            packageName,
                            timeUntilLimit
                        )
                        RoutineLimits.markRoutineReminderSent(packageName)
                    }
                    lastCheckTime = currentTime
                }

                if (routineUsageTime >= routineLimit) {
                    if (!RoutineLimits.hasRoutineGracePeriodStarted(packageName)) {
                        RoutineLimits.startRoutineGracePeriod(packageName)
                        NotificationHelper.showGracePeriodNotification(this, packageName)
                        return
                    }

                    if (RoutineLimits.isInRoutineGracePeriod(packageName)) {
                        return
                    }

                    showTimeLimitNotification(packageName, "routine")
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    return
                }
            }

            if (hasRegularLimit) {
                val usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
                val usageTime = AppLimits.getUsageTime(packageName, usageStatsManager)
                val limit = AppLimits.getLimit(packageName)

                if (shouldCheckReminders) {
                    val timeUntilLimit = limit - usageTime
                    if (timeUntilLimit in 1..600000 && !AppLimits.hasReminderBeenSent(packageName)) {
                        Log.d(
                            "BlockerService",
                            "Sending reminder for $packageName - ${timeUntilLimit / 60000} minutes remaining"
                        )
                        NotificationHelper.showReminderNotification(
                            this,
                            packageName,
                            timeUntilLimit
                        )
                        AppLimits.markReminderSent(packageName)
                    }
                    lastCheckTime = currentTime
                }

                if (usageTime >= limit) {
                    if (!AppLimits.hasGracePeriodStarted(packageName)) {
                        AppLimits.startGracePeriod(packageName)
                        NotificationHelper.showGracePeriodNotification(this, packageName)
                        return
                    }

                    if (AppLimits.isInGracePeriod(packageName)) {
                        return
                    }

                    showTimeLimitNotification(packageName, "regular")
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
            }
        }
    }

    private fun showBlockingNotification(packageName: String) {
        val appName =
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0))

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Distraction Blocked")
            .setSmallIcon(R.drawable.round_hourglass_disabled_24)
            .setContentText("You were using $appName")
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(this).notify(notificationId, builder.build())
        } else {
            Log.w("BlockerService", "Missing notification permission")
        }
    }


    private fun showTimeLimitNotification(packageName: String, limitType: String) {
        val appName = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0))
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }

        // Get the correct limit based on type
        val limit = if (limitType == "routine") {
            RoutineLimits.getRoutineLimit(packageName)
        } else {
            AppLimits.getLimit(packageName)
        }

        val intent = Intent(this, AppUsageActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val limitSource = if (limitType == "routine") {
            val activeRoutineId = RoutineLimits.getActiveRoutineId()
            val routine = activeRoutineId?.let { id ->
                RoutineManager.getRoutines().find { it.id == id }
            }
            "routine limit (${routine?.name ?: "Active Routine"})"
        } else {
            "daily limit"
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("$appName blocked for exceeding $limitSource")
            .setSmallIcon(R.drawable.round_hourglass_disabled_24)
            .setContentText("You've reached your ${getFormattedTime(limit)} limit")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("You've reached your ${getFormattedTime(limit)} $limitSource for $appName")
            )
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(this).notify(1, builder.build())
        } else {
            Log.w("AppLimitService", "Missing notification permission")
        }
    }

    override fun onInterrupt() {
        Log.d("BlockerService", "Accessibility service interrupted")
    }

    fun getFormattedTime(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        return String.format(java.util.Locale.getDefault(), "%02d:%02d", hours, minutes)
    }
}
