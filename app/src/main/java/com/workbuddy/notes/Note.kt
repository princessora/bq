package com.workbuddy.notes

import java.util.UUID

enum class Module { QUAD, IDEA, UNDECIDED }

/**
 * 一条便签。借鉴 ANotes 的「便签 + 分类」思路：
 * 用 [module] 区分三大收纳区，用 [quadZone] 在四象限内定位。
 */
data class Note(
    val id: String = UUID.randomUUID().toString(),
    var text: String = "",
    var colorHex: String = "#FFFFFF",
    val createdAt: Long = System.currentTimeMillis(),
    var module: Module = Module.QUAD,
    var quadZone: Int = 1
) {
    companion object {
        /** 四象限标签，借鉴 Einsen 的 Eisenhower 矩阵 */
        val QUAD_ZONES = listOf(
            "① 重要 · 紧急",
            "② 重要 · 不紧急",
            "③ 不重要 · 紧急",
            "④ 不重要 · 不紧急"
        )
        val MODULE_TITLE = mapOf(
            Module.QUAD to "四象限归纳",
            Module.IDEA to "点子存放处",
            Module.UNDECIDED to "未想清楚的事"
        )
    }
}
