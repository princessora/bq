package com.workbuddy.notes

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView

/** 把一条便签渲染成卡片 View，并接好「改 / 色 / 移 / 删」四个动作。 */
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
        tvText.text = if (note.text.isBlank()) "（空便签）" else note.text

        val bg = view.background
        if (bg is GradientDrawable) {
            try {
                bg.setColor(Color.parseColor(note.colorHex))
            } catch (_: Exception) {
                // 颜色解析失败则保留默认白底
            }
        }

        view.findViewById<Button>(R.id.btnEdit).setOnClickListener { onEdit() }
        view.findViewById<Button>(R.id.btnColor).setOnClickListener { onColor() }
        view.findViewById<Button>(R.id.btnMove).setOnClickListener { onMove() }
        view.findViewById<Button>(R.id.btnDelete).setOnClickListener { onDelete() }

        return view
    }
}
