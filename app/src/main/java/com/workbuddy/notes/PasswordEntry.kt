package com.workbuddy.notes

import java.util.UUID

/**
 * 一条密码本记录（账号/密码）。
 *
 * 整库以加密文件形式落盘（见 PasswordRepository，调用 Crypto.encrypt 加密整个 JSON），
 * 因此本数据类持有明文即可，无需在字段级再做加密——仓库层面统一在读写边界处理。
 */
data class PasswordEntry(
    var id: String = UUID.randomUUID().toString(),
    var title: String = "",          // 网站/App 名称，如「12306」「Steam」
    var username: String = "",       // 账号/用户名
    var password: String = "",       // 密码（明文，落盘前由仓库整体加密）
    var note: String = "",           // 备注/找回邮箱/安全提示等
    var category: String = "其他",   // 分类：社交/购物/游戏/工作/金融/其他
    var time: Long = System.currentTimeMillis()
) {
    companion object {
        val CATEGORIES = listOf("社交", "购物", "游戏", "工作", "金融", "其他")
    }

    /** 列表里用于搜索匹配的字符串（标题/账号/备注/分类，统一小写）。 */
    fun searchText(): String = "$title $username $note $category".lowercase()
}
