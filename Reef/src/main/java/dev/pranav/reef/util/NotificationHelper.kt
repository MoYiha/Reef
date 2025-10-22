package dev.pranav.reef.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.pranav.reef.R
import dev.pranav.reef.RoutinesActivity
import dev.pranav.reef.data.Routine

object NotificationHelper {
    private const val ROUTINE_NOTIFICATION_ID = 100
    private const val REMINDER_NOTIFICATION_ID = 200
    private const val GRACE_PERIOD_NOTIFICATION_ID = 300

    fun Context.createNotificationChannel() {
        val descriptionText = "Shows reminders for screen time and when apps are blocked."
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(CHANNEL_ID, "Content Blocker", importance).apply {
            description = descriptionText
            setSound(null, null)
        }
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    fun showRoutineActivatedNotification(context: Context, routine: Routine) {
        val intent = Intent(context, RoutinesActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val limitsText = when (routine.limits.size) {
            0 -> "No app limits applied"
            1 -> "1 app limit applied"
            else -> "${routine.limits.size} app limits applied"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Routine Activated")
            .setContentText("${routine.name} - $limitsText")
            .setSmallIcon(R.drawable.round_schedule_24)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(ROUTINE_NOTIFICATION_ID, builder.build())
        }
    }

    fun showRoutineDeactivatedNotification(context: Context, routine: Routine) {
        val intent = Intent(context, RoutinesActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Routine Deactivated")
            .setContentText("${routine.name} has ended")
            .setSmallIcon(R.drawable.round_schedule_24)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context)
                .notify(ROUTINE_NOTIFICATION_ID + 1, builder.build())
        }
    }
    
    fun showReminderNotification(context: Context, packageName: String, timeRemaining: Long) {
        val appName = try {
            context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(packageName, 0)
            )
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
        
        val minutes = (timeRemaining / 60000).toInt()
        
        val intent = Intent(context, dev.pranav.reef.AppUsageActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Time Limit Reminder")
            .setContentText("$appName will be blocked in $minutes minutes")
            .setSmallIcon(R.drawable.round_hourglass_disabled_24)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(
                REMINDER_NOTIFICATION_ID + packageName.hashCode(), 
                builder.build()
            )
        }
    }
    
    fun showGracePeriodNotification(context: Context, packageName: String) {
        val appName = try {
            context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(packageName, 0)
            )
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
        
        val intent = Intent(context, dev.pranav.reef.AppUsageActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Time Limit - Final Warning")
            .setContentText("$appName will be blocked in 5 minutes. Please finish up.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("You've reached your time limit for $appName. The app will be blocked in 5 minutes. Please wrap up your current activity.")
            )
            .setSmallIcon(R.drawable.round_hourglass_disabled_24)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(
                GRACE_PERIOD_NOTIFICATION_ID + packageName.hashCode(), 
                builder.build()
            )
        }
    }
}
