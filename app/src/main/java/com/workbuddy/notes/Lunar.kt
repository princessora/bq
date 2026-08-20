package com.workbuddy.notes

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

/**
 * 农历（Chinese lunar）转换工具。
 *
 * 纯 JDK 实现，基于经典的 1900–2100 农历信息整数表：
 * 每个 int 的低 4 位表示闰月位置，bit5..16 表示 12 个月大小，
 * bit16 表示闰月为 30 天。与项目根目录 lunar_test.js 中的算法一致（已用多个日期验证）。
 * 无第三方依赖。
 */
object Lunar {

    // 1900..2100 每年一份农历信息（与 lunar_test.js 的 LUNAR_INFO 一致）。
    @Suppress("MagicNumber")
    private val LUNAR_INFO = intArrayOf(
        0x04bd8, 0x04ae0, 0x0a570, 0x054d5, 0x0d260, 0x0d950, 0x16554, 0x056a0, 0x09ad0, 0x055d2,
        0x04ae0, 0x0a5b6, 0x0a4d0, 0x0d250, 0x1d255, 0x0b540, 0x0d6a0, 0x0ada2, 0x095b0, 0x14977,
        0x04970, 0x0a4b0, 0x0b4b5, 0x06a50, 0x06d40, 0x1ab54, 0x02b60, 0x09570, 0x052f2, 0x04970,
        0x06566, 0x0d4a0, 0x0ea50, 0x06e95, 0x05ad0, 0x02b60, 0x186e3, 0x092e0, 0x1c8d7, 0x0c950,
        0x0d4a0, 0x1d8a6, 0x0b550, 0x056a0, 0x1a5b4, 0x025d0, 0x092d0, 0x0d2b2, 0x0a950, 0x0b557,
        0x06ca0, 0x0b550, 0x15355, 0x04da0, 0x0a5b0, 0x14573, 0x052b0, 0x0a9a8, 0x0e950, 0x06aa0,
        0x0aea6, 0x0ab50, 0x04b60, 0x0aae4, 0x0a570, 0x05260, 0x0f263, 0x0d950, 0x05b57, 0x056a0,
        0x096d0, 0x04dd5, 0x04ad0, 0x0a4d0, 0x0d4d4, 0x0d250, 0x0d558, 0x0b540, 0x0b6a0, 0x195a6,
        0x095b0, 0x049b0, 0x0a974, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40, 0x0af46, 0x0ab60, 0x09570,
        0x04af5, 0x04970, 0x064b0, 0x074a3, 0x0ea50, 0x06b58, 0x055c0, 0x0ab60, 0x096d5, 0x092e0,
        0x0c960, 0x0d954, 0x0d4a0, 0x0da50, 0x07552, 0x056a0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5,
        0x0a950, 0x0b4a0, 0x0baa4, 0x0ad50, 0x055d9, 0x04ba0, 0x0a5b0, 0x15176, 0x052b0, 0x0a930,
        0x07954, 0x06aa0, 0x0ad50, 0x05b52, 0x04b60, 0x0a6e6, 0x0a4e0, 0x0d260, 0x0ea65, 0x0d530,
        0x05aa0, 0x076a3, 0x096d0, 0x04bd7, 0x04ad0, 0x0a4d0, 0x1d0b6, 0x0d250, 0x0d520, 0x0dd45,
        0x0b5a0, 0x056d0, 0x055b2, 0x049b0, 0x0a577, 0x0a4b0, 0x0aa50, 0x1b255, 0x06d20, 0x0ada0,
        0x14b63, 0x09370, 0x049f8, 0x04970, 0x064b0, 0x168a6, 0x0ea50, 0x06b20, 0x1a6c4, 0x0aae0,
        0x0a2e0, 0x0d2e3, 0x0c960, 0x0d557, 0x0d4a0, 0x0da50, 0x05d55, 0x056a0, 0x0a6d0, 0x055d4,
        0x052d0, 0x0a9b8, 0x0a950, 0x0b4a0, 0x0b6a6, 0x0ad50, 0x055a0, 0x0aba4, 0x0a5b0, 0x052b0,
        0x0b273, 0x06930, 0x07337, 0x06aa0, 0x0ad50, 0x14b55, 0x04b60, 0x0a570, 0x054e4, 0x0d160,
        0x0e968, 0x0d520, 0x0daa0, 0x16aa6, 0x056d0, 0x04ae0, 0x0a9d4, 0x0a2d0, 0x0d150, 0x0f252,
        0x0d520
    )

    private val DAYS = arrayOf(
        "初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
        "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
        "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十"
    )

    private val MONTHS = arrayOf(
        "正月", "二月", "三月", "四月", "五月", "六月",
        "七月", "八月", "九月", "十月", "冬月", "腊月"
    )

    private val ZODIAC = arrayOf(
        "鼠", "牛", "虎", "兔", "龙", "蛇",
        "马", "羊", "猴", "鸡", "狗", "猪"
    )

    private const val BASE_YEAR = 1900
    private const val DAY_MS = 86400000L

    /** 某农历年的闰月（1..12），无闰月返回 0。 */
    private fun leapMonth(year: Int): Int = LUNAR_INFO[year - BASE_YEAR] and 0xf

    /** 闰月天数（无闰月为 0）。 */
    private fun leapDays(year: Int): Int {
        val lm = leapMonth(year)
        return if (lm == 0) {
            0
        } else {
            if (LUNAR_INFO[year - BASE_YEAR] and 0x10000 != 0) 30 else 29
        }
    }

    /** 非闰月 [1..12] 的天数。 */
    private fun monthDays(year: Int, month: Int): Int {
        return if (LUNAR_INFO[year - BASE_YEAR] and (0x10000 ushr month) != 0) 30 else 29
    }

    /** 某农历年的总天数。 */
    private fun yearDays(year: Int): Int {
        var sum = 348
        var i = 0x8000
        while (i > 0x8) {
            if (LUNAR_INFO[year - BASE_YEAR] and i != 0) sum++
            i = i ushr 1
        }
        return sum + leapDays(year)
    }

    /** 完整农历日期，如 "农历 八月十五"。 */
    fun solarToLunar(cal: Calendar): String = "农历 " + lunarMonthDay(cal)

    /** 公历年份对应的生肖。 */
    fun zodiac(year: Int): String {
        val idx = ((year - 4) % 12 + 12) % 12
        return ZODIAC[idx]
    }

    /** 只取月日部分，如 "八月十五" 或 "闰二月十八"。 */
    fun lunarMonthDay(cal: Calendar): String {
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)

        // 基准日：1900-01-31（农历 1900 年正月初一），用 UTC 避免夏令时影响。
        val base = GregorianCalendar(TimeZone.getTimeZone("GMT"))
        base.set(1900, Calendar.JANUARY, 31, 0, 0, 0)
        base.set(Calendar.MILLISECOND, 0)

        val target = GregorianCalendar(TimeZone.getTimeZone("GMT"))
        target.set(y, m - 1, d, 0, 0, 0)
        target.set(Calendar.MILLISECOND, 0)

        var offset = ((target.timeInMillis - base.timeInMillis) / DAY_MS).toInt()

        var year = BASE_YEAR
        var temp = 0
        while (year < 2101 && offset > 0) {
            temp = yearDays(year)
            offset -= temp
            year++
        }
        if (offset < 0) {
            offset += temp
            year--
        }

        val leap = leapMonth(year)
        var isLeap = false
        var month = 1
        var j = 1
        while (j <= 12 && offset > 0) {
            if (leap > 0 && j == leap + 1 && !isLeap) {
                j--
                isLeap = true
                temp = leapDays(year)
            } else {
                temp = monthDays(year, j)
            }
            if (isLeap && j == leap + 1) {
                isLeap = false
            }
            offset -= temp
            j++
        }
        if (offset == 0 && leap > 0 && j == leap + 1) {
            if (isLeap) {
                isLeap = false
            } else {
                isLeap = true
                j--
            }
        }
        if (offset < 0) {
            offset += temp
            j--
        }

        month = j
        val day = offset + 1

        val monthStr = (if (isLeap) "闰" else "") + MONTHS[month - 1]
        val dayStr = DAYS[day - 1]
        return monthStr + dayStr
    }
}
