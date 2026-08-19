package com.workbuddy.notes

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog

/** 通用 UI 构造：编辑便签的弹窗（文本 + 颜色选择）。 */
object Ui {

    fun showEditor(
        context: Context,
        title: String,
        initialText: String,
        initialColor: String,
        onOk: (String, String) -> Unit
    ) {
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
        layout.addView(label)
        layout.addView(colorRow)

        AlertDialog.Builder(context)
            .setTitle(title)
            .setView(layout)
            .setPositiveButton("保存") { _, _ ->
                onOk(et.text.toString().trim(), chosen)
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
