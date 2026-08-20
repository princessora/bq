package com.workbuddy.notes

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import java.io.File

/** 把一条便签渲染成卡片 View，并接好「改 / 色 / 移 / 删」四个动作，以及图片/语音展示。 */
object Cards {
    fun create(
        context: Context,
        note: Note,
        onEdit: () -> Unit,
        onColor: () -> Unit,
        onMove: () -> Unit,
        onDelete: () -> Unit
    ): View {
        val view = LayoutInflater.from(context).inflate(R.layout.item_note, null)

        val tvText = view.findViewById<TextView>(R.id.tvText)
        tvText.text = if (note.text.isBlank() && note.imagePath == null && note.audioPath == null) {
            "（空便签）"
        } else {
            note.text
        }

        val bg = view.background
        if (bg is GradientDrawable) {
            try {
                bg.setColor(Color.parseColor(note.colorHex))
            } catch (_: Exception) {
                // 颜色解析失败则保留默认白底
            }
        }

        // ---- 配图 ----
        val ivImage = view.findViewById<ImageView>(R.id.ivImage)
        if (note.imagePath != null && File(note.imagePath!!).exists()) {
            ivImage.visibility = View.VISIBLE
            ivImage.setImageURI(Uri.fromFile(File(note.imagePath!!)))
            ivImage.setOnClickListener { showFullImage(context, note.imagePath!!) }
        } else {
            ivImage.visibility = View.GONE
        }

        // ---- 语音 ----
        val audioRow = view.findViewById<LinearLayout>(R.id.audioRow)
        val btnPlay = view.findViewById<Button>(R.id.btnPlay)
        val tvAudioDur = view.findViewById<TextView>(R.id.tvAudioDur)
        if (note.audioPath != null && File(note.audioPath!!).exists()) {
            audioRow.visibility = View.VISIBLE
            tvAudioDur.text = "${note.audioDurationMs / 1000} 秒"
            val refreshBtn = {
                btnPlay.text = if (AudioPlayer.isPlaying(note.audioPath)) "⏸ 停止" else "▶ 播放"
            }
            refreshBtn()
            btnPlay.setOnClickListener {
                AudioPlayer.toggle(note.audioPath) { refreshBtn() }
                refreshBtn()
            }
            audioRow.setOnClickListener { btnPlay.performClick() }
        } else {
            audioRow.visibility = View.GONE
        }

        view.findViewById<Button>(R.id.btnEdit).setOnClickListener { onEdit() }
        view.findViewById<Button>(R.id.btnColor).setOnClickListener { onColor() }
        view.findViewById<Button>(R.id.btnMove).setOnClickListener { onMove() }
        view.findViewById<Button>(R.id.btnDelete).setOnClickListener { onDelete() }

        return view
    }

    /** 点击配图后弹出大图查看 */
    private fun showFullImage(context: Context, path: String) {
        val iv = ImageView(context).apply {
            setImageURI(Uri.fromFile(File(path)))
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        AlertDialog.Builder(context)
            .setTitle("查看图片")
            .setView(iv)
            .setPositiveButton("关闭", null)
            .show()
    }
}
