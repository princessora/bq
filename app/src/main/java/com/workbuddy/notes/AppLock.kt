package com.workbuddy.notes

/**
 * 应用锁状态跟踪：统计处于前台的「本应用 Activity」数量。
 * 当数量从 0 变 1，说明 App 刚从后台/冷启动恢复，此时若开启了隐私锁则应要求解锁。
 * 这样在应用内部跳转（如打开涂鸦/回收站 Activity）时不会误触发重新锁屏。
 */
object AppLock {
    var unlocked = false
    private var active = 0

    /** 在 Activity.onResume 调用；返回 true 表示本次是「从后台或冷启动」恢复。 */
    fun onResume(): Boolean {
        active++
        return active == 1
    }

    /** 在 Activity.onPause 调用。 */
    fun onPause() {
        if (active > 0) active--
    }
}
