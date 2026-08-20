package com.workbuddy.notes

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.PopupMenu
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * 主容器：顶部栏（标题 + 搜索 + ☰菜单）+ 底部三个 Tab（四象限 / 点子 / 未想清）。
 * 三个 Fragment 常驻，靠 show/hide 切换，避免重复创建导致数据错位。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView
    private lateinit var quad: QuadFragment
    private lateinit var idea: ListFragment
    private lateinit var und: ListFragment
    private lateinit var searchBox: EditText
    private var activeTag = "quad"

    override fun onCreate(savedInstanceState: Bundle?) {
        // 深色模式：必须在 setContentView 前设定
        AppCompatDelegate.setDefaultNightMode(
            if (AppSettings.isDark(this)) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
        super.onCreate(savedInstanceState)

        // 初始化共享数据层：三个 Fragment 读写同一份列表，杜绝互相覆盖
        NotesStore.init(this)
        // 启动时顺手清理过期回收站项目
        purgeExpiredTrash()

        setContentView(R.layout.activity_main)
        bottomNav = findViewById(R.id.bottomNav)
        searchBox = findViewById(R.id.searchBox)

        val fm = supportFragmentManager
        quad = (fm.findFragmentByTag("quad") as? QuadFragment) ?: QuadFragment()
        idea = (fm.findFragmentByTag("idea") as? ListFragment)
            ?: ListFragment.newInstance(Module.IDEA)
        und = (fm.findFragmentByTag("und") as? ListFragment)
            ?: ListFragment.newInstance(Module.UNDECIDED)

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

        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                setActiveSearch(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        findViewById<Button>(R.id.menuBtn).setOnClickListener { showMenu(it) }
    }

    private fun showOnly(tag: String): Boolean {
        activeTag = tag
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
        // 切换 Tab 时清空搜索，避免误过滤
        if (searchBox.text.isNotEmpty()) {
            searchBox.setText("")
        } else {
            setActiveSearch("")
        }
        return true
    }

    private fun activeFragment(): Fragment? = when (activeTag) {
        "quad" -> quad
        "idea" -> idea
        "und" -> und
        else -> quad
    }

    /** 把搜索词分发到当前 Tab 对应的 Fragment（Fragment 基类没有 setSearch）。 */
    private fun setActiveSearch(q: String) {
        when (activeTag) {
            "quad" -> quad.setSearch(q)
            "idea" -> idea.setSearch(q)
            "und" -> und.setSearch(q)
        }
    }

    private fun showMenu(anchor: android.view.View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, "🗑 回收站")
        popup.menu.add(0, 2, 0, "📤 导出备份")
        popup.menu.add(0, 3, 0, if (AppSettings.isDark(this)) "☀ 日间模式" else "🌙 夜间模式")
        popup.menu.add(0, 4, 0, "🔒 隐私锁")
        popup.menu.add(0, 5, 0, "ℹ 关于")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> startActivity(Intent(this, RecycleBinActivity::class.java))
                2 -> showExportChoice()
                3 -> toggleDark()
                4 -> showPrivacyLock()
                5 -> showAbout()
            }
            true
        }
        popup.show()
    }

    private fun showExportChoice() {
        AlertDialog.Builder(this)
            .setTitle("导出备份")
            .setItems(arrayOf("导出为 PDF", "导出为 Word (DOCX)")) { _, which ->
                val notes = NotesStore.all().filter { !it.deleted }
                val file = if (which == 0) Export.exportPdf(this, notes)
                else Export.exportDocx(this, notes)
                val mime = if (which == 0) "application/pdf"
                else "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                Export.shareFile(this, file, mime)
            }
            .show()
    }

    private fun toggleDark() {
        val on = !AppSettings.isDark(this)
        AppSettings.setDark(this, on)
        AppCompatDelegate.setDefaultNightMode(
            if (on) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    private fun showPrivacyLock() {
        if (AppSettings.isLockOn(this)) {
            AlertDialog.Builder(this)
                .setTitle("隐私锁已开启")
                .setItems(arrayOf("关闭隐私锁", "修改密码")) { _, which ->
                    if (which == 0) PinDialog.turnOff(this) { AppLock.unlocked = true }
                    else PinDialog.setup(this) {}
                }
                .show()
        } else {
            PinDialog.setup(this) { }
        }
    }

    private fun showAbout() {
        AlertDialog.Builder(this)
            .setTitle("关于 · 札记")
            .setMessage("一款治愈系便签：四象限归纳 + 点子/未想清，支持图片、语音、标签、加密、回收站与导出（PDF / Word）。\n\n数据仅存于本机，卸载即清空。")
            .setPositiveButton("知道了", null)
            .show()
    }

    /** 任一模块改动后，刷新全部三个 Fragment 的视图并刷新小部件。 */
    fun refreshAll() {
        quad.refresh()
        idea.refresh()
        und.refresh()
        updateWidget()
    }

    fun updateWidget() {
        NotesWidgetProvider.updateAll(this)
    }

    private fun purgeExpiredTrash() {
        val cut = System.currentTimeMillis() - Note.TRASH_DAYS * 24L * 60 * 60 * 1000
        val all = NotesStore.all()
        val expired = all.filter { it.deleted && it.deletedAt < cut }
        if (expired.isNotEmpty()) {
            expired.forEach { note ->
                Media.deleteFile(note.imagePath)
                Media.deleteFile(note.audioPath)
                Media.deleteFile(note.drawingPath)
            }
            all.removeAll(expired)
            NotesStore.save()
        }
    }

    // ---------- 应用锁 ----------
    override fun onResume() {
        super.onResume()
        if (AppLock.onResume() && AppSettings.isLockOn(this) && !AppLock.unlocked) {
            showAppLock()
        }
        refreshAll()
    }

    override fun onPause() {
        super.onPause()
        AppLock.onPause()
    }

    private fun showAppLock() {
        val dp = resources.displayMetrics.density
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * dp).toInt(), (8 * dp).toInt(), (24 * dp).toInt(), 0)
        }
        val et = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "请输入密码"
        }
        val fp = Button(this).apply { text = "使用指纹解锁" }
        layout.addView(et)
        layout.addView(fp)

        val dialog = AlertDialog.Builder(this)
            .setTitle("🔒 已锁定")
            .setView(layout)
            .setCancelable(false)
            .setPositiveButton("解锁") { _, _ -> tryUnlock(et.text.toString(), null) }
            .create()
        dialog.show()
        fp.setOnClickListener { startBiometric(dialog) }
    }

    private fun tryUnlock(pin: String, dialog: AlertDialog?) {
        val hash = AppSettings.getPinHash(this)
        if (hash != null && AppSettings.hashPin(pin) == hash) {
            AppLock.unlocked = true
            dialog?.dismiss()
        } else {
            AlertDialog.Builder(this)
                .setTitle("密码错误")
                .setMessage("请重试，或使用指纹。")
                .setPositiveButton("重试") { _, _ -> showAppLock() }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun startBiometric(dialog: AlertDialog) {
        val bm = BiometricManager.from(this)
        if (bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            != BiometricManager.BIOMETRIC_SUCCESS
        ) {
            AlertDialog.Builder(this)
                .setTitle("无法使用指纹")
                .setMessage("设备未录入指纹，请直接用密码解锁。")
                .setPositiveButton("知道了", null)
                .show()
            return
        }
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    AppLock.unlocked = true
                    dialog.dismiss()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {}
                override fun onAuthenticationFailed() {}
            })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("指纹解锁")
            .setNegativeButtonText("用密码")
            .build()
        try {
            prompt.authenticate(info)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
