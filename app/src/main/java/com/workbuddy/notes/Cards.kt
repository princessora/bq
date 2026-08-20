package com.workbuddy.notes

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import java.io.File
import java.util.Calendar

/** 把一条便签渲染成卡片 View，并接好「改 / 色 / 移 / 删」动作，以及图片/涂鸦/语音/标签/置顶/收藏/加密展示。 */
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
        onLocation: () -> Unit,
        highlight: String? = null
    ): View {
        val view = LayoutInflater.from(context).inflate(R.layout.item_note, null)

        val tvText = view.findViewById<TextView>(R.id.tvText)
        val ivImage = view.findViewById<ImageView>(R.id.ivImage)
        val ivDraw = view.findViewById<ImageView>(R.id.ivDraw)
        val audioRow = view.findViewById<LinearLayout>(R.id.audioRow)
        val btnPlay = view.findViewById<TextView>(R.id.btnPlay)
        val tvAudioDur = view.findViewById<TextView>(R.id.tvAudioDur)
        val tvTags = view.findViewById<TextView>(R.id.tvTags)
        val tvMeta = view.findViewById<TextView>(R.id.tvMeta)
        val btnEdit = view.findViewById<TextView>(R.id.btnEdit)
        val btnColor = view.findViewById<TextView>(R.id.btnColor)
        val btnMove = view.findViewById<TextView>(R.id.btnMove)
        val btnDelete = view.findViewById<TextView>(R.id.btnDelete)
        val btnShare = view.findViewById<TextView>(R.id.btnShare)

        val bg = view.background
        if (bg is GradientDrawable) {
            try {
                bg.setColor(Color.parseColor(effectiveCardColor(context, note.colorHex)))
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

        // ---- 指示器（置顶/收藏/加密/纪念日/位置） ----
        val meta = buildMeta(note)
        if (meta.isNotEmpty()) {
            tvMeta.visibility = View.VISIBLE
            tvMeta.text = meta
        } else {
            tvMeta.visibility = View.GONE
        }
        if (note.latitude != null || !note.locationName.isNullOrBlank()) {
            tvMeta.setOnClickListener { onLocation() }
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
        // 搜索高亮：命中词标黄色底
        val q = highlight?.trim()
        if (!q.isNullOrBlank() && note.text.isNotBlank()) {
            tvText.text = highlightSpans(note.text, q)
        }

        // ---- 配图 ----
        if (note.imagePath != null && File(note.imagePath!!).exists()) {
            ivImage.visibility = View.VISIBLE
            ivImage.setImageURI(Uri.fromFile(File(note.imagePath!!)))
            ivImage.setOnClickListener { showFullImage(context, note.imagePath!!) }
        } else {
            ivImage.visibility = View.GONE
        }

        // ---- 涂鸦 ----
        if (note.drawingPath != null && File(note.drawingPath!!).exists()) {
            ivDraw.visibility = View.VISIBLE
            ivDraw.setImageURI(Uri.fromFile(File(note.drawingPath!!)))
            ivDraw.setOnClickListener { showFullImage(context, note.drawingPath!!) }
        } else {
            ivDraw.visibility = View.GONE
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

    /**
     * 夜间模式下，如果便签底色是「白/浅色」，自动换成深色 surface，
     * 避免卡片在深色界面上过亮、文字看不全（夜间字色已跟随 values-night 反相为白）。
     * 用户主动选的彩色（红/橙/黄/绿/蓝/紫等）则保留。
     */
    private fun effectiveCardColor(context: Context, hex: String): String {
        val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        if (!night) return hex
        val isLightish = isLightColor(hex)
        return if (isLightish) "#1E1E1E" else hex
    }

    /** 颜色是否偏白/浅（HSV 亮度阈值 0.85，且饱和度低） */
    private fun isLightColor(hex: String): Boolean {
        return try {
            val c = Color.parseColor(hex)
            val r = Color.red(c) / 255f
            val g = Color.green(c) / 255f
            val b = Color.blue(c) / 255f
            val brightness = (r * 0.299f + g * 0.587f + b * 0.114f)
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            val sat = if (max == 0f) 0f else (max - min) / max
            brightness >= 0.85f && sat <= 0.20f
        } catch (_: Exception) {
            true
        }
    }

    /** 拼接指示器文案：置顶 / 收藏 / 加密 / 纪念日(含农历) / 位置 */
    private fun buildMeta(note: Note): String {
        val parts = mutableListOf<String>()
        if (note.pinned) parts += "📌"
        if (note.favorite) parts += "⭐"
        if (note.locked) parts += "🔒"
        if (note.eventDate != null) {
            val cal = Calendar.getInstance().apply { timeInMillis = note.eventDate!! }
            val lunar = try { " · ${Lunar.lunarMonthDay(cal)}" } catch (_: Exception) { "" }
            parts += "📅${note.eventLabel ?: ""}${Ui.countdownText(note.eventDate!!)}$lunar"
        }
        if (note.latitude != null || !note.locationName.isNullOrBlank()) {
            parts += "📍${note.locationName ?: "坐标"}"
        }
        return parts.joinToString(" ")
    }

    /** 搜索词高亮：所有不区分大小写的命中处加黄色背景 */
    private fun highlightSpans(text: String, query: String): Spannable {
        val sp = SpannableString(text)
        val q = query.lowercase()
        val lower = text.lowercase()
        var from = 0
        while (true) {
            val idx = lower.indexOf(q, from)
            if (idx < 0) break
            sp.setSpan(
                BackgroundColorSpan(0x66FFC107),
                idx, idx + q.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            from = idx + q.length
        }
        return sp
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
