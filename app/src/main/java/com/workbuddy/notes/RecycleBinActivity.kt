package com.workbuddy.notes

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * 回收站：列出软删除的便签，支持恢复 / 彻底删除；
 * 进入时自动清理超过 [Note.TRASH_DAYS] 天的项目（防误删的兜底）。
 */
class RecycleBinActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotesStore.init(this)
        purgeExpired()

        val title = TextView(this).apply {
            text = "🗑 回收站"
            textSize = 20f
            setPadding(20, 24, 20, 12)
        }
        val emptyHint = TextView(this).apply {
            text = "（空空如也，删掉的便签会在这里停留 7 天）"
            setPadding(20, 12, 20, 12)
        }
        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val scroll = ScrollView(this).apply { addView(container) }

        val btnClear = Button(this).apply {
            text = "清空回收站"
            setOnClickListener { clearAll() }
        }
        val btnBack = Button(this).apply {
            text = "返回"
            setOnClickListener { finish() }
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 8, 16, 8)
            addView(btnBack, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(btnClear, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(resources.getColor(android.R.color.background_light, null))
            addView(title)
            addView(emptyHint)
            addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(bar)
        }
        setContentView(root)
        refresh(emptyHint)
    }

    override fun onResume() {
        super.onResume()
        AppLock.onResume()
    }

    override fun onPause() {
        AppLock.onPause()
        super.onPause()
    }

    private fun purgeExpired() {
        val cut = System.currentTimeMillis() - Note.TRASH_DAYS * 24L * 60 * 60 * 1000
        val all = NotesStore.all()
        val expired = all.filter { it.deleted && it.deletedAt < cut }
        expired.forEach { cleanAttachments(it) }
        all.removeAll(expired)
        NotesStore.save()
    }

    private fun refresh(emptyHint: TextView) {
        container.removeAllViews()
        val items = NotesStore.all().filter { it.deleted }.sortedByDescending { it.deletedAt }
        emptyHint.visibility = if (items.isEmpty()) TextView.VISIBLE else TextView.GONE
        items.forEach { note ->
            container.addView(buildRow(note))
        }
    }

    private fun buildRow(note: Note): LinearLayout {
        val dp = resources.displayMetrics.density
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(20, 10, 20, 10)
        }
        val tv = TextView(this).apply {
            text = if (note.locked) "🔒 加密便签" else note.text.takeIf { it.isNotBlank() }
                ?: (if (note.hasAnyImage()) "[图片/涂鸦]" else if (note.audioPath != null) "[语音]" else "（空便签）")
            textSize = 14f
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnRestore = Button(this).apply {
            text = "恢复"
            textSize = 12f
            setOnClickListener { restore(note) }
        }
        val btnDel = Button(this).apply {
            text = "彻底删除"
            textSize = 12f
            setOnClickListener { permanentDelete(note) }
        }
        row.addView(tv)
        row.addView(btnRestore)
        row.addView(btnDel)
        return row
    }

    private fun restore(note: Note) {
        note.deleted = false
        note.deletedAt = 0
        NotesStore.save()
        NotesWidgetProvider.updateAll(this)
        recreate()
    }

    private fun permanentDelete(note: Note) {
        AlertDialog.Builder(this)
            .setTitle("彻底删除")
            .setMessage("此操作不可恢复，确定吗？")
            .setPositiveButton("删除") { _, _ ->
                cleanAttachments(note)
                NotesStore.all().remove(note)
                NotesStore.save()
                NotesWidgetProvider.updateAll(this)
                recreate()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun clearAll() {
        val items = NotesStore.all().filter { it.deleted }
        if (items.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("清空回收站")
            .setMessage("将彻底删除 ${items.size} 条便签，不可恢复。")
            .setPositiveButton("清空") { _, _ ->
                items.forEach { cleanAttachments(it) }
                NotesStore.all().removeAll(items)
                NotesStore.save()
                NotesWidgetProvider.updateAll(this)
                recreate()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun cleanAttachments(note: Note) {
        Media.deleteFile(note.imagePath)
        Media.deleteFile(note.audioPath)
        Media.deleteFile(note.drawingPath)
    }
}
