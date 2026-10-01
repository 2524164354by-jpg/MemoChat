package com.memochat.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.memochat.app.MainActivity
import com.memochat.app.R

class TodayWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            val rv = RemoteViews(c.packageName, R.layout.widget_today)
            val pi = PendingIntent.getActivity(
                c, 0, Intent(c, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            rv.setOnClickPendingIntent(R.id.widget_title, pi)
            rv.setOnClickPendingIntent(R.id.widget_sub, pi)
            m.updateAppWidget(id, rv)
        }
    }
}
