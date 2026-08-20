package com.workbuddy.notes

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.FileOutputStream

/**
 * 附件（图片/语音）管理：统一存到 app 私有目录 files/attachments/ 下，
 * 卸载即清空；Note 里只存绝对路径字符串。
 */
object Media {
    private const val ATTACH_DIR = "attachments"

    private fun attachDir(context: Context): File =
        File(context.filesDir, ATTACH_DIR).apply { mkdirs() }

    /** 把 content Uri（图库/相册）复制到私有目录，返回绝对路径；失败返回 null */
    fun saveImage(context: Context, uri: Uri): String? {
        return try {
            val name = "img_${System.currentTimeMillis()}.jpg"
            val dst = File(attachDir(context), name)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dst).use { output -> input.copyTo(output) }
            }
            dst.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** 删除附件文件（容错，不存在也不报错） */
    fun deleteFile(path: String?) {
        if (path.isNullOrBlank()) return
        try {
            File(path).delete()
        } catch (_: Exception) {
        }
    }
}

/**
 * 简易录音器：MediaRecorder 录 AAC 到私有目录。
 * 用前必须已授予 RECORD_AUDIO 权限。
 */
class AudioRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var path: String? = null
    var isRecording: Boolean = false
        private set

    /** 当前录音文件路径（开始录音后才有值） */
    val filePath: String?
        get() = path

    /** 开始录音，返回文件路径；失败返回 null */
    fun start(): String? {
        stop()
        val name = "aud_${System.currentTimeMillis()}.m4a"
        val file = File(Media.attachDir(context), name)
        path = file.absolutePath
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
        return try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(44100)
            r.setAudioEncodingBitRate(96000)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            isRecording = true
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            try { r.release() } catch (_: Exception) {}
            recorder = null
            isRecording = false
            path = null
            null
        }
    }

    /** 停止录音，返回时长（毫秒）；失败返回 -1（文件已清理） */
    fun stop(): Long {
        val r = recorder ?: return -1
        return try {
            r.stop()
            isRecording = false
            val dur = durationMs(path)
            r.release()
            recorder = null
            dur
        } catch (e: Exception) {
            // 录音太短 stop 会抛异常，清理掉即可
            e.printStackTrace()
            isRecording = false
            try { r.reset(); r.release() } catch (_: Exception) {}
            recorder = null
            Media.deleteFile(path)
            path = null
            -1
        }
    }

    /** 取消录音并删除文件 */
    fun cancel() {
        try {
            recorder?.let { it.stop(); it.release() }
        } catch (_: Exception) {
        }
        recorder = null
        isRecording = false
        Media.deleteFile(path)
        path = null
    }

    private fun durationMs(path: String?): Long {
        if (path == null) return 0
        return try {
            val mp = MediaPlayer()
            mp.setDataSource(path)
            mp.prepare()
            val d = mp.duration.toLong()
            mp.release()
            d
        } catch (_: Exception) {
            0
        }
    }
}

/** 语音播放单例：同一时间只播一个，重复点击暂停/继续 */
object AudioPlayer {
    private var player: MediaPlayer? = null
    private var currentPath: String? = null

    /** 播放/停止切换；onDone 在播放自然结束或手动停止时回调（用于刷新 UI） */
    fun toggle(path: String?, onDone: (() -> Unit)? = null) {
        if (path == null) return
        if (isPlaying(path)) {
            stop()
            onDone?.invoke()
            return
        }
        stop()
        currentPath = path
        player = try {
            MediaPlayer().apply {
                setDataSource(path)
                setOnCompletionListener {
                    stop()
                    onDone?.invoke()
                }
                setOnErrorListener { _, _, _ ->
                    stop()
                    onDone?.invoke()
                    true
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            currentPath = null
            null
        }
    }

    fun isPlaying(path: String?): Boolean =
        player?.isPlaying == true && currentPath == path

    fun stop() {
        try {
            player?.release()
        } catch (_: Exception) {
        }
        player = null
        currentPath = null
    }
}
