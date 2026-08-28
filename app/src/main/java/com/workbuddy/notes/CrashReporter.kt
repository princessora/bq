package com.workbuddy.notes

import android.content.Context
import android.content.Intent
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全局未捕获异常捕获器。
 *
 * 用途：本项目在 CI 上「编译通过」但真机上偶发启动崩溃时，普通用户没有 adb 环境、
 * 看不到 logcat。此工具把堆栈写进私有文件，并在「下次启动」或「崩溃时」跳到一个
 * 纯文本页面展示，用户截图即可反馈，便于定位。
 *
 * 设计要点：
 * - [install] 在 Application.onCreate 最早调用，覆盖 Application/Activity 全阶段。
 * - handler 先把堆栈落盘（crash.txt），再交给系统默认行为；避免「直接吞掉异常导致
 *   进程挂起」的副作用。
 * - [peek] 在 Activity.onCreate 最开头（super.onCreate 之前）判断是否有未消费的崩溃，
 *   若有则立即转 [launch] 展示并 finish，避免再次触发同一崩溃形成死循环。
 */
object CrashReporter {

    private const val FILE = "crash.txt"

    fun install(ctx: Context) {
        val def = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sb = StringBuilder()
                sb.append("时间: ")
                    .append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
                    .append("\n线程: ").append(t.name).append("\n")
                sb.append("异常: ").append(e.javaClass.name).append(": ").append(e.message ?: "")
                    .append("\n\n")
                sb.append(android.util.Log.getStackTraceString(e))
                ctx.openFileOutput(FILE, Context.MODE_PRIVATE).use {
                    it.write(sb.toString().toByteArray())
                }
            } catch (_: Exception) {
                // 写盘失败也不要影响崩溃流程
            }
            def?.uncaughtException(t, e)
        }
    }

    /** 是否已有待消费的崩溃记录（不删除，留给 [launch] 处理）。 */
    fun peek(ctx: Context): Boolean = File(ctx.filesDir, FILE).exists()

    /** 读取并清除崩溃记录，跳到崩溃展示页。调用方应随后 finish() 当前 Activity。 */
    fun launch(ctx: Context) {
        val f = File(ctx.filesDir, FILE)
        val stack = if (f.exists()) {
            try { f.readText() } catch (_: Exception) { "(崩溃记录存在但无法读取)" }
        } else "(无崩溃记录)"
        try { f.delete() } catch (_: Exception) {}

        val intent = Intent(ctx, CrashActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra("stack", stack)
        }
        ctx.startActivity(intent)
    }
}
