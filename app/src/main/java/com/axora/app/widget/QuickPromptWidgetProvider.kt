package com.axora.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.axora.app.R

// Виджет «быстрый промпт»: тап открывает чат (MainActivity), поле ввода получает фокус.
// Ввод промпта на самом виджете не делаем — RemoteViews-TextField на экране виджета в Android
// ловит IME-баги на разных лаунчерах; вместо этого мгновенный переход в приложение.
class QuickPromptWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> appWidgetManager.updateAppWidget(id, buildViews(context)) }
    }

    private fun buildViews(context: Context): RemoteViews {
        val intent = Intent(context, com.axora.app.ui.MainActivity::class.java).apply {
            action = ACTION_OPEN_PROMPT
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return RemoteViews(context.packageName, R.layout.widget_quick_prompt).apply {
            setOnClickPendingIntent(R.id.widget_root, pending)
        }
    }

    companion object {
        const val ACTION_OPEN_PROMPT = "com.axora.app.action.OPEN_PROMPT"
    }
}
