package com.workbuddy.notes

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * 近 6 月收支趋势柱状图（自绘 Canvas，不引第三方库）。
 * 每个月两根柱子：收入（红）/ 支出（绿），底部标注月份。
 */
class TrendChartView(
    context: Context,
    private val entries: List<TrendEntry>
) : View(context) {

    data class TrendEntry(val label: String, val income: Long, val expense: Long)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val dp = resources.displayMetrics.density

        if (entries.isEmpty()) {
            paint.color = 0xFF9E9E9E.toInt()
            paint.textSize = 14f * dp
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("暂无数据", w / 2f, h / 2f, paint)
            return
        }

        val maxVal = entries.maxOfOrNull { maxOf(it.income, it.expense) } ?: 0L
        val topPad = 16f * dp
        val bottomPad = 22f * dp
        val drawH = h - topPad - bottomPad

        // 基线
        paint.color = 0xFFE0E0E0.toInt()
        paint.strokeWidth = 1f * dp
        canvas.drawLine(0f, h - bottomPad, w, h - bottomPad, paint)

        if (maxVal <= 0) {
            paint.color = 0xFF9E9E9E.toInt()
            paint.textSize = 13f * dp
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("近 6 月无收支", w / 2f, h / 2f, paint)
            return
        }

        val n = entries.size
        val slotW = w / n
        val barW = min(slotW * 0.28f, 26f * dp)
        entries.forEachIndexed { i, e ->
            val cx = slotW * i + slotW / 2f
            val incH = e.income.toFloat() / maxVal.toFloat() * drawH
            val expH = e.expense.toFloat() / maxVal.toFloat() * drawH
            // 收入（红）
            paint.color = 0xFFC62828.toInt()
            canvas.drawRect(cx - barW - 1f * dp, h - bottomPad - incH, cx - 1f * dp, h - bottomPad, paint)
            // 支出（绿）
            paint.color = 0xFF2E7D32.toInt()
            canvas.drawRect(cx + 1f * dp, h - bottomPad - expH, cx + 1f * dp + barW, h - bottomPad, paint)
            // 月份标签
            paint.color = 0xFF757575.toInt()
            paint.textSize = 11f * dp
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(e.label, cx, h - 4f * dp, paint)
        }
    }
}
