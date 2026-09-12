package com.deniz.eda.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.deniz.eda.R
import com.deniz.eda.ui.MainActivity
import android.app.PendingIntent
import android.content.Intent

object NotificationHelper {
    const val CHANNEL_ID = "eda_service_channel"
    const val NOTIF_ID = 1001

    fun channelOlustur(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val kanal = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(kanal)
        }
    }

    fun bildirimOlustur(context: Context, metin: String): android.app.Notification {
        val acilisIntent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(metin)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(acilisIntent)
            .setOngoing(true)
            .build()
    }
}
