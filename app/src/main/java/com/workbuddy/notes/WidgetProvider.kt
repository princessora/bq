package com.workbuddy.notes

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** 桌面小部件：显示最近几条便签，点击打开 App。 */
class NotesWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        NotesStore.init(context)
        ids.forEach { update(context, mgr, it) }
    }

    companion object {
        /** 任意数据变更后调用，刷新所有已放置的小部件。 */
        fun updateAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, NotesWidgetProvider::class.java))
            ids.forEach { update(context, mgr, it) }
        }

        private fun update(context: Context, mgr: AppWidgetManager, id: Int) {
            NotesStore.init(context)
            val rv = RemoteViews(context.packageName, R.layout.widget)

            val notes = NotesStore.all()
                .filter { !it.deleted }
                .sortedByDescending { it.createdAt }
                .take(5)

            val sb = StringBuilder()
            if (notes.isEmpty()) {
                sb.append("还没有便签，点此速记～")
            } else {
                notes.forEach { n ->
                    val line = when {
                        n.locked -> "🔒 加密便签"
                        n.text.isNotBlank() -> n.text
                        n.hasAnyImage() -> "[图片 / 涂鸦]"
                        n.audioPath != null -> "[语音]"
                        else -> "（空便签）"
                    }
                    sb.append("• ").append(line.take(28)).append("\n")
                }
            }
            rv.setTextViewText(R.id.widget_text, sb.toString())

            val intent = Intent(context, MainActivity::class.java)
            val pi = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            rv.setOnClickPendingIntent(R.id.widget_root, pi)
            mgr.updateAppWidget(id, rv)
        }
    }
}
