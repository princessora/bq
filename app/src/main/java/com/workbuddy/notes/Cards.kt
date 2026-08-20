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

/** 把一条便签渲染成卡片 View，并接好「改 / 色 / 移 / 删」动作，以及图片/语音/标签/加密展示。 */
object Cards {
    fun create(
        context: Context,
        note: Note,
        onEdit: () -> Unit,
        onColor: () -> Unit,
        onMove: () -> Unit,
        onDelete: () -> Unit,
        onShare: () -> Unit,
        onUnlock: () -> Unit,
        onLocation: () -> Unit
    ): View {
        val view = LayoutInflater.from(context).inflate(R.layout.item_note, null)

        val tvText = view.findViewById<TextView>(R.id.tvText)
        val ivImage = view.findViewById<ImageView>(R.id.ivImage)
        val ivDraw = view.findViewById<ImageView>(R.id.ivDraw)
        val audioRow = view.findViewById<LinearLayout>(R.id.audioRow)
        val btnPlay = view.findViewById<Button>(R.id.btnPlay)
        val tvAudioDur = view.findViewById<TextView>(R.id.tvAudioDur)
        val tvTags = view.findViewById<TextView>(R.id.tvTags)
        val tvMeta = view.findViewById<TextView>(R.id.tvMeta)
        val btnEdit = view.findViewById<Button>(R.id.btnEdit)
        val btnColor = view.findViewById<Button>(R.id.btnColor)
        val btnMove = view.findViewById<Button>(R.id.btnMove)
        val btnDelete = view.findViewById<Button>(R.id.btnDelete)
        val btnShare = view.findViewById<Button>(R.id.btnShare)
        btnShare.visibility = View.GONE

        val bg = view.background
        if (bg is GradientDrawable) {
            try {
                bg.setColor(Color.parseColor(note.colorHex))
            } catch (_: Exception) {
                // 颜色解析失败则保留默认白底
            }
        }

        // ---- 标签 ----
        val tags = note.tagList()
        if (tags.isNotEmpty()) {
            tvTags.visibility = View.VISIBLE
            tvTags.text = tags.joinToString(" ") { "#$it" }
        } else {
            tvTags.visibility = View.GONE
        }

        // ---- 指示器（加密） ----
        val meta = buildMeta(note)
        if (meta.isNotEmpty()) {
            tvMeta.visibility = View.VISIBLE
            tvMeta.text = meta
        } else {
            tvMeta.visibility = View.GONE
        }

        val btnEditAction: () -> Unit = { if (note.locked) onUnlock() else onEdit() }
        btnEdit.setOnClickListener { btnEditAction() }

        // ---- 加密便签：遮罩内容，点击解锁 ----
        if (note.locked) {
            tvText.text = "🔒 已加密，点击解锁查看"
            ivImage.visibility = View.GONE
            ivDraw.visibility = View.GONE
            audioRow.visibility = View.GONE
            view.setOnClickListener { onUnlock() }
            btnColor.setOnClickListener { onUnlock() }
            btnMove.setOnClickListener { onUnlock() }
            btnDelete.setOnClickListener { onDelete() }
            btnShare.setOnClickListener { onShare() }
            return view
        }

        tvText.text = if (note.text.isBlank() && !note.hasAnyImage() && note.audioPath == null) {
            "（空便签）"
        } else {
            note.text
        }

        // ---- 配图 ----
        if (note.imagePath != null && File(note.imagePath!!).exists()) {
            ivImage.visibility = View.VISIBLE
            ivImage.setImageURI(Uri.fromFile(File(note.imagePath!!)))
            ivImage.setOnClickListener { showFullImage(context, note.imagePath!!) }
        } else {
            ivImage.visibility = View.GONE
        }

        // ---- 语音 ----
        if (note.audioPath != null && File(note.audioPath!!).exists()) {
            audioRow.visibility = View.VISIBLE
            tvAudioDur.text = "${note.audioDurationMs / 1000} 秒"
            val refresh = {
                btnPlay.text = if (AudioPlayer.isPlaying(note.audioPath)) "⏸ 停止" else "▶ 播放"
            }
            refresh()
            btnPlay.setOnClickListener {
                AudioPlayer.toggle(note.audioPath) { refresh() }
                refresh()
            }
            audioRow.setOnClickListener { btnPlay.performClick() }
        } else {
            audioRow.visibility = View.GONE
        }

        btnColor.setOnClickListener { onColor() }
        btnMove.setOnClickListener { onMove() }
        btnDelete.setOnClickListener { onDelete() }
        btnShare.setOnClickListener { onShare() }
        return view
    }

    /** 拼接指示器文案（Batch 1：仅加密标记） */
    private fun buildMeta(note: Note): String {
        return if (note.locked) "🔒" else ""
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
