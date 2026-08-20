package com.workbuddy.notes

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

/**
 * 随手画 / 涂鸦（⑩）：在白板上用手指涂鸦。
 * 顶部提供 返回 / 撤销 / 清空 / 保存；保存为 PNG 存到附件目录，
 * 通过 result extra "path" 回传给编辑弹窗。
 */
class DrawActivity : AppCompatActivity() {

    private lateinit var drawView: DrawView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        drawView = DrawView(this)

        val btnBack = Button(this).apply {
            text = "← 返回"
            setOnClickListener { finish() }
        }
        val btnUndo = Button(this).apply {
            text = "↩ 撤销"
            setOnClickListener { drawView.undo() }
        }
        val btnClear = Button(this).apply {
            text = "🗑 清空"
            setOnClickListener { drawView.clear() }
        }
        val btnSave = Button(this).apply {
            text = "✓ 保存"
            setOnClickListener { save() }
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(8, 8, 8, 8)
            addView(btnBack, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(btnUndo, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(btnClear, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(btnSave, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            addView(bar)
            addView(drawView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        AppLock.onResume()
    }

    override fun onPause() {
        AppLock.onPause()
        super.onPause()
    }

    /** 把画布渲染成 PNG 保存到附件目录，返回结果给编辑弹窗。 */
    private fun save() {
        val bmp = drawView.render()
        val file = File(Media.attachDir(this), "draw_${System.currentTimeMillis()}.png")
        try {
            FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bmp.recycle()
            setResult(RESULT_OK, Intent().putExtra("path", file.absolutePath))
            finish()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** 涂鸦画布：维护笔迹列表，支持撤销 / 清空 / 导出位图。 */
    private class DrawView(context: Context) : View(context) {

        private val strokes = mutableListOf<Pair<Path, Paint>>()
        private var current: Path? = null

        private val paint = Paint().apply {
            color = Color.parseColor("#1F2933")
            style = Paint.Style.STROKE
            strokeWidth = 6f * resources.displayMetrics.density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val x = event.x
            val y = event.y
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    current = Path().apply { moveTo(x, y) }
                    strokes.add((current!!) to paint)
                    invalidate()
                }
                MotionEvent.ACTION_MOVE -> {
                    current?.lineTo(x, y)
                    invalidate()
                }
                MotionEvent.ACTION_UP -> {
                    current?.lineTo(x, y)
                    current = null
                    invalidate()
                }
            }
            return true
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawColor(Color.WHITE)
            strokes.forEach { (p, pt) -> canvas.drawPath(p, pt) }
        }

        fun undo() {
            if (strokes.isNotEmpty()) strokes.removeAt(strokes.size - 1)
            invalidate()
        }

        fun clear() {
            strokes.clear()
            invalidate()
        }

        fun render(): Bitmap {
            val w = maxOf(width, 1)
            val h = maxOf(height, 1)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawColor(Color.WHITE)
            strokes.forEach { (p, pt) -> c.drawPath(p, pt) }
            return bmp
        }
    }
}
