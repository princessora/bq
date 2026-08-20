package com.workbuddy.notes

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * 本地持久化层。借鉴 ANotes 的「数据存本地、无需联网」思路，
 * 用 JSON 文件保存在应用私有目录，卸载即清空，隐私友好。
 */
object NotesRepository {
    private const val FILE_NAME = "notes.json"
    private val gson = Gson()

    fun load(context: Context): MutableList<Note> {
        return try {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) return mutableListOf()
            val json = file.readText(StandardCharsets.UTF_8)
            val type = object : TypeToken<MutableList<Note>>() {}.type
            gson.fromJson<MutableList<Note>>(json, type) ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun save(context: Context, notes: List<Note>) {
        try {
            val file = File(context.filesDir, FILE_NAME)
            file.writeText(gson.toJson(notes), StandardCharsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** 导出备份用：把便签列表序列化为 JSON 字符串 */
    fun toJson(notes: List<Note>): String = gson.toJson(notes)

    /** 导入备份用：解析 JSON 字符串为便签列表（解析失败返回空表） */
    fun fromJson(json: String): MutableList<Note> = try {
        gson.fromJson<MutableList<Note>>(
            json,
            object : TypeToken<MutableList<Note>>() {}.type
        ) ?: mutableListOf()
    } catch (e: Exception) {
        mutableListOf()
    }
}
