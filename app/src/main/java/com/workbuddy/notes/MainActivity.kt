package com.workbuddy.notes

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.PopupMenu
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * 主容器：顶部栏（标题 + 搜索 + ☰菜单）+ 底部四个 Tab（四象限 / 点子 / 未想清 / 碎碎念）。
 * 四个 Fragment 常驻，靠 show/hide 切换，避免重复创建导致数据错位。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView
    private lateinit var quad: QuadFragment
    private lateinit var idea: ListFragment
    private lateinit var und: ListFragment
    private lateinit var mumble: ListFragment
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
        // 每日数据快照归档（保留最近 7 天，防主文件+备份同时损坏的极端情况）
        archiveDailySnapshot()

        setContentView(R.layout.activity_main)
        bottomNav = findViewById(R.id.bottomNav)
        searchBox = findViewById(R.id.searchBox)

        val fm = supportFragmentManager
        quad = (fm.findFragmentByTag("quad") as? QuadFragment) ?: QuadFragment()
        idea = (fm.findFragmentByTag("idea") as? ListFragment)
            ?: ListFragment.newInstance(Module.IDEA)
        und = (fm.findFragmentByTag("und") as? ListFragment)
            ?: ListFragment.newInstance(Module.UNDECIDED)
        mumble = (fm.findFragmentByTag("mumble") as? ListFragment)
            ?: ListFragment.newInstance(Module.MUMBLE)

        if (fm.findFragmentByTag("quad") == null) {
            fm.beginTransaction()
                .add(R.id.container, quad, "quad")
                .add(R.id.container, idea, "idea")
                .add(R.id.container, und, "und")
                .add(R.id.container, mumble, "mumble")
                .commitNow()
        }

        showOnly("quad")
        bottomNav.selectedItemId = R.id.navQuad

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navQuad -> showOnly("quad")
                R.id.navIdea -> showOnly("idea")
                R.id.navUnd -> showOnly("und")
                R.id.navMumble -> showOnly("mumble")
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
            "und" to und,
            "mumble" to mumble
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
        "mumble" -> mumble
        else -> quad
    }

    /** 把搜索词分发到当前 Tab 对应的 Fragment（Fragment 基类没有 setSearch）。 */
    private fun setActiveSearch(q: String) {
        when (activeTag) {
            "quad" -> quad.setSearch(q)
            "idea" -> idea.setSearch(q)
            "und" -> und.setSearch(q)
            "mumble" -> mumble.setSearch(q)
        }
    }

    private var favOnly = false
    /** 当前应用锁弹窗（防止密码错误重试时叠出多个全屏锁屏框） */
    private var lockDialog: AlertDialog? = null

    private fun showMenu(anchor: android.view.View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, "🗑 回收站")
        popup.menu.add(0, 2, 0, "📤 导出备份")
        popup.menu.add(0, 6, 0, "📥 导入备份")
        popup.menu.add(0, 7, 0, if (favOnly) "⭐ 取消收藏筛选" else "⭐ 只看收藏")
        popup.menu.add(0, 3, 0, if (AppSettings.isDark(this)) "☀ 日间模式" else "🌙 夜间模式")
        popup.menu.add(0, 4, 0, "🔒 隐私锁")
        popup.menu.add(0, 5, 0, "ℹ 关于")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> startActivity(Intent(this, RecycleBinActivity::class.java))
                2 -> showExportChoice()
                6 -> importLauncher.launch(
                    arrayOf(
                        "application/zip",
                        "application/x-zip-compressed",
                        "application/octet-stream"
                    )
                )
                7 -> toggleFavOnly()
                3 -> toggleDark()
                4 -> showPrivacyLock()
                5 -> showAbout()
            }
            true
        }
        popup.show()
    }

    /** ⭐ 只看收藏：四区统一切换过滤视图 */
    private fun toggleFavOnly() {
        favOnly = !favOnly
        quad.setFavOnly(favOnly)
        idea.setFavOnly(favOnly)
        und.setFavOnly(favOnly)
        mumble.setFavOnly(favOnly)
    }

    private fun showExportChoice() {
        AlertDialog.Builder(this)
            .setTitle("导出备份")
            .setItems(
                arrayOf("导出为 PDF", "导出为 Word (DOCX)", "导出完整备份包 (ZIP，含附件，可导入恢复)")
            ) { _, which ->
                val notes = NotesStore.all().filter { !it.deleted }
                when (which) {
                    0 -> {
                        val file = Export.exportPdf(this, notes)
                        Export.shareFile(this, file, "application/pdf")
                    }
                    1 -> {
                        val file = Export.exportDocx(this, notes)
                        Export.shareFile(
                            this, file,
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                        )
                    }
                    else -> {
                        val file = Export.exportBackup(this, notes)
                        Export.shareFile(this, file, "application/zip")
                    }
                }
            }
            .show()
    }

    // ---------- 导入备份 ----------
    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) promptImportMode(uri)
    }

    private fun promptImportMode(uri: Uri) {
        AlertDialog.Builder(this)
            .setTitle("导入备份")
            .setMessage("选择导入方式：")
            .setItems(arrayOf("合并到现有数据", "清空现有数据后导入")) { _, which ->
                doImport(uri, replace = which == 1)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun doImport(uri: Uri, replace: Boolean) {
        try {
            // 先解析成功再动现有数据，避免解析失败时误清空
            val imported = parseBackup(uri)
            val all = NotesStore.all()
            if (replace) {
                all.forEach { note ->
                    Media.deleteFile(note.imagePath)
                    Media.deleteFile(note.audioPath)
                    Media.deleteFile(note.drawingPath)
                }
                all.clear()
            }
            val existingIds = all.map { it.id }.toSet()
            val added = imported.filter { it.id !in existingIds }
            all.addAll(added)
            NotesStore.save()
            refreshAll()
            AlertDialog.Builder(this)
                .setTitle("导入完成")
                .setMessage("共导入 ${added.size} 条便签（含附件）。")
                .setPositiveButton("知道了", null)
                .show()
        } catch (e: Exception) {
            e.printStackTrace()
            AlertDialog.Builder(this)
                .setTitle("导入失败")
                .setMessage("备份文件无法解析，请确认选择的是本应用导出的 ZIP 备份包。")
                .setPositiveButton("知道了", null)
                .show()
        }
    }

    /** 解析 ZIP 备份：读 notes.json，把 attachments 目录下附件解压到私有目录并重写 Note 附件绝对路径。 */
    private fun parseBackup(uri: Uri): MutableList<Note> {
        val attachDir = Media.attachDir(this)
        val extracted = mutableMapOf<String, File>()
        val json = StringBuilder()
        contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        when {
                            entry.name.endsWith("notes.json") ->
                                json.append(zis.readBytes().toString(Charsets.UTF_8))
                            entry.name.contains("attachments/") -> {
                                val name = entry.name.substringAfterLast('/')
                                val dst = File(attachDir, name)
                                FileOutputStream(dst).use { zis.copyTo(it) }
                                extracted[name] = dst
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }
        val notes = NotesRepository.fromJson(json.toString())
        notes.forEach { note ->
            val map = listOf(note.imagePath, note.audioPath, note.drawingPath)
                .filter { !it.isNullOrBlank() }
                .associateWith { File(it).name }
            map.forEach { (old, name) ->
                val dst = extracted[name] ?: return@forEach
                when (old) {
                    note.imagePath -> note.imagePath = dst.absolutePath
                    note.audioPath -> note.audioPath = dst.absolutePath
                    note.drawingPath -> note.drawingPath = dst.absolutePath
                }
            }
        }
        return notes
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

    /** 任一模块改动后，刷新全部四个 Fragment 的视图、小部件，并重新校准定时提醒。 */
    fun refreshAll() {
        quad.refresh()
        idea.refresh()
        und.refresh()
        mumble.refresh()
        updateWidget()
        ReminderScheduler.scheduleAll(this)
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

    /** 每日快照：把当天的 notes.json 归档到 files/backups/，保留最近 7 份，更早的自动清理。 */
    private fun archiveDailySnapshot() {
        try {
            val dir = File(filesDir, "backups")
            dir.mkdirs()
            val today = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
                .format(java.util.Date())
            val dst = File(dir, "notes-$today.json")
            val src = File(filesDir, "notes.json")
            if (!dst.exists() && src.exists()) {
                src.copyTo(dst, overwrite = false)
            }
            dir.listFiles()?.filter { it.name.startsWith("notes-") }
                ?.sortedByDescending { it.name }
                ?.drop(7)
                ?.forEach { it.delete() }
        } catch (_: Exception) {
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
        // 离开页面/退后台时停止语音播放，避免声音残留
        AudioPlayer.stop()
        // 清理解密后的明文缓存（锁定便签附件），避免明文长期留在 cacheDir
        Crypto.cleanupDecryptedCache()
    }

    private fun showAppLock() {
        // 防止旧锁屏框未关导致叠框
        lockDialog?.dismiss()
        lockDialog = null
        val dp = resources.displayMetrics.density
        // 全屏容器：背景图 + 半透蒙层 + 居中的解锁面板
        val root = android.widget.FrameLayout(this).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundDrawable(
                android.graphics.drawable.BitmapDrawable(
                    resources,
                    android.graphics.BitmapFactory.decodeResource(resources, R.drawable.lock_bg)
                )
            )
        }
        // 半透黑蒙层，让背景图隐约可见且保证前景文字可读
        val scrim = android.view.View(this).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0x99000000.toInt())
        }
        root.addView(scrim)

        // 居中面板
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (24 * dp).toInt()
            setPadding(pad, pad, pad, pad)
            val cardBg = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = (16 * dp)
                setColor(0xCCFFFFFF.toInt())
            }
            background = cardBg
            val cardLp = android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = (24 * dp).toInt()
                rightMargin = (24 * dp).toInt()
                gravity = android.view.Gravity.CENTER
            }
            layoutParams = cardLp
        }

        val title = android.widget.TextView(this).apply {
            text = "🔒 已锁定"
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(0xFF212121.toInt())
        }
        val et = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "请输入密码"
        }
        val fp = Button(this).apply { text = "使用指纹解锁" }
        card.addView(title)
        val gap1 = android.view.View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, (12 * dp).toInt()
            )
        }
        card.addView(gap1)
        card.addView(et)
        val gap2 = android.view.View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, (8 * dp).toInt()
            )
        }
        card.addView(gap2)
        card.addView(fp)

        root.addView(card)

        val dialog = AlertDialog.Builder(this)
            .setView(root)
            .setCancelable(false)
            .setPositiveButton("解锁") { _, _ -> tryUnlock(et.text.toString(), null) }
            .create()
        dialog.show()
        lockDialog = dialog
        // 让背景图真正铺满：去掉系统 dialog 默认的边距/背景
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(0))
        dialog.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        )
        fp.setOnClickListener { startBiometric(dialog) }
    }

    private fun tryUnlock(pin: String, dialog: AlertDialog?) {
        val hash = AppSettings.getPinHash(this)
        if (hash != null && AppSettings.hashPin(pin) == hash) {
            AppLock.unlocked = true
            lockDialog?.dismiss()
            lockDialog = null
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
