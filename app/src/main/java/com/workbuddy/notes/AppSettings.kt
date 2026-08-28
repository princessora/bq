package com.workbuddy.notes

import android.content.Context
import android.content.SharedPreferences

/**
 * 全局偏好设置：深色模式、隐私锁开关与 PIN 哈希。
 * 仅本地存储，用于防窥屏，并非高安全强度。
 */
object AppSettings {
    private const val NAME = "zaji_prefs"
    private const val KEY_DARK = "dark_mode"
    private const val KEY_LOCK_ON = "lock_enabled"
    private const val KEY_PIN = "lock_pin_hash"
    private const val KEY_GUIDE_SHOWN = "guide_shown"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun isDark(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_DARK, false)
    fun setDark(ctx: Context, on: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_DARK, on).apply()

    fun isLockOn(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_LOCK_ON, false)
    fun setLockOn(ctx: Context, on: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_LOCK_ON, on).apply()

    fun getPinHash(ctx: Context): String? {
        val h = prefs(ctx).getString(KEY_PIN, null)
        return if (h.isNullOrBlank()) null else h
    }
    fun setPinHash(ctx: Context, hash: String) =
        prefs(ctx).edit().putString(KEY_PIN, hash).apply()

    /** 对 PIN 做加盐哈希（本地防明文存储） */
    fun hashPin(pin: String): String {
        val salted = "zaji_${pin}_2026"
        return Integer.toHexString(salted.hashCode())
    }

    /** 新手引导是否已经向用户展示过（首次启动自动弹一次） */
    fun isGuideShown(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_GUIDE_SHOWN, false)

    fun markGuideShown(ctx: Context) {
        prefs(ctx).edit().putBoolean(KEY_GUIDE_SHOWN, true).apply()
    }
}
