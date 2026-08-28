package com.workbuddy.notes

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment

/**
 * 密码本页面（r49 完整版）。
 *
 *  - 顶：搜索框（标题 / 账号 / 备注 / 分类）
 *  - 中：卡片列表（点 = 编辑；长按 = 删除；账号/密码一键复制；密码显隐）
 *  - 右下：FAB 新增
 *  - 空态：居中提示
 *
 * 列表渲染沿用现有项目范式（ScrollView + LinearLayout 手动 inflate 每条 item），
 * 避免引入 RecyclerView 依赖、也匹配 ListFragment / BookkeepingFragment 的写法。
 *
 * 密码默认掩码显示，点击右侧 👁 单条切换；切换是 view-tag 局部态，切 tab / 搜索不影响。
 */
class PasswordFragment : Fragment() {

    private var query: String = ""
    private val revealedIds = mutableSetOf<String>()   // 列表里哪些 id 当前是明文
    private val dp: Float by lazy { resources.displayMetrics.density }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_password, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val etSearch = view.findViewById<EditText>(R.id.etSearch)
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString().orEmpty().trim().lowercase()
                refresh()
            }
        })

        val fab = view.findViewById<ImageButton>(R.id.fabAdd)
        fab.setOnClickListener {
            val ctx = it.context
            PasswordEditor.show(ctx, existing = null,
                onSaved = { entry ->
                    PasswordStore.add(entry)
                    Toast.makeText(ctx, "已新增", Toast.LENGTH_SHORT).show()
                    refresh()
                })
        }

        refresh()
    }

    /** 供 Activity.refreshAll / onResume 调用 */
    fun refresh() {
        val v = view ?: return
        val container = v.findViewById<LinearLayout>(R.id.containerPassword)
        val tvEmpty = v.findViewById<TextView>(R.id.tvEmpty)
        container.removeAllViews()

        val all = PasswordStore.all()
        val list = if (query.isEmpty()) all.toList() else all.filter { it.searchText().contains(query) }

        if (list.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            tvEmpty.text = if (all.isEmpty())
                "还没有保存任何账号\n点右下角 ＋ 新增"
            else
                "没有匹配「$query」的记录"
            return
        }
        tvEmpty.visibility = View.GONE

        val inflater = LayoutInflater.from(v.context)
        list.forEach { entry ->
            val row = inflater.inflate(R.layout.item_password, container, false)
            bindRow(row, entry)
            container.addView(row)
        }
    }

    private fun bindRow(row: View, entry: PasswordEntry) {
        val tvTitle = row.findViewById<TextView>(R.id.itemPwdTitle)
        val tvCat = row.findViewById<TextView>(R.id.itemPwdCategory)
        val tvUser = row.findViewById<TextView>(R.id.itemPwdUsername)
        val tvPwd = row.findViewById<TextView>(R.id.itemPwdPassword)
        val tvNote = row.findViewById<TextView>(R.id.itemPwdNote)
        val btnCopyUser = row.findViewById<ImageButton>(R.id.itemPwdCopyUser)
        val btnToggle = row.findViewById<ImageButton>(R.id.itemPwdTogglePwd)
        val btnCopyPwd = row.findViewById<ImageButton>(R.id.itemPwdCopyPwd)

        tvTitle.text = entry.title
        tvCat.text = entry.category
        tvUser.text = if (entry.username.isEmpty()) "—" else entry.username
        if (entry.note.isBlank()) {
            tvNote.visibility = View.GONE
        } else {
            tvNote.visibility = View.VISIBLE
            tvNote.text = entry.note
        }
        // 密码渲染（默认掩码；revealedIds 里的 id 才显示明文）
        fun renderPwd() {
            val revealed = revealedIds.contains(entry.id)
            tvPwd.text = if (revealed || entry.password.isEmpty())
                entry.password.ifEmpty { "—" }
            else
                "•".repeat(entry.password.length.coerceAtMost(16))
            btnToggle.setImageResource(
                if (revealed) android.R.drawable.ic_menu_close_clear_cancel
                else android.R.drawable.ic_menu_view
            )
            btnToggle.contentDescription = if (revealed) "隐藏密码" else "显示密码"
        }
        renderPwd()

        // 显隐切换
        btnToggle.setOnClickListener {
            if (revealedIds.contains(entry.id)) revealedIds.remove(entry.id)
            else revealedIds.add(entry.id)
            renderPwd()
        }
        // 复制账号
        btnCopyUser.setOnClickListener {
            val ctx = it.context
            copyToClipboard(ctx, "账号", entry.username, "已复制账号")
        }
        // 复制密码
        btnCopyPwd.setOnClickListener {
            val ctx = it.context
            copyToClipboard(ctx, "密码", entry.password, "已复制密码")
        }

        // 点卡片整体 = 编辑（but not when 点了 复制/显隐 按钮，事件冒泡已被 ImageButton 消费则不触发，下方用 setOnClickListener 在 LinearLayout 上即可）
        row.setOnClickListener {
            val ctx = it.context
            PasswordEditor.show(ctx, existing = entry,
                onSaved = { updated ->
                    PasswordStore.update(updated)
                    Toast.makeText(ctx, "已保存", Toast.LENGTH_SHORT).show()
                    // 编辑模式取消该 id 的显隐态，避免下次字段变化后误显示
                    revealedIds.remove(entry.id)
                    refresh()
                })
        }

        // 长按 = 删除（带二次确认）
        row.setOnLongClickListener {
            val ctx = it.context
            AlertDialog.Builder(ctx)
                .setTitle("删除「${entry.title}」？")
                .setMessage("删除后无法恢复（仅本机本数据，不连网）。")
                .setPositiveButton("删除") { _, _ ->
                    PasswordStore.remove(entry.id)
                    revealedIds.remove(entry.id)
                    Toast.makeText(ctx, "已删除", Toast.LENGTH_SHORT).show()
                    refresh()
                }
                .setNegativeButton("取消", null)
                .show()
            true
        }
    }
}
