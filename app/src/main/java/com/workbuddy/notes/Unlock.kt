package com.workbuddy.notes

import android.content.Context
import android.text.InputType
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * 单条便签解锁：默认指纹，可选密码。
 * 设备已录入生物识别 → 优先弹指纹；点「用密码」或设备无指纹 → 回退密码输入。
 * 密码与「隐私锁」共用同一个 PIN（AppSettings）。
 */
object Unlock {

    /** 校验解锁。验证成功回调 [onSuccess]。 */
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
        val activity = context as? FragmentActivity
        val canBiometric = activity != null &&
            BiometricManager.from(context)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
        if (canBiometric) {
            biometric(activity, hash, onSuccess)
        } else {
            promptPin(context, hash, onSuccess)
        }
    }

    /** 指纹优先：认证成功直接通过；点「用密码」回退到密码输入。 */
    private fun biometric(activity: FragmentActivity, hash: String, onSuccess: () -> Unit) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // 用户点了「用密码」或取消 → 回退密码输入
                    if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        promptPin(activity, hash, onSuccess)
                    }
                }

                override fun onAuthenticationFailed() {}
            })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("指纹解锁便签")
            .setSubtitle("使用已录入的指纹验证")
            .setNegativeButtonText("用密码")
            .build()
        try {
            prompt.authenticate(info)
        } catch (e: Exception) {
            // 极少数机型弹窗异常 → 直接回退密码
            promptPin(activity, hash, onSuccess)
        }
    }

    /** 纯密码输入框（指纹不可用 / 用户选择用密码时的回退）。 */
    private fun promptPin(context: Context, hash: String, onSuccess: () -> Unit) {
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
                        .setPositiveButton("重试") { _, _ -> promptPin(context, hash, onSuccess) }
                        .setNegativeButton("取消", null)
                        .show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
