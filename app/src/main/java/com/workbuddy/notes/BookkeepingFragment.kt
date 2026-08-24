package com.workbuddy.notes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

/**
 * 记账页（占位骨架，第一层先确认「导航 tab + 数据层」可编译可运行；
 * 后续提交会在此填充列表 / 录入弹窗 / 月度汇总 / 图表 / 自动捕获）。
 */
class BookkeepingFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_book, container, false)
    }
}
