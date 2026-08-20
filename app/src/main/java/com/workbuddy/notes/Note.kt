package com.workbuddy.notes

import java.util.UUID

enum class Module { QUAD, IDEA, UNDECIDED }

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
    // ---- ④ 标签（逗号分隔的文本，Gson 反序列化后可能为 null） ----
    var tags: String? = null,
    // ---- ⑪ 纪念日 / 生日（epoch 毫秒，null=无） ----
    var eventDate: Long? = null,
    var eventLabel: String? = null,
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
            Module.UNDECIDED to "未想清楚的事"
        )
        /** 回收站保留天数 */
        const val TRASH_DAYS = 7
    }

    /** 标签列表（安全的非空取值，逗号分隔） */
    fun tagList(): List<String> =
        tags?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()

    /** 是否含图（配图或涂鸦） */
    fun hasAnyImage(): Boolean =
        (!imagePath.isNullOrBlank()) || (!drawingPath.isNullOrBlank())
}
