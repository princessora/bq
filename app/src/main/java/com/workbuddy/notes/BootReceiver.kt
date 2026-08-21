package com.workbuddy.notes

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 开机完成后重新调度所有提醒（闹钟在重启后会丢失，需重新挂回）。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ReminderScheduler.scheduleAll(context)
        }
    }
}
