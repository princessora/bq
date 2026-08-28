package com.workbuddy.notes

import android.app.Application

/**
 * 自定义 Application：在任意 Activity 创建之前安装全局崩溃捕获器，
 * 确保应用启动早期（含主题 inflate、Fragment 事务）的崩溃也能被记录。
 */
class NotesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
    }
}
