package com.workbuddy.notes

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.min

/**
 * 支出分类饼图（自绘 Canvas，不引第三方库）。
 * 左侧画饼，右侧画图例（分类 / 金额 / 占比）。无数据时居中提示。
 */
class PieChartView(
    context: Context,
    private val entries: List<PieEntry>
) : View(context) {

    data class PieEntry(val label: String, val value: Long, val color: Int)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val dp = resources.displayMetrics.density
        val total = entries.sumOf { it.value }

        if (entries.isEmpty() || total <= 0) {
            paint.color = 0xFF9E9E9E.toInt()
            paint.textSize = 14f * dp
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("本月暂无支出分类", w / 2f, h / 2f, paint)
            return
        }

        val radius = min(w * 0.32f, h * 0.40f)
        val cx = w * 0.26f
        val cy = h / 2f
        val rect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)

        var start = -90f
        entries.forEach {
            val sweep = it.value.toFloat() / total.toFloat() * 360f
            paint.color = it.color
            canvas.drawArc(rect, start, sweep, true, paint)
            start += sweep
        }

        // 图例
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 12f * dp
        val lineH = 18f * dp
        var ly = cy - (entries.size * lineH) / 2f + lineH / 2f
        val lx = cx + radius + 12f * dp
        entries.forEach {
            paint.color = it.color
            canvas.drawRect(lx, ly - 8f * dp, lx + 8f * dp, ly, paint)
            paint.color = 0xFF424242.toInt()
            val pct = (it.value.toFloat() / total.toFloat() * 100).toInt()
            val yuan = String.format(java.util.Locale.US, "%.0f", it.value / 100.0)
            canvas.drawText("${it.label} ¥$yuan $pct%", lx + 14f * dp, ly, paint)
            ly += lineH
        }
    }
}
