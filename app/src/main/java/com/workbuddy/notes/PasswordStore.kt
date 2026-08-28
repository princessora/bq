package com.workbuddy.notes

import android.content.Context

/**
 * 密码本共享内存态单例（镜像 BookkeepingStore）。
 *
 * 所有对密码数据的增删改都走这里拿到的**同一个 MutableList 引用**，
 * 任意 Fragment 修改后调用 [save] 写盘，永远不会互相覆盖。
 */
object PasswordStore {
    private var list: MutableList<PasswordEntry>? = null
    private var appContext: Context? = null

    fun init(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    /** 返回共享列表（首次调用时从磁盘加载） */
    fun all(): MutableList<PasswordEntry> {
        val ctx = appContext ?: error("PasswordStore 未初始化，请先调用 init()")
        if (list == null) list = PasswordRepository.load(ctx)
        return list!!
    }

    fun save() {
        val ctx = appContext ?: error("PasswordStore 未初始化，请先调用 init()")
        PasswordRepository.save(ctx, list ?: mutableListOf())
    }

    fun reload() {
        val ctx = appContext ?: error("PasswordStore 未初始化，请先调用 init()")
        list = PasswordRepository.load(ctx)
    }

    /** 新增一条（置顶插入，最新在前），并写盘 */
    fun add(e: PasswordEntry) {
        all().add(0, e)
        save()
    }

    fun update(e: PasswordEntry) {
        val idx = all().indexOfFirst { it.id == e.id }
        if (idx >= 0) {
            all()[idx] = e
            save()
        } else {
            add(e)
        }
    }

    fun remove(id: String) {
        all().removeAll { it.id == id }
        save()
    }
}
