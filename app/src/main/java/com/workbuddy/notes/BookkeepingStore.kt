package com.workbuddy.notes

import android.content.Context

/**
 * 记账共享内存态单例（镜像 NotesStore）。
 *
 * 所有对记账数据的增删改都走这里拿到的**同一个 MutableList 引用**，
 * 任意 Fragment 修改后调用 [save] 写盘，永远不会互相覆盖。
 */
object BookkeepingStore {
    private var list: MutableList<Transaction>? = null
    private var appContext: Context? = null

    fun init(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    /** 返回共享列表（首次调用时从磁盘加载） */
    fun all(): MutableList<Transaction> {
        val ctx = appContext ?: error("BookkeepingStore 未初始化，请先调用 init()")
        if (list == null) list = BookkeepingRepository.load(ctx)
        return list!!
    }

    fun save() {
        val ctx = appContext ?: error("BookkeepingStore 未初始化，请先调用 init()")
        BookkeepingRepository.save(ctx, list ?: mutableListOf())
    }

    fun reload() {
        val ctx = appContext ?: error("BookkeepingStore 未初始化，请先调用 init()")
        list = BookkeepingRepository.load(ctx)
    }

    /** 新增一条（置顶插入，最新在前），并写盘 */
    fun add(t: Transaction) {
        all().add(0, t)
        save()
    }

    fun remove(id: String) {
        all().removeAll { it.id == id }
        save()
    }
}
