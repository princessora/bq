package com.workbuddy.notes

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * 新手引导全屏 Activity。
 *  - 首页首次启动由 MainActivity 触发：onCreate 末尾检测 !AppSettings.isGuideShown() → startActivity
 *  - 用户也可通过 ☰ 菜单的「💡 新手引导」随时重看
 *  - 翻页：底部 上一步 / 下一步 / 完成；右上角跳过直接 finish
 */
class GuideActivity : AppCompatActivity() {

    private var idx: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_guide)

        val tvEmoji = findViewById<TextView>(R.id.guideEmoji)
        val tvTitle = findViewById<TextView>(R.id.guideTitle)
        val tvBody = findViewById<TextView>(R.id.guideBody)
        val btnSkip = findViewById<Button>(R.id.guideSkip)
        val btnPrev = findViewById<Button>(R.id.guidePrev)
        val btnNext = findViewById<Button>(R.id.guideNext)
        val dots = findViewById<LinearLayout>(R.id.guideDots)

        val pages = GuidePages.PAGES
        renderDots(dots, pages.size)

        fun render() {
            val p = pages[idx]
            tvEmoji.text = p.emoji
            tvTitle.text = p.title
            tvBody.text = p.body
            btnPrev.visibility = if (idx == 0) View.INVISIBLE else View.VISIBLE
            btnNext.text = if (idx == pages.size - 1) "开始使用 ✓" else "下一步 ›"
            // 更新圆点高亮（直接重画整个 LinearLayout，简单且 N<=8）
            for (i in 0 until dots.childCount) {
                val d = dots.getChildAt(i) as TextView
                d.text = if (i == idx) "●" else "○"
                d.setTextColor(if (i == idx) 0xFF00897B.toInt() else 0xFFB0BEC5.toInt())
                d.textSize = if (i == idx) 14f else 12f
            }
        }

        btnSkip.setOnClickListener { finish() }
        btnPrev.setOnClickListener {
            if (idx > 0) {
                idx -= 1
                render()
            }
        }
        btnNext.setOnClickListener {
            if (idx == pages.size - 1) {
                finish()
            } else {
                idx += 1
                render()
            }
        }
        render()
    }

    /**
     * 在 [LinearLayout] 里动态放 N 个圆点指示符（render() 里再更新内容）。
     * 这里只占位置、设初始宽度，避免反复新建 View。
     */
    private fun renderDots(dots: LinearLayout, n: Int) {
        dots.removeAllViews()
        val dp = resources.displayMetrics.density
        val pad = (4 * dp).toInt()
        repeat(n) { i ->
            val tv = TextView(this)
            tv.text = if (i == 0) "●" else "○"
            tv.textSize = if (i == 0) 14f else 12f
            tv.setTextColor(if (i == 0) 0xFF00897B.toInt() else 0xFFB0BEC5.toInt())
            tv.setPadding(pad, 0, pad, 0)
            dots.addView(tv)
        }
    }
}
