package com.workbuddy.notes

import android.content.Context
import android.text.InputType
import android.widget.EditText
import androidx.appcompat.app.AlertDialog

/**
 * 密码（PIN）对话框：用于「单条便签加密」的解锁，以及「隐私锁」的设置。
 * 全局应用锁的指纹+PIN 在 MainActivity 中实现；这里提供可复用的 PIN 输入与设置。
 */
object PinDialog {

    /** 校验 PIN；若尚未设置密码，提示先去菜单设置。验证成功回调 [onSuccess]。 */
    fun verify(context: Context, onSuccess: () -> Unit) {
        val hash = AppSettings.getPinHash(context)
        if (hash == null) {
            AlertDialog.Builder(context)
                .setTitle("未设置隐私密码")
                .setMessage("请先在「☰ 菜单 → 隐私锁」中设置密码，才能解锁加密便签。")
                .setPositiveButton("知道了", null)
                .show()
            return
        }
        prompt(context, hash, onSuccess)
    }

    private fun prompt(context: Context, hash: String, onSuccess: () -> Unit) {
        val et = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "请输入密码"
        }
        AlertDialog.Builder(context)
            .setTitle("输入密码")
            .setView(et)
            .setPositiveButton("确定") { _, _ ->
                if (AppSettings.hashPin(et.text.toString()) == hash) {
                    onSuccess()
                } else {
                    AlertDialog.Builder(context)
                        .setTitle("密码错误")
                        .setMessage("请重试。")
                        .setPositiveButton("重试") { _, _ -> prompt(context, hash, onSuccess) }
                        .setNegativeButton("取消", null)
                        .show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 设置 / 修改密码（输入两次，自动开启隐私锁）。 */
    fun setup(context: Context, onDone: () -> Unit) {
        val et = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "设置 4-8 位密码"
        }
        AlertDialog.Builder(context)
            .setTitle("设置隐私密码")
            .setView(et)
            .setPositiveButton("下一步") { _, _ ->
                val p1 = et.text.toString()
                if (p1.length < 4) {
                    AlertDialog.Builder(context)
                        .setTitle("密码太短")
                        .setMessage("至少需要 4 位。")
                        .setPositiveButton("重新设置") { _, _ -> setup(context, onDone) }
                        .show()
                } else {
                    confirm(context, p1, onDone)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun confirm(context: Context, p1: String, onDone: () -> Unit) {
        val et2 = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "再次输入确认"
        }
        AlertDialog.Builder(context)
            .setTitle("确认密码")
            .setView(et2)
            .setPositiveButton("完成") { _, _ ->
                if (et2.text.toString() == p1) {
                    AppSettings.setPinHash(context, AppSettings.hashPin(p1))
                    AppSettings.setLockOn(context, true)
                    onDone()
                } else {
                    AlertDialog.Builder(context)
                        .setTitle("两次不一致")
                        .setMessage("请重新设置。")
                        .setPositiveButton("重新设置") { _, _ -> setup(context, onDone) }
                        .show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 关闭隐私锁（需先验证） */
    fun turnOff(context: Context, onDone: () -> Unit) {
        verify(context) {
            AppSettings.setLockOn(context, false)
            onDone()
        }
    }
}
