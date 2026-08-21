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
     * 卡片 / 编辑器 / 导出统一显示用的日期文案。
     * 无论「周期 / 按间隔 / 单次」，都展示「下次提醒时间」。
     * eventDate 为空时返回空串。
     */
    fun formatEventLine(): String {
        if (eventDate == null) return ""
        val now = System.currentTimeMillis()
        val next = nextRemindAt(now)
        val kind = eventKind ?: EVENT_KIND_ONCE
        val typeText = when (kind) {
            EVENT_KIND_CYCLE -> eventRepeat ?: "每天"
            EVENT_KIND_INTERVAL -> "每 ${eventIntervalDays ?: 1} 天"
            else -> eventLabel ?: "纪念日"
        }
        val nextTxt = formatNext(next)
        // 未来且不超过一年，补充「还有 N 天」倒计时，让周期/间隔也有直观预期
        val extra = if (next != null && next > now) {
            val days = (next - now) / (24L * 60 * 60 * 1000)
            if (days in 1..365) "（还有 ${days} 天）" else ""
        } else ""
        return "📅 $typeText · 下次 $nextTxt$extra"
    }

    /**
     * 计算下一次提醒的绝对时间（epoch 毫秒）。
     * - 单次且未过期：返回该时间点；已过期返回 null（不再提醒）。
     * - 周期 / 按间隔：从起始日期起，向前推进到「第一个 >= 现在」的命中时刻。
     */
    fun nextRemindAt(now: Long): Long? {
        val date = eventDate ?: return null
        val time = eventTime ?: return null
        val parts = time.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val cal = java.util.Calendar.getInstance().apply {
            timeInMillis = date
            set(java.util.Calendar.HOUR_OF_DAY, h)
            set(java.util.Calendar.MINUTE, m)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val base = cal.timeInMillis
        return when (eventKind ?: EVENT_KIND_ONCE) {
            EVENT_KIND_ONCE -> if (base >= now) base else null
            EVENT_KIND_CYCLE -> {
                val repeat = eventRepeat ?: "每天"
                val c = java.util.Calendar.getInstance().apply { timeInMillis = base }
                while (c.timeInMillis < now) {
                    when (repeat) {
                        "每天" -> c.add(java.util.Calendar.DAY_OF_MONTH, 1)
                        "每周" -> c.add(java.util.Calendar.DAY_OF_MONTH, 7)
                        "每月" -> c.add(java.util.Calendar.MONTH, 1)
                        "每年" -> c.add(java.util.Calendar.YEAR, 1)
                        else -> c.add(java.util.Calendar.DAY_OF_MONTH, 1)
                    }
                }
                c.timeInMillis
            }
            EVENT_KIND_INTERVAL -> {
                val step = (eventIntervalDays ?: 1) * 24L * 60 * 60 * 1000
                var t = base
                while (t < now) t += step
                t
            }
        }
    }

    private fun formatNext(t: Long?): String {
        if (t == null) return "已过期"
        val c = java.util.Calendar.getInstance().apply { timeInMillis = t }
        return String.format(
            java.util.Locale.US,
            "%02d-%02d %02d:%02d",
            c.get(java.util.Calendar.MONTH) + 1,
            c.get(java.util.Calendar.DAY_OF_MONTH),
            c.get(java.util.Calendar.HOUR_OF_DAY),
            c.get(java.util.Calendar.MINUTE)
        )
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
