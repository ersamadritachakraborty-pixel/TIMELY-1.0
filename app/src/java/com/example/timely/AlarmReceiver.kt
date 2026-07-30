package com.example.timely

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("REMINDER_TITLE") ?: "Timely Alert"
        val message = intent.getStringExtra("REMINDER_MESSAGE") ?: "Time to check your timeline!"
        val urgencyStr = intent.getStringExtra("REMINDER_URGENCY") ?: "NORMAL"
        val urgency = try { UrgencyLevel.valueOf(urgencyStr) } catch (_: Exception) { UrgencyLevel.NORMAL }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "timely_alerts"
        val color = if (urgency == UrgencyLevel.CRITICAL) 0xFFFF4B4B.toInt() else 0xFF00F2FE.toInt()
        val importance = NotificationManager.IMPORTANCE_HIGH

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Timely Notifications", importance).apply {
                description = "MNC Style Alerts for your Timeline"
                enableLights(true)
                lightColor = color
                vibrationPattern = if (urgency == UrgencyLevel.CRITICAL) longArrayOf(0, 500, 200, 500) else longArrayOf(0, 200, 100, 200)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(message)
            .setColor(color)
            .setPriority(if (urgency == UrgencyLevel.CRITICAL) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
