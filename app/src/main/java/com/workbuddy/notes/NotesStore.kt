package com.workbuddy.notes

import android.content.Context

/**
 * 共享内存态单例。
 *
 * 背景：三个 Fragment（四象限 / 点子 / 未想清）之前各自 `NotesRepository.load()`
 * 出一份独立快照，`persist()` 时把各自快照整盘写回，**互相覆盖**——这是
 * 「退出应用后数据丢失」的根因。
 *
 * 现在所有 Fragment 都通过 [all] 拿到**同一个 MutableList 引用**，任意一处
 * add / remove / 改字段，其余 Fragment 立即可见；[save] 写盘也是同一份列表，
 * 永远不会覆盖。
 */
object NotesStore {
    private var notes: MutableList<Note>? = null
    private var appContext: Context? = null

    /** 在 Application / Activity 创建时调用一次，持有 applicationContext 防泄漏 */
    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
    }

    /** 返回共享列表（首次调用时从磁盘加载） */
    fun all(): MutableList<Note> {
        val ctx = appContext ?: error("NotesStore 未初始化，请先调用 init()")
        if (notes == null) {
            notes = NotesRepository.load(ctx)
        }
        return notes!!
    }

    /** 把共享列表写盘 */
    fun save() {
        val ctx = appContext ?: error("NotesStore 未初始化，请先调用 init()")
        NotesRepository.save(ctx, notes ?: mutableListOf())
    }

    /** 强制从磁盘重读（一般不需要，保留以备将来同步场景） */
    fun reload() {
        val ctx = appContext ?: error("NotesStore 未初始化，请先调用 init()")
        notes = NotesRepository.load(ctx)
    }
}
