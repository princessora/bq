package com.workbuddy.notes

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

/**
 * 自定义 Application：在任意 Activity 创建之前安装全局崩溃捕获器，
 * 确保应用启动早期（含主题 inflate、Fragment 事务）的崩溃也能被记录；
 * 并提前应用用户选择的深色模式，避免第一帧白闪。
 */
class NotesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 深色模式：必须在任何 Activity inflate 之前设定
        AppCompatDelegate.setDefaultNightMode(
            if (AppSettings.isDark(this)) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
        CrashReporter.install(this)
    }
}
