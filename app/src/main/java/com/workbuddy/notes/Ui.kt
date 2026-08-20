package com.workbuddy.notes

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import java.io.File

/** 通用 UI 构造：编辑便签的弹窗（文本 + 颜色 + 图片 + 语音）。 */
object Ui {

    /**
     * 显示编辑弹窗，返回 dialog 供调用方更新图片/语音预览。
     *
     * @param onPickImage 点「📷 图片」时回调（调用方打开系统图库）
     * @param onRecordAudio 点「🎤 语音」时回调（调用方弹出录音对话框）
     */
    fun showEditor(
        context: Context,
        title: String,
        initialText: String,
        initialColor: String,
        imagePath: String? = null,
        audioPath: String? = null,
        audioDurationMs: Long = 0,
        onPickImage: () -> Unit = {},
        onRecordAudio: () -> Unit = {},
        onOk: (String, String) -> Unit
    ): AlertDialog {
        val dp = context.resources.displayMetrics.density
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (24 * dp).toInt(),
                (16 * dp).toInt(),
                (24 * dp).toInt(),
                (8 * dp).toInt()
            )
        }

        val et = EditText(context).apply {
            setText(initialText)
            hint = "写点什么…"
            minLines = 3
        }

        // ---- 附件按钮行：📷 图片 + 🎤 语音 ----
        val attachRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, (12 * dp).toInt(), 0, 0)
        }
        val btnImage = Button(context).apply {
            id = R.id.editor_btn_image
            text = "📷 图片"
            textSize = 13f
            setOnClickListener { onPickImage() }
        }
        val btnAudio = Button(context).apply {
            id = R.id.editor_btn_audio
            text = "🎤 语音"
            textSize = 13f
            setOnClickListener { onRecordAudio() }
        }
        attachRow.addView(
            btnImage,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = (8 * dp).toInt()
            }
        )
        attachRow.addView(
            btnAudio,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )

        // 图片预览（选了图才显示）
        val ivPreview = ImageView(context).apply {
            id = R.id.editor_image_preview
            visibility = View.GONE
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            maxHeight = (220 * dp).toInt()
        }
        if (!imagePath.isNullOrBlank()) {
            try {
                ivPreview.setImageURI(Uri.fromFile(File(imagePath)))
                ivPreview.visibility = View.VISIBLE
            } catch (_: Exception) {
            }
        }

        // 语音状态提示
        val tvAudioInfo = TextView(context).apply {
            id = R.id.editor_audio_info
            text = if (audioPath.isNullOrBlank()) "未添加语音"
            else "已添加语音 · ${audioDurationMs / 1000} 秒"
            setPadding(0, (8 * dp).toInt(), 0, 0)
            textSize = 13f
        }

        val label = TextView(context).apply {
            text = "选择颜色"
            setPadding(0, (16 * dp).toInt(), 0, (8 * dp).toInt())
        }

        val colorRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        var chosen = initialColor
        val swatches = mutableListOf<View>()

        ColorPalette.COLORS.forEach { hex ->
            val size = (36 * dp).toInt()
            val swatch = View(context).apply {
                setBackgroundColor(Color.parseColor(hex))
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    rightMargin = (10 * dp).toInt()
                }
                setOnClickListener {
                    chosen = hex
                    swatches.forEach { it.alpha = 0.35f }
                    this.alpha = 1f
                }
            }
            swatch.alpha = if (hex == initialColor) 1f else 0.35f
            swatches.add(swatch)
            colorRow.addView(swatch)
        }

        layout.addView(et)
        layout.addView(attachRow)
        layout.addView(ivPreview)
        layout.addView(tvAudioInfo)
        layout.addView(label)
        layout.addView(colorRow)

        val dialog = AlertDialog.Builder(context)
            .setTitle(title)
            .setView(layout)
            .setPositiveButton("保存") { _, _ ->
                onOk(et.text.toString().trim(), chosen)
            }
            .setNegativeButton("取消", null)
            .create()
        dialog.show()
        return dialog
    }

    /** 编辑弹窗打开期间，刷新图片预览（选完图后调用） */
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

    /** 编辑弹窗打开期间，刷新语音状态（录完音后调用） */
    fun updateAudioPreview(dialog: AlertDialog?, path: String?, durationMs: Long) {
        if (dialog == null) return
        val tv = dialog.findViewById<TextView>(R.id.editor_audio_info) ?: return
        tv.text = if (path.isNullOrBlank()) "未添加语音" else "已添加语音 · ${durationMs / 1000} 秒"
    }
}
