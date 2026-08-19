package com.workbuddy.notes

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * 主容器：底部三个 Tab —— 四象限 / 点子 / 未想清。
 * 三个 Fragment 常驻，靠 show/hide 切换，避免重复创建导致数据错位。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView
    private lateinit var quad: QuadFragment
    private lateinit var idea: ListFragment
    private lateinit var und: ListFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bottomNav = findViewById(R.id.bottomNav)

        val fm = supportFragmentManager
        quad = (fm.findFragmentByTag("quad") as? QuadFragment) ?: QuadFragment()
        idea = (fm.findFragmentByTag("idea") as? ListFragment)
            ?: ListFragment.newInstance(Module.IDEA)
        und = (fm.findFragmentByTag("und") as? ListFragment)
            ?: ListFragment.newInstance(Module.UNDECIDED)

        // 首次启动才 add，旋转后由 FragmentManager 自动恢复，避免重复实例
        if (fm.findFragmentByTag("quad") == null) {
            fm.beginTransaction()
                .add(R.id.container, quad, "quad")
                .add(R.id.container, idea, "idea")
                .add(R.id.container, und, "und")
                .commitNow()
        }

        showOnly("quad")
        bottomNav.selectedItemId = R.id.navQuad

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navQuad -> showOnly("quad")
                R.id.navIdea -> showOnly("idea")
                R.id.navUnd -> showOnly("und")
                else -> false
            }
        }
    }

    private fun showOnly(tag: String): Boolean {
        val map: Map<String, Fragment> = mapOf(
            "quad" to quad,
            "idea" to idea,
            "und" to und
        )
        supportFragmentManager.beginTransaction().apply {
            map.forEach { (t, f) ->
                if (t == tag) show(f) else hide(f)
            }
        }.commitAllowingStateLoss()
        return true
    }

    /** 任一模块改动后，刷新全部三个 Fragment 的视图（各自重新读盘）。 */
    fun refreshAll() {
        quad.refresh()
        idea.refresh()
        und.refresh()
    }

    override fun onResume() {
        super.onResume()
        refreshAll()
    }
}
