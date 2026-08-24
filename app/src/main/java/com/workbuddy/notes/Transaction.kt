package com.workbuddy.notes

import java.util.Locale
import java.util.UUID

/**
 * 一条记账记录。
 *
 * 设计要点：
 * - 金额统一用「分」(Long) 存储，避免浮点累加误差；展示时除以 100。
 * - [source] 区分 手动录入 / 通知自动捕获 / 短信自动捕获；[auto] 标记自动捕获的「待确认」项，
 *   用户可在列表里删除误抓的条目。
 * - 全部字段可空性宽松（Gson 反序列化容错），磁盘损坏也不会整体崩。
 */
data class Transaction(
    var id: String = UUID.randomUUID().toString(),
    var amount: Long = 0,             // 金额（分，正数）
    var type: Int = TYPE_EXPENSE,     // 0 支出 / 1 收入 / 2 转账
    var category: String = "",        // 分类：餐饮/交通/购物/...
    var account: String = ACC_ALIPAY, // 账户：支付宝/微信/云闪付/现金/银行卡
    var note: String = "",            // 备注
    var time: Long = System.currentTimeMillis(),
    var source: Int = SRC_MANUAL,     // 0 手动 / 1 通知 / 2 短信
    var merchant: String = "",        // 商户/对方
    var raw: String = "",             // 原始通知/短信文本（便于核查）
    var auto: Boolean = false         // 是否自动捕获（默认待确认，可删）
) {
    companion object {
        const val TYPE_EXPENSE = 0
        const val TYPE_INCOME = 1
        const val TYPE_TRANSFER = 2

        const val SRC_MANUAL = 0
        const val SRC_NOTIFY = 1
        const val SRC_SMS = 2

        const val ACC_ALIPAY = "支付宝"
        const val ACC_WECHAT = "微信"
        const val ACC_UNIONPAY = "云闪付"
        const val ACC_CASH = "现金"
        const val ACC_BANK = "银行卡"

        val ACCOUNTS = listOf(ACC_ALIPAY, ACC_WECHAT, ACC_UNIONPAY, ACC_CASH, ACC_BANK)
        val CATEGORIES = listOf(
            "餐饮", "交通", "购物", "居家", "娱乐",
            "医疗", "教育", "工资", "红包", "其他"
        )
    }

    /** 展示用金额字符串，如 1234 -> "12.34"（固定小数点，避免本地化逗号） */
    fun amountYuan(): String = String.format(Locale.US, "%.2f", amount / 100.0)

    fun isExpense() = type == TYPE_EXPENSE
    fun isIncome() = type == TYPE_INCOME
}
