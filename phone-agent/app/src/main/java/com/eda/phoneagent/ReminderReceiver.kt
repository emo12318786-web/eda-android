package com.eda.phoneagent

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val message = intent.getStringExtra("message") ?: "Hatırlatıcın var."
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = "eda_reminders"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(channel, "Eda hatırlatıcıları", NotificationManager.IMPORTANCE_HIGH))
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            android.app.Notification.Builder(context, channel)
        else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(context)
        }
        manager.notify(intent.getIntExtra("id", 1001), builder
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Eda · Hatırlatıcı")
            .setContentText(message).setAutoCancel(true).build())
    }
}
