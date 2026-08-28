package com.workbuddy.notes

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * 密码本本地持久化层。
 *
 * 与记账模块同源思路（JSON 存应用私有目录、原子写 + 写前备份），
 * 但**多一层整库加密**：写出前用 [Crypto.encrypt] 加密整个 JSON 字符串，
 * 读入时用 [Crypto.decrypt] 还原。这样即便文件被拷走，没有本机 Keystore 密钥也无法读取。
 * 本 App 无 INTERNET 权限，数据全留本机。
 */
object PasswordRepository {
    private const val FILE_NAME = "passwords.json"
    private const val BACKUP_NAME = "passwords.json.bak"
    private val gson = Gson()

    fun load(context: Context): MutableList<PasswordEntry> {
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

    /** 读取并解析一个（加密的）JSON 文件；不存在/空/解析失败都返回 null（不抛异常） */
    private fun readList(file: File): MutableList<PasswordEntry>? {
        return try {
            if (!file.exists()) null
            else {
                val raw = file.readText(StandardCharsets.UTF_8)
                if (raw.isBlank()) null
                else {
                    // 优先按加密格式解密；若文件是旧版明文（理论不会出现，作为兜底）直接解析
                    val json = if (Crypto.isEncrypted(raw)) Crypto.decrypt(raw) else raw
                    val type = object : TypeToken<MutableList<PasswordEntry>>() {}.type
                    gson.fromJson<MutableList<PasswordEntry>>(json, type)
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    fun save(context: Context, list: List<PasswordEntry>) {
        try {
            val json = gson.toJson(list)
            val enc = Crypto.encrypt(json)
            val dir = context.filesDir
            val file = File(dir, FILE_NAME)
            // 1) 写前轮转：当前主文件 → 备份
            if (file.exists()) {
                file.copyTo(File(dir, BACKUP_NAME), overwrite = true)
            }
            // 2) 原子写：先写临时文件再 rename
            val tmp = File(dir, "$FILE_NAME.tmp")
            tmp.writeText(enc, StandardCharsets.UTF_8)
            if (!tmp.renameTo(file)) {
                file.writeText(enc, StandardCharsets.UTF_8)
                tmp.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun toJson(list: List<PasswordEntry>): String = gson.toJson(list)
    fun fromJson(json: String): MutableList<PasswordEntry> = try {
        val type = object : TypeToken<MutableList<PasswordEntry>>() {}.type
        gson.fromJson<MutableList<PasswordEntry>>(json, type) ?: mutableListOf()
    } catch (e: Exception) {
        mutableListOf()
    }
}
