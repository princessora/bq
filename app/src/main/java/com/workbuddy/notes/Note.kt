package com.workbuddy.notes

import java.util.UUID

enum class Module { QUAD, IDEA, UNDECIDED, MUMBLE }

/**
 * 一条便签。借鉴 ANotes 的「便签 + 分类」思路：
 * 用 [module] 区分三大收纳区，用 [quadZone] 在四象限内定位。
 *
 * 注意：Gson 反序列化时不会调用 Kotlin 构造函数，因此带非空默认值的
 * 引用类型字段（“”、List）在旧数据里会是 JVM 默认值（null）。
 * 所以 tags / eventLabel 等一律声明为可空，取值时做空安全处理。
 */
data class Note(
    val id: String = UUID.randomUUID().toString(),
    var text: String = "",
    var colorHex: String = "#FFFFFF",
    val createdAt: Long = System.currentTimeMillis(),
    var module: Module = Module.QUAD,
    var quadZone: Int = 1,
    /** 配图绝对路径（app 私有目录），null 表示无图 */
    var imagePath: String? = null,
    /** 语音文件绝对路径（app 私有目录），null 表示无语音 */
    var audioPath: String? = null,
    /** 语音时长（毫秒），仅在 audioPath 非空时有效 */
    var audioDurationMs: Long = 0L,
    // ---- ① 回收站：软删除 ----
    /** 是否已移入回收站（软删除） */
    var deleted: Boolean = false,
    /** 移入回收站的时间（epoch 毫秒），用于 7 天自动清理 */
    var deletedAt: Long = 0L,
    // ---- ⑤ 置顶 / 收藏 ----
    var pinned: Boolean = false,
    var favorite: Boolean = false,
    // ---- 「点子/未想清/碎碎念」打勾完成，排序让 done 的便签后移 ----
    var done: Boolean = false,
    // ---- ④ 标签（逗号分隔的文本，Gson 反序列化后可能为 null） ----
    var tags: String? = null,
    // ---- ⑪ 纪念日 / 生日（epoch 毫秒，null=无） ----
    var eventDate: Long? = null,
    var eventLabel: String? = null,
    /** 日期类型：null / "单次" 视为只一次；"周期" 配合 [eventRepeat]；"按间隔" 配合 [eventIntervalDays] */
    var eventKind: String? = null,
    /** 周期模式下具体循环规则："每天" / "每周" / "每月" / "每年" */
    var eventRepeat: String? = null,
    /** 按间隔模式下循环间隔（单位：天）。例如 3 = 每 3 天一次 */
    var eventIntervalDays: Int? = null,
    /** 提醒时间（"HH:mm"），null=不限定时间。卡片会显示 ⏰ HH:mm */
    var eventTime: String? = null,
    // ---- ⑫ 地理位置 ----
    var latitude: Double? = null,
    var longitude: Double? = null,
    var locationName: String? = null,
    // ---- ⑩ 涂鸦（PNG 绝对路径） ----
    var drawingPath: String? = null,
    // ---- ③ 单条便签加密 ----
    var locked: Boolean = false
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
            Module.UNDECIDED to "未想清楚的事",
            Module.MUMBLE to "碎碎念"
        )
        /** 回收站保留天数 */
        const val TRASH_DAYS = 7
        /** 日期类型：单次/周期/按间隔。null 视作"单次"（兼容旧数据） */
        const val EVENT_KIND_ONCE = "单次"
        const val EVENT_KIND_CYCLE = "周期"
        const val EVENT_KIND_INTERVAL = "按间隔"
    }

    /**
     * 卡片 / 编辑器统一显示用的日期文案。
     * - 周期：📅 每天 ⏰ 19:04
     * - 按间隔：📅 每 3 天 ⏰ 19:04
     * - 单次：📅 纪念日 · 还有 5 天 ⏰ 19:04
     * eventDate 为空时返回空串。
     */
    fun formatEventLine(): String {
        if (eventDate == null) return ""
        val timeSuffix = eventTime?.let { " ⏰ $it" } ?: ""
        return when (eventKind ?: EVENT_KIND_ONCE) {
            EVENT_KIND_CYCLE -> "📅 ${eventRepeat ?: "每天"}$timeSuffix"
            EVENT_KIND_INTERVAL -> "📅 每 ${eventIntervalDays ?: 1} 天$timeSuffix"
            else -> {
                val lbl = eventLabel ?: "纪念日"
                "📅 $lbl · ${countdownText(eventDate!!)}$timeSuffix"
            }
        }
    }

    private fun countdownText(target: Long): String {
        val now = System.currentTimeMillis()
        val dayMs = 24L * 60 * 60 * 1000
        val diff = ((target - now) / dayMs).toInt()
        return when {
            diff > 0 -> "还有 $diff 天"
            diff == 0 -> "就是今天"
            else -> "已过 ${-diff} 天"
        }
    }

    /** 标签列表（安全的非空取值，逗号分隔） */
    fun tagList(): List<String> =
        tags?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()

    /** 象限标题（越界兜底到第一象限，防止导入的坏数据导致数组越界崩溃） */
    fun quadTitle(): String =
        QUAD_ZONES.getOrElse(quadZone - 1) { QUAD_ZONES.first() }

    /** 是否含图（配图或涂鸦） */
    fun hasAnyImage(): Boolean =
        (!imagePath.isNullOrBlank()) || (!drawingPath.isNullOrBlank())
}
