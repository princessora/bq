package com.workbuddy.notes

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * 接收定时闹钟广播，弹出系统通知。
 * 通知内容只展示便签标题/正文摘要，不泄露锁定便签的内容（锁定便签不会进入提醒调度）。
 */
class ReminderReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION = "com.workbuddy.notes.REMIND"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        const val CHANNEL_ID = "notes_reminder"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "札记提醒"
        val body = intent.getStringExtra(EXTRA_BODY) ?: "该打卡 / 纪念日到了"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "便签提醒", NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "便签设置的纪念日与定时提醒" }
            nm.createNotificationChannel(channel)
        }
        val openApp = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        // 用标题 hash 作 id，避免不同提醒互相覆盖；冲突概率极低可接受
        nm.notify(title.hashCode(), notification)
    }
}
