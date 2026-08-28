package com.workbuddy.notes

/**
 * 新手引导页面数据模型 + 全部页面定义。
 *
 * 单一数据源，便于以后再加页面或国际化时只改这里。
 */
data class GuidePage(
    val emoji: String,
    val title: String,
    val body: String,
    val isFinal: Boolean = false
)

object GuidePages {

    val PAGES: List<GuidePage> = listOf(
        GuidePage(
            emoji = "👋",
            title = "欢迎使用「札记」",
            body = "本地记事本 + 记账 + 密码本。\n所有数据仅存在你手机里，不上传任何服务器。\n（App 完全无网络权限。）"
        ),
        GuidePage(
            emoji = "📋",
            title = "四象限 · 点子 · 未想清 · 碎碎念",
            body = "底部前 4 个 tab，把想法分门别类：\n• 象限：按重要 / 紧急分四区，彩色区分\n• 点子：闪念速记（橙色标题条）\n• 未想清：暂时归不了类（紫色标题条）\n• 碎碎念：随便写写"
        ),
        GuidePage(
            emoji = "💰",
            title = "记账",
            body = "手动记账或自动捕获：\n• ＋ 号手动录一笔（金额用分，避免浮点误差）\n• 首次启动时引导里可开启通知监听，抓支付宝/微信/云闪付的支付通知自动入账\n• 支持饼图看分类占比、近 6 月趋势图"
        ),
        GuidePage(
            emoji = "🔐",
            title = "密码本",
            body = "账号密码本地保管：\n• 整库 AES-256-GCM 加密（Android Keystore 主密钥）\n• 列表卡片：可一键复制账号 / 密码；密码默认掩码，按 👁 切换\n• 长按卡片删除，确认后不可恢复"
        ),
        GuidePage(
            emoji = "🔒",
            title = "锁定便签 + 纪念日提醒",
            body = "• 单条便签可上锁，文本和附件都用 Keystore 加密\n• 提醒支持 3 种模式：\n  – 单次：到时间发通知\n  – 周期：每天 / 每周 / 每月 / 每年循环\n  – 间隔：每 N 天重复\n• 重启手机后提醒仍能触发（BootReceiver）"
        ),
        GuidePage(
            emoji = "⌨️",
            title = "顶部功能栏",
            body = "• 搜索框：按标题 / 标签过滤当前 Tab 的便签\n• ☰ 菜单：回收站 / 导出 / 导入 / 收藏筛选 / 夜间模式 / 隐私锁 / 关于 / 新手引导"
        ),
        GuidePage(
            emoji = "⚙️",
            title = "设置 & 数据",
            body = "• 夜间模式：☰ 里一键切换\n• 隐私锁：离开 App 后回前台需指纹或 PIN\n• 导出 / 导入：JSON 备份，方便换机\n• 提示：密码本和锁定便签的加密数据换新设备无法解密（手机丢了也算）"
        ),
        GuidePage(
            emoji = "🎉",
            title = "准备好了",
            body = "底部 6 个 tab 自由切换。\n遇到问题：☰ → ℹ 关于。\n想再看一遍本引导：☰ → 💡 新手引导。\n\n——祝你用得顺手。",
            isFinal = true
        )
    )
}
