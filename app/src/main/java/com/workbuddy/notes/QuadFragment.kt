package com.workbuddy.notes

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

/**
 * 四象限归纳（借鉴 Einsen 的 Eisenhower 矩阵）。
 * 四个象限：① 重要·紧急 ② 重要·不紧急 ③ 不重要·紧急 ④ 不重要·不紧急。
 * 每条便签可在象限间「移」，也能跨区移进「点子 / 未想清」。
 * 支持图文 + 语音：编辑弹窗可加图片、录音。
 */
class QuadFragment : Fragment() {

    private val zones = mutableMapOf<Int, ViewGroup>()
    private var notes: MutableList<Note> = mutableListOf()

    /** 当前正在编辑的便签（新建时为临时对象，保存时才入列表） */
    private var editingNote: Note? = null
    private var editingDialog: AlertDialog? = null

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val path = Media.saveImage(requireContext(), uri)
            editingNote?.imagePath = path
            Ui.updateImagePreview(editingDialog, path)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_quad, container, false)

        zones[1] = view.findViewById(R.id.zone1)
        zones[2] = view.findViewById(R.id.zone2)
        zones[3] = view.findViewById(R.id.zone3)
        zones[4] = view.findViewById(R.id.zone4)

        view.findViewById<Button>(R.id.add1).setOnClickListener { addNote(1) }
        view.findViewById<Button>(R.id.add2).setOnClickListener { addNote(2) }
        view.findViewById<Button>(R.id.add3).setOnClickListener { addNote(3) }
        view.findViewById<Button>(R.id.add4).setOnClickListener { addNote(4) }

        refresh()
        return view
    }

    private fun addNote(zone: Int) {
        openEditor(Note.QUAD_ZONES[zone - 1], zone, null)
    }

    /** 打开编辑弹窗（新建/编辑共用），支持图片与语音 */
    private fun openEditor(title: String, zone: Int, existing: Note?) {
        val isNew = existing == null
        val note = existing ?: Note(module = Module.QUAD, quadZone = zone)
        editingNote = note
        editingDialog = Ui.showEditor(
            requireContext(),
            title,
            note.text,
            note.colorHex,
            imagePath = note.imagePath,
            audioPath = note.audioPath,
            audioDurationMs = note.audioDurationMs,
            onPickImage = { pickImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onRecordAudio = { requestRecordPermission() },
            onOk = { t, c ->
                note.text = t
                note.colorHex = c
                if (isNew) notes.add(note)
                persist()
                refresh()
            }
        )
    }

    // ---------- 录音 ----------

    private fun requestRecordPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            showRecorderDialog()
        } else {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_AUDIO)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_AUDIO &&
            grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            showRecorderDialog()
        } else {
            AlertDialog.Builder(requireContext())
                .setTitle("需要麦克风权限")
                .setMessage("录音便签需要麦克风权限，请在系统设置中开启。")
                .setPositiveButton("知道了", null)
                .show()
        }
    }

    private fun showRecorderDialog() {
        val recorder = AudioRecorder(requireContext())
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("语音便签")
            .setMessage("点击「开始」后说话，点「停止」保存")
            .setPositiveButton("开始", null)
            .setNegativeButton("取消", null)
            .create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (recorder.isRecording) {
                // 停止并保存
                val dur = recorder.stop()
                if (dur >= 0 && recorder.filePath != null) {
                    editingNote?.audioPath = recorder.filePath
                    editingNote?.audioDurationMs = dur
                    Ui.updateAudioPreview(editingDialog, recorder.filePath, dur)
                }
                dialog.dismiss()
            } else {
                val path = recorder.start()
                if (path != null) {
                    dialog.setMessage("录音中… 点「停止」保存")
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).text = "停止"
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).text = "取消录音"
                }
            }
        }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
            if (recorder.isRecording) recorder.cancel()
            dialog.dismiss()
        }
        dialog.setOnDismissListener {
            // 只在「还在录音」时清理；已保存的文件不能删
            if (recorder.isRecording) recorder.cancel()
        }
    }

    fun refresh() {
        // 共享列表：拿到所有 Fragment 共用的同一份引用
        notes = NotesStore.all()
        zones.forEach { (zone, container) ->
            container.removeAllViews()
            notes.filter { it.module == Module.QUAD && it.quadZone == zone }
                .sortedByDescending { it.createdAt }
                .forEach { note ->
                    container.addView(
                        Cards.create(
                            requireContext(),
                            note,
                            onEdit = {
                                openEditor(Note.QUAD_ZONES[zone - 1], zone, note)
                            },
                            onColor = { cycleColor(note) },
                            onMove = { showMove(note) },
                            onDelete = {
                                AlertDialog.Builder(requireContext())
                                    .setTitle("确认删除")
                                    .setMessage("确定要删除这条便签吗？（图片和语音也会一并删除）")
                                    .setPositiveButton("删除") { _, _ ->
                                        Media.deleteFile(note.imagePath)
                                        Media.deleteFile(note.audioPath)
                                        notes.remove(note)
                                        persist()
                                        refresh()
                                    }
                                    .setNegativeButton("取消", null)
                                    .show()
                            }
                        )
                    )
                }
        }
    }

    private fun showMove(note: Note) {
        val options = arrayOf(
            "移到「点子存放处」",
            "移到「未想清楚的事」",
            "改到其他象限"
        )
        AlertDialog.Builder(requireContext())
            .setTitle("移动便签")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> { note.module = Module.IDEA; note.quadZone = 1; afterMove() }
                    1 -> { note.module = Module.UNDECIDED; note.quadZone = 1; afterMove() }
                    2 -> changeZone(note)
                }
            }
            .show()
    }

    private fun changeZone(note: Note) {
        AlertDialog.Builder(requireContext())
            .setTitle("选择象限")
            .setSingleChoiceItems(
                Note.QUAD_ZONES.toTypedArray(),
                note.quadZone - 1
            ) { d, which ->
                note.quadZone = which + 1
                persist()
                refresh()
                d.dismiss()
            }
            .show()
    }

    private fun afterMove() {
        persist()
        refresh()
        (activity as? MainActivity)?.refreshAll()
    }

    private fun cycleColor(note: Note) {
        val list = ColorPalette.COLORS
        val idx = list.indexOf(note.colorHex).let { if (it < 0) 0 else (it + 1) % list.size }
        note.colorHex = list[idx]
        persist()
        refresh()
    }

    private fun persist() {
        NotesStore.save()
    }

    companion object {
        private const val REQ_AUDIO = 1001
    }
}
