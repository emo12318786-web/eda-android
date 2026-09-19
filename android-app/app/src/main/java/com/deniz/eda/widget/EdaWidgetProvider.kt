package com.deniz.eda.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.deniz.eda.R
import com.deniz.eda.core.Settings
import com.deniz.eda.panel.PanelActivity
import com.deniz.eda.ui.dashboard.DashboardActivity

/**
 * EDA Home Screen Widget — Termux:Widget karsiligi.
 * 5 düğme: Başlat, Durdur, Uyku, Panel, Durum
 */
class EdaWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (widgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_eda)

            // 🟢 Başlat
            views.setOnClickPendingIntent(
                R.id.btn_baslat,
                widgetAction(context, "com.deniz.eda.WIDGET_BASLAT", 1)
            )

            // ❌ Durdur
            views.setOnClickPendingIntent(
                R.id.btn_durdur,
                widgetAction(context, "com.deniz.eda.WIDGET_DURDUR", 2)
            )

            // 💤 Uyku
            views.setOnClickPendingIntent(
                R.id.btn_uyku,
                widgetAction(context, "com.deniz.eda.WIDGET_UYKU", 3)
            )

            // 🎛 Panel
            val panelIntent = Intent(context, PanelActivity::class.java)
            val panelPending = PendingIntent.getActivity(
                context, 4, panelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_panel, panelPending)

            // 📊 Durum (Dashboard)
            val dashIntent = Intent(context, DashboardActivity::class.java)
            val dashPending = PendingIntent.getActivity(
                context, 5, dashIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_durum, dashPending)

            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            "com.deniz.eda.WIDGET_BASLAT" -> {
                Settings.arabaModuAktif = false
                // Servisi başlat
                val serviceIntent = Intent(context, com.deniz.eda.service.EdaForegroundService::class.java)
                context.startForegroundService(serviceIntent)
            }
            "com.deniz.eda.WIDGET_DURDUR" -> {
                // Servisi durdur
                val serviceIntent = Intent(context, com.deniz.eda.service.EdaForegroundService::class.java)
                context.stopService(serviceIntent)
            }
            "com.deniz.eda.WIDGET_UYKU" -> {
                Settings.uykuBeklemeAraligiMs = 60000L
            }
        }
    }

    private fun widgetAction(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, EdaWidgetProvider::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
