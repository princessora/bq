package com.workbuddy.notes

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * 统一管理定时提醒：遍历便签中设置了「日期 + 时间」的条目，用 AlarmManager 注册精确闹钟。
 * 设计为「先取消旧闹钟、再按当前数据重排」，因此可反复调用（幂等），每次保存便签后调用一次即可。
 */
object ReminderScheduler {

    /** 全量重排：先取消全部提醒闹钟，再按当前数据重新设置。 */
    fun scheduleAll(context: Context) {
        // 直接读磁盘，兼容「开机自启」场景（此时内存单例可能尚未初始化）
        val notes = NotesRepository.load(context)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (note in notes) {
            // 软删除 / 未设时间 / 锁定便签 都不参与提醒：
            // 锁定便签正文是加密内容，绝不允许出现在系统通知栏（避免明文泄露）
            if (note.deleted || note.locked) continue
            if (note.eventDate == null || note.eventTime == null) continue
            val pi = buildIntent(context, note)
            try {
                am.cancel(pi)
            } catch (_: Exception) {
                // 忽略：可能原本就没有这条闹钟
            }
            val next = note.nextRemindAt(System.currentTimeMillis()) ?: continue
            // 已过期（如单次）不再设置；未来的时间点才注册
            if (next <= System.currentTimeMillis()) continue
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
            } catch (e: Exception) {
                // 精确闹钟权限被用户关闭或机型限制：降级为不提醒，不崩溃
                e.printStackTrace()
            }
        }
    }

    /** 关闭 App 卸载前：取消全部闹钟（防止残留）。 */
    fun cancelAll(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        NotesRepository.load(context).forEach { note ->
            if (note.eventDate != null && note.eventTime != null) {
                try { am.cancel(buildIntent(context, note)) } catch (_: Exception) {}
            }
        }
    }

    private fun buildIntent(context: Context, note: Note): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION
            putExtra(ReminderReceiver.EXTRA_NOTE_ID, note.id)
            putExtra(ReminderReceiver.EXTRA_TITLE, note.eventLabel ?: "札记提醒")
            putExtra(
                ReminderReceiver.EXTRA_BODY,
                note.text.takeIf { it.isNotBlank() } ?: "该打卡 / 纪念日到了"
            )
        }
        // requestCode 用 id 的 hash，同一便签可覆盖旧闹钟
        return PendingIntent.getBroadcast(
            context,
            note.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
