package com.workbuddy.notes

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import java.io.File

/** 通用 UI 构造：编辑便签的弹窗（文本 + 颜色 + 图片 + 语音 + 标签 + 加密）。 */
object Ui {

    /**
     * 显示编辑弹窗。直接修改传入的可变 [note] 对象，保存时由调用方持久化。
     *
     * @param onPickImage 点「📷 图片」回调
     * @param onRecordAudio 点「🎤 语音」回调
     * @param onOk 点「保存」后回调（此时 note 各字段已写入）
     */
    fun showEditor(
        context: Context,
        title: String,
        note: Note,
        onPickImage: () -> Unit = {},
        onRecordAudio: () -> Unit = {},
        onOk: () -> Unit
    ): AlertDialog {
        val dp = context.resources.displayMetrics.density

        val scroll = ScrollView(context).apply {
            setPadding((16 * dp).toInt(), (4 * dp).toInt(), (16 * dp).toInt(), 0)
        }
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (8 * dp).toInt(), 0, (8 * dp).toInt())
        }

        val et = EditText(context).apply {
            setText(note.text)
            hint = "写点什么…"
            minLines = 3
            gravity = Gravity.TOP
        }

        // ---- 附件按钮：两行各三个 ----
        val row1 = mkBtnRow(
            context, dp, listOf(
                "📷 图片" to onPickImage,
                "🎤 语音" to onRecordAudio
            )
        )

        // 配图预览
        val ivPreview = ImageView(context).apply {
            id = R.id.editor_image_preview
            visibility = View.GONE
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            maxHeight = (180 * dp).toInt()
        }
        if (!note.imagePath.isNullOrBlank()) {
            try {
                ivPreview.setImageURI(Uri.fromFile(File(note.imagePath!!)))
                ivPreview.visibility = View.VISIBLE
            } catch (_: Exception) {
            }
        }

        // 标签
        val etTags = EditText(context).apply {
            id = R.id.editor_tags
            hint = "标签，逗号分隔，如 旅行,待办"
            setText(note.tags ?: "")
            textSize = 13f
            setPadding(0, (8 * dp).toInt(), 0, 0)
        }

        // 加密
        val cbLock = CheckBox(context).apply {
            id = R.id.editor_lock
            text = "🔒加密"
            isChecked = note.locked
            textSize = 13f
        }
        val toggleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, (8 * dp).toInt(), 0, 0)
            addView(cbLock, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }

        // 颜色
        val label = TextView(context).apply {
            text = "选择颜色"
            setPadding(0, (12 * dp).toInt(), 0, (8 * dp).toInt())
        }
        val colorRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val swatches = mutableListOf<View>()
        ColorPalette.COLORS.forEach { hex ->
            val size = (34 * dp).toInt()
            val swatch = View(context).apply {
                setBackgroundColor(Color.parseColor(hex))
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    rightMargin = (10 * dp).toInt()
                }
                setOnClickListener {
                    note.colorHex = hex
                    swatches.forEach { it.alpha = 0.35f }
                    this.alpha = 1f
                }
            }
            swatch.alpha = if (hex == note.colorHex) 1f else 0.35f
            swatches.add(swatch)
            colorRow.addView(swatch)
        }
        // 若当前色不在调色板内（旧数据），至少保持初始选中态
        if (ColorPalette.COLORS.none { it == note.colorHex }) swatches.firstOrNull()?.let {
            it.alpha = 1f; note.colorHex = ColorPalette.COLORS.first()
        }

        layout.addView(et)
        layout.addView(row1)
        layout.addView(ivPreview)
        layout.addView(etTags)
        layout.addView(toggleRow)
        layout.addView(label)
        layout.addView(colorRow)
        scroll.addView(layout)

        val dialog = AlertDialog.Builder(context)
            .setTitle(title)
            .setView(scroll)
            .setPositiveButton("保存") { _, _ ->
                note.text = et.text.toString().trim()
                note.tags = etTags.text.toString().trim()
                note.locked = cbLock.isChecked
                onOk()
            }
            .setNegativeButton("取消", null)
            .create()
        dialog.show()
        return dialog
    }

    private fun mkBtnRow(
        context: Context,
        dp: Float,
        items: List<Pair<String, () -> Unit>>
    ): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, (8 * dp).toInt(), 0, 0)
        }
        items.forEachIndexed { i, (label, action) ->
            val b = Button(context).apply {
                text = label
                textSize = 12f
                setOnClickListener { action() }
            }
            row.addView(
                b,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (i < items.lastIndex) rightMargin = (6 * dp).toInt()
                }
            )
        }
        return row
    }

    /** 编辑弹窗打开期间，刷新配图预览 */
    fun updateImagePreview(dialog: AlertDialog?, path: String?) {
        if (dialog == null) return
        val iv = dialog.findViewById<ImageView>(R.id.editor_image_preview) ?: return
        if (path.isNullOrBlank()) {
            iv.visibility = View.GONE
        } else {
            try {
                iv.setImageURI(Uri.fromFile(File(path)))
                iv.visibility = View.VISIBLE
            } catch (_: Exception) {
            }
        }
    }

    /** 编辑弹窗打开期间，刷新涂鸦预览 */
    fun updateDrawPreview(dialog: AlertDialog?, path: String?) {
        if (dialog == null) return
        val iv = dialog.findViewById<ImageView>(R.id.editor_draw_preview) ?: return
        if (path.isNullOrBlank()) {
            iv.visibility = View.GONE
        } else {
            try {
                iv.setImageURI(Uri.fromFile(File(path)))
                iv.visibility = View.VISIBLE
            } catch (_: Exception) {
            }
        }
    }

    /** 编辑弹窗打开期间，刷新语音状态 */
    fun updateAudioPreview(dialog: AlertDialog?, path: String?, durationMs: Long) {
        if (dialog == null) return
        val tv = dialog.findViewById<TextView>(R.id.editor_audio_info) ?: return
        tv.text = if (path.isNullOrBlank()) "未添加语音" else "已添加语音 · ${durationMs / 1000} 秒"
    }

    /** 编辑弹窗打开期间，刷新日期信息 */
    fun updateDatePreview(dialog: AlertDialog?, note: Note) {
        if (dialog == null) return
        val tv = dialog.findViewById<TextView>(R.id.editor_date_info) ?: return
        tv.text = if (note.eventDate != null) {
            "📅 ${note.eventLabel ?: "纪念日"} · ${countdownText(note.eventDate!!)}"
        } else "未设置日期"
    }

    /** 编辑弹窗打开期间，刷新位置信息 */
    fun updateLocationPreview(dialog: AlertDialog?, note: Note) {
        if (dialog == null) return
        val tv = dialog.findViewById<TextView>(R.id.editor_location_info) ?: return
        tv.text = if (!note.locationName.isNullOrBlank() || note.latitude != null) {
            "📍 ${note.locationName ?: "已记录坐标"}"
        } else "未记录位置"
    }

    /** 模板选择后，回填编辑框文本（保留原有内容时由调用方决定覆盖） */
    fun setEditorText(dialog: AlertDialog?, text: String) {
        if (dialog == null) return
        // 编辑框没有固定 id，遍历找到第一个 EditText
        val et = dialog.findViewById<EditText>(android.R.id.text1)
            ?: run {
                val root = dialog.window?.decorView
                findFirstEditText(root)
            }
        et?.setText(text)
    }

    private fun findFirstEditText(view: View?): EditText? {
        if (view is EditText) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val r = findFirstEditText(view.getChildAt(i))
                if (r != null) return r
            }
        }
        return null
    }

    /** 距离目标日期的倒计时文案 */
    fun countdownText(target: Long): String {
        val now = System.currentTimeMillis()
        val dayMs = 24L * 60 * 60 * 1000
        val diff = ((target - now) / dayMs).toInt()
        return when {
            diff > 0 -> "还有 $diff 天"
            diff == 0 -> "就是今天"
            else -> "已过 ${-diff} 天"
        }
    }
}
