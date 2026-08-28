package com.workbuddy.notes

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * 崩溃信息展示页：纯文本显示上次崩溃堆栈，可复制或截图反馈。
 * 页面极简（仅 TextView + 两个按钮），自身不会再次崩溃。
 */
class CrashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crash)

        val stack = intent.getStringExtra("stack") ?: "(无堆栈信息)"
        findViewById<TextView>(R.id.crashText).text = stack

        findViewById<Button>(R.id.btnCopy).setOnClickListener {
            try {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("crash", stack))
                Toast.makeText(this, "已复制，可粘贴发给我", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(this, "复制失败，请直接截图", Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<Button>(R.id.btnClose).setOnClickListener {
            finishAffinity()
        }
    }
}
