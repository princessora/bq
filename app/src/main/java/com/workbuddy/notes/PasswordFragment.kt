package com.workbuddy.notes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

/**
 * 密码本页（r48 占位骨架，先确认 tab + 数据层能编译；
 * 完整列表/录入/复制见 r49）。
 */
class PasswordFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_password, container, false)

    /** 供 Activity.refreshAll 在 onResume 时调用（占位阶段无操作）。 */
    fun refresh() {}
}
