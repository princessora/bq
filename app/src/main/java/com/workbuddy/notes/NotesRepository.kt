package com.workbuddy.notes

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * 本地持久化层。借鉴 ANotes 的「数据存本地、无需联网」思路，
 * 用 JSON 文件保存在应用私有目录，卸载即清空，隐私友好。
 *
 * ## 防丢数据的三道保障
 * 1. **原子写**：先写 `notes.json.tmp` 再 rename 成 `notes.json`，
 *    避免「写到一半崩溃把主文件写坏」导致整份数据解析失败。
 * 2. **写前轮转备份**：每次保存前把当前 `notes.json` 复制为 `notes.json.bak`，
 *    主文件损坏时自动回退到备份（load 失败 → 尝试 .bak）。
 * 3. **每日快照**：`MainActivity` 启动时归档当日 `notes-YYYYMMDD.json`（见 MainActivity），
 *    保留最近 7 天，再往前由用户 ZIP 导出兜底。
 */
object NotesRepository {
    private const val FILE_NAME = "notes.json"
    private const val BACKUP_NAME = "notes.json.bak"
    private val gson = Gson()

    /**
     * 落盘 / 导出前：把「锁定便签」的 text / tags 加密（已加密则跳过，保证幂等、
     * 可重复导入而不会被二次加密）。内存态的 Note 始终是明文，这里只改动副本。
     */
    private fun prepareForStorage(notes: List<Note>): List<Note> = notes.map { note ->
        if (!note.locked) return@map note
        note.copy(
            text = if (Crypto.isEncrypted(note.text)) note.text else enc(note.text),
            tags = if (note.tags != null && Crypto.isEncrypted(note.tags)) note.tags
                   else note.tags?.let { enc(it) }
        )
    }

    /**
     * 载入 / 导入后：把「锁定便签」的 text / tags 解密回明文。
     * 内存态始终明文，UI / 搜索 / 分享逻辑无需改动。
     */
    private fun afterLoad(notes: MutableList<Note>) {
        for (n in notes) {
            if (!n.locked) continue
            if (Crypto.isEncrypted(n.text)) n.text = Crypto.decrypt(n.text)
            if (!n.tags.isNullOrBlank() && Crypto.isEncrypted(n.tags)) n.tags = Crypto.decrypt(n.tags)
        }
    }

    /** 加密单字段；极端情况下（如设备不支持 Keystore）加密失败则保留明文，避免丢数据。 */
    private fun enc(s: String): String = try {
        Crypto.encrypt(s)
    } catch (e: Exception) {
        e.printStackTrace()
        s
    }

    fun load(context: Context): MutableList<Note> {
        val dir = context.filesDir
        // 1) 主文件优先
        readList(File(dir, FILE_NAME))?.let { return it }
        // 2) 主文件损坏/缺失 → 回退 .bak，并尝试用备份修复主文件
        val bak = File(dir, BACKUP_NAME)
        val recovered = readList(bak)
        if (recovered != null) {
            try {
                bak.copyTo(File(dir, FILE_NAME), overwrite = true)
            } catch (_: Exception) {
            }
            return recovered
        }
        // 3) 都失败：返回空表但**不删除任何文件**，等待用户用 ZIP 备份恢复
        return mutableListOf()
    }

    /** 读取并解析一个 JSON 文件；文件不存在 / 内容为空 / 解析失败都返回 null（不抛异常） */
    private fun readList(file: File): MutableList<Note>? {
        return try {
            if (!file.exists()) return null
            val json = file.readText(StandardCharsets.UTF_8)
            if (json.isBlank()) return null
            val type = object : TypeToken<MutableList<Note>>() {}.type
            val list = gson.fromJson<MutableList<Note>>(json, type) ?: return null
            afterLoad(list)
            list
        } catch (e: Exception) {
            null
        }
    }

    fun save(context: Context, notes: List<Note>) {
        try {
            val json = toJson(notes)
            val dir = context.filesDir
            val file = File(dir, FILE_NAME)

            // 1) 轮转：当前主文件 → 备份（写前快照，保证 .bak 永远是最近一次成功的数据）
            if (file.exists()) {
                file.copyTo(File(dir, BACKUP_NAME), overwrite = true)
            }

            // 2) 原子写：先写临时文件，再 rename（同目录 rename 是原子操作）
            val tmp = File(dir, "$FILE_NAME.tmp")
            tmp.writeText(json, StandardCharsets.UTF_8)
            if (!tmp.renameTo(file)) {
                // rename 失败（极端情况）降级为直接写
                file.writeText(json, StandardCharsets.UTF_8)
                tmp.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** 导出备份用：把便签列表序列化为 JSON 字符串（锁定便签已加密，备份包同样是密文） */
    fun toJson(notes: List<Note>): String = gson.toJson(prepareForStorage(notes))

    /** 导入备份用：解析 JSON 字符串为便签列表（解析失败返回空表；密文会自动解密） */
    fun fromJson(json: String): MutableList<Note> = try {
        val type = object : TypeToken<MutableList<Note>>() {}.type
        val list = gson.fromJson<MutableList<Note>>(json, type) ?: mutableListOf()
        afterLoad(list)
        list
    } catch (e: Exception) {
        mutableListOf()
    }
}
