package com.workbuddy.notes

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 真加密核心：用 Android Keystore 中的 AES-256-GCM 主密钥，对「锁定便签」的
 * 文本 / 标签 / 附件（图片、涂鸦、语音）做加密后落盘。密钥由系统级安全硬件
 * （TEE / StrongBox）保管，永不离开设备、不以明文出现在任何文件里。
 *
 * 威胁模型（针对用户「手机丢失 / 文件被拷走」的担忧）：
 * - 即便别人把整个 notes.json（含 .bak / 每日快照 / 备份 zip）连同 attachments/
 *   目录一起拷走，没有本机 Keystore 密钥也无法解密，看到的只会是密文。
 * - 本 App 在 AndroidManifest 中**没有任何 INTERNET 权限**，数据不会经 WiFi /
 *   网络外传——所谓「连 WiFi 泄露」在技术上不可能发生。
 *
 * 局限（务必知悉）：
 * - 密钥绑定本机。换手机 / 卸载重装后，旧备份里的加密便签与加密附件无法在新设备
 *   解密。重要账号密码仍建议用专业密码管理器。
 */
object Crypto {
    private const val PROVIDER = "AndroidKeyStore"
    private const val ALIAS = "zaji_vault_key"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val PREFIX = "ZJENC1:"
    /** 加密附件文件的魔数头，用于识别「这个文件是不是已被本 App 加密」。 */
    private const val FILE_MAGIC = "ZJENCF01"
    private val FILE_MAGIC_BYTES = FILE_MAGIC.toByteArray(Charsets.US_ASCII)

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(PROVIDER).apply { load(null) }
    }

    /** 取主密钥：不存在则生成（首次保存加密便签时触发）。 */
    private fun key(): SecretKey {
        val existing = keyStore.getEntry(ALIAS, null)
        if (existing is KeyStore.SecretKeyEntry) return existing.secretKey
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        gen.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return gen.generateKey()
    }

    // ---------------- 文本 / 标签（字符串）加解密 ----------------

    /** 明文 → 带前缀的密文串（base64(iv).base64(ct)）。 */
    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORM).apply {
            init(Cipher.ENCRYPT_MODE, key())
        }
        val iv = cipher.iv
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return PREFIX +
            Base64.encodeToString(iv, Base64.NO_WRAP) + "." +
            Base64.encodeToString(ct, Base64.NO_WRAP)
    }

    /**
     * 密文 → 明文；若入参不是本格式（旧版明文 / 未加密数据）则原样返回，保证向后兼容。
     * 解密失败（如密钥丢失/损坏）返回占位文案而非抛异常，避免崩溃。
     */
    fun decrypt(payload: String?): String {
        if (payload == null) return ""
        if (!payload.startsWith(PREFIX)) return payload
        return try {
            val body = payload.removePrefix(PREFIX)
            val dot = body.indexOf('.')
            val iv = Base64.decode(body.substring(0, dot), Base64.NO_WRAP)
            val ct = Base64.decode(body.substring(dot + 1), Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORM).apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(GCM_TAG_BITS, iv))
            }
            String(cipher.doFinal(ct), Charsets.UTF_8)
        } catch (e: Exception) {
            "🔒 加密数据已损坏或无法解密（密钥已丢失）"
        }
    }

    /** 判断一段文本是否已是本格式密文（用于幂等判断，避免重复加密）。 */
    fun isEncrypted(s: String?): Boolean = !s.isNullOrEmpty() && s.startsWith(PREFIX)

    // ---------------- 附件（文件）加解密 ----------------

    /** 判断一个文件是否已被本 App 加密（读前 8 字节魔数）。 */
    fun isEncryptedFile(file: File): Boolean {
        if (!file.exists() || !file.isFile) return false
        return try {
            val head = ByteArray(FILE_MAGIC.length)
            val n = FileInputStream(file).use { it.read(head) }
            n == FILE_MAGIC.length && head.contentEquals(FILE_MAGIC_BYTES)
        } catch (_: Exception) {
            false
        }
    }

    /** 把源文件加密写入目标文件（流式，避免大图 OOM）。 */
    fun encryptFile(src: File, dst: File) {
        val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val iv = cipher.iv
        FileOutputStream(dst).use { out ->
            out.write(FILE_MAGIC_BYTES)
            out.write(iv.size)
            out.write(iv)
            CipherOutputStream(out, cipher).use { cos ->
                FileInputStream(src).use { it.copyTo(cos) }
            }
        }
    }

    /** 把已加密文件解密写入目标文件（流式）。 */
    fun decryptFile(src: File, dst: File) {
        FileInputStream(src).use { inp ->
            val head = ByteArray(FILE_MAGIC.length)
            if (inp.read(head) != FILE_MAGIC.length || !head.contentEquals(FILE_MAGIC_BYTES)) {
                throw java.io.IOException("not an encrypted file")
            }
            val ivLen = inp.read()
            val iv = ByteArray(ivLen) { inp.read().toByte() }
            val cipher = Cipher.getInstance(TRANSFORM).apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(GCM_TAG_BITS, iv))
            }
            CipherInputStream(inp, cipher).use { cis ->
                FileOutputStream(dst).use { it.copyTo(cis) }
            }
        }
    }

    /**
     * 读取附件时按需解密：返回可直接使用的「明文路径」。
     * - 文件未加密（未锁定便签 / 旧数据）→ 原样返回；
     * - 已加密（锁定便签）→ 解密到 cacheDir 的同名缓存文件并返回该路径。
     * 这样 UI 无需关心加密与否，直接 decodeSampled / 播放即可。
     */
    fun plainPath(context: Context, path: String?): String? {
        if (path == null) return null
        val f = File(path)
        if (!f.exists()) return null
        if (!isEncryptedFile(f)) return path
        val cache = File(context.cacheDir, "dec_" + f.name)
        return try {
            decryptFile(f, cache)
            cache.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
