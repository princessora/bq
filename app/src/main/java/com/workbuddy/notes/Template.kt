package com.workbuddy.notes

/** 一条便签模板：name 用于列表展示，body 为点选后回填的正文。 */
data class NoteTemplate(val name: String, val body: String)

/** 内置模板预设（⑧ 模板功能）。 */
object Templates {
    val LIST = listOf(
        NoteTemplate(
            "购物清单",
            "□ \n□ \n□ \n□ "
        ),
        NoteTemplate(
            "旅行打包",
            "□ 身份证/证件\n□ 手机/充电宝/数据线\n□ 换洗衣物\n□ 洗漱用品\n□ 雨伞"
        ),
        NoteTemplate(
            "会议纪要",
            "议题：\n\n结论：\n\n待办：\n- \n- "
        ),
        NoteTemplate(
            "读书笔记",
            "书名：\n\n金句：\n\n感悟："
        ),
        NoteTemplate(
            "健身计划",
            "□ 热身 5 分钟\n□ 力量训练\n□ 有氧 30 分钟\n□ 拉伸"
        ),
        NoteTemplate(
            "灵感捕捉",
            "灵感：\n\n为什么想到它：\n\n下一步："
        )
    )
}
