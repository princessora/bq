package com.workbuddy.notes

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.InputType
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

/**
 * 密码本新增/编辑弹窗。
 *  - existing == null：新增模式，提交后 [onSaved] 拿到新建的 [PasswordEntry]
 *  - existing != null：编辑模式，预填字段，提交后 [onSaved] 拿到更新后的 [PasswordEntry]
 *  - onCancelled：无操作（用户取消时调用）
 *
 * 显隐/复制不依赖 onSaved——纯本地 Copy 自带 Toast 反馈。
 */
object PasswordEditor {

    fun show(
        context: Context,
        existing: PasswordEntry?,
        onSaved: (PasswordEntry) -> Unit,
        onCancelled: () -> Unit = {}
    ) {
        val v = LayoutInflater.from(context).inflate(R.layout.dialog_password_editor, null)
        val etTitle = v.findViewById<EditText>(R.id.dlgPwdTitle)
        val etUser = v.findViewById<EditText>(R.id.dlgPwdUsername)
        val etPwd = v.findViewById<EditText>(R.id.dlgPwdPassword)
        val etNote = v.findViewById<EditText>(R.id.dlgPwdNote)
        val spCat = v.findViewById<Spinner>(R.id.dlgPwdCategory)
        val btnToggle = v.findViewById<Button>(R.id.dlgPwdTogglePwd)

        // 分类下拉
        val cats = PasswordEntry.CATEGORIES
        spCat.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, cats)

        // 预填
        if (existing != null) {
            etTitle.setText(existing.title)
            etUser.setText(existing.username)
            etPwd.setText(existing.password)
            etNote.setText(existing.note)
            spCat.setSelection(cats.indexOf(existing.category).coerceAtLeast(0))
        } else {
            spCat.setSelection(cats.indexOf("其他").coerceAtLeast(0))
        }

        // 显隐切换（掩码 ↔ 明文）
        var visible = false
        fun applyEye() {
            etPwd.inputType = if (visible)
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            else
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            etPwd.setSelection(etPwd.text?.length ?: 0)
            btnToggle.text = if (visible) "🙈" else "👁"
        }
        applyEye()
        btnToggle.setOnClickListener {
            visible = !visible
            applyEye()
        }

        val titleRes = if (existing == null) "新增密码" else "编辑密码"
        val dialog = AlertDialog.Builder(context)
            .setTitle(titleRes)
            .setView(v)
            .setPositiveButton("保存", null)   // 下面手动绑定，先校验
            .setNegativeButton("取消") { d, _ ->
                d.dismiss()
                onCancelled()
            }
            .setCancelable(true)
            .create()

        dialog.setOnShowListener {
            val btnSave = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            btnSave.setOnClickListener {
                val t = etTitle.text?.toString()?.trim().orEmpty()
                val u = etUser.text?.toString()?.trim().orEmpty()
                val p = etPwd.text?.toString().orEmpty()
                val n = etNote.text?.toString()?.trim().orEmpty()
                if (t.isEmpty()) {
                    toast(context, "标题不能为空")
                    return@setOnClickListener
                }
                if (p.isEmpty()) {
                    toast(context, "密码不能为空")
                    return@setOnClickListener
                }
                val cat = spCat.selectedItem?.toString() ?: "其他"
                val out = existing?.copy(
                    title = t,
                    username = u,
                    password = p,
                    note = n,
                    category = cat,
                    time = System.currentTimeMillis()
                ) ?: PasswordEntry(
                    title = t,
                    username = u,
                    password = p,
                    note = n,
                    category = cat
                )
                onSaved(out)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun toast(ctx: Context, msg: String) {
        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
    }
}

/**
 * 把 [text] 复制到剪贴板，并弹 Toast 反馈。
 * 仅本 App 进程私有敏感场景用（密码本）。
 */
fun copyToClipboard(ctx: Context, label: String, text: String, feedback: String = "已复制") {
    if (text.isEmpty()) {
        Toast.makeText(ctx, "无内容", Toast.LENGTH_SHORT).show()
        return
    }
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    if (cm == null) {
        Toast.makeText(ctx, "剪贴板不可用", Toast.LENGTH_SHORT).show()
        return
    }
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    // 不依赖 Android 13 的系统复制预览提示（设备兼容性更稳）
    Toast.makeText(ctx, feedback, Toast.LENGTH_SHORT).show()
}
