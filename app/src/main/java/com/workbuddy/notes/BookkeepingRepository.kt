package com.workbuddy.notes

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * 记账本地持久化层。
 *
 * 与 NotesRepository 同源思路：JSON 存应用私有目录，本 App 无 INTERNET 权限，
 * 数据全留本机（对个人财务隐私更友好）。沿用「原子写 + 写前备份」的防丢策略。
 * 不涉及加密/附件归一化（记账无附件，且本地已足够私密）。
 */
object BookkeepingRepository {
    private const val FILE_NAME = "bookkeeping.json"
    private const val BACKUP_NAME = "bookkeeping.json.bak"
    private val gson = Gson()

    fun load(context: Context): MutableList<Transaction> {
        val dir = context.filesDir
        // 1) 主文件优先
        readList(File(dir, FILE_NAME))?.let { return it }
        // 2) 主文件损坏/缺失 → 回退 .bak 并尝试修复主文件
        val bak = File(dir, BACKUP_NAME)
        val recovered = readList(bak)
        if (recovered != null) {
            try { bak.copyTo(File(dir, FILE_NAME), overwrite = true) } catch (_: Exception) {}
            return recovered
        }
        // 3) 都失败：返回空表，不删任何文件
        return mutableListOf()
    }

    /** 读取并解析一个 JSON 文件；不存在/空/解析失败都返回 null（不抛异常） */
    private fun readList(file: File): MutableList<Transaction>? {
        return try {
            if (!file.exists()) null
            else {
                val json = file.readText(StandardCharsets.UTF_8)
                if (json.isBlank()) null
                else {
                    val type = object : TypeToken<MutableList<Transaction>>() {}.type
                    gson.fromJson<MutableList<Transaction>>(json, type)
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    fun save(context: Context, list: List<Transaction>) {
        try {
            val json = gson.toJson(list)
            val dir = context.filesDir
            val file = File(dir, FILE_NAME)
            // 1) 写前轮转：当前主文件 → 备份
            if (file.exists()) {
                file.copyTo(File(dir, BACKUP_NAME), overwrite = true)
            }
            // 2) 原子写：先写临时文件再 rename
            val tmp = File(dir, "$FILE_NAME.tmp")
            tmp.writeText(json, StandardCharsets.UTF_8)
            if (!tmp.renameTo(file)) {
                file.writeText(json, StandardCharsets.UTF_8)
                tmp.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun toJson(list: List<Transaction>): String = gson.toJson(list)
    fun fromJson(json: String): MutableList<Transaction> = try {
        val type = object : TypeToken<MutableList<Transaction>>() {}.type
        gson.fromJson<MutableList<Transaction>>(json, type) ?: mutableListOf()
    } catch (e: Exception) {
        mutableListOf()
    }
}
