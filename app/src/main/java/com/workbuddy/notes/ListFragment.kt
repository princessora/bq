package com.workbuddy.notes

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

/**
 * 「点子存放处」与「未想清楚的事」共用此列表页（借鉴 ANotes 的分类收纳思路）。
 * 通过 [module] 区分两类，UI 完全一致。支持图文 + 语音。
 */
class ListFragment : Fragment() {

    private lateinit var module: Module
    private lateinit var containerNotes: ViewGroup
    private lateinit var tvTitle: TextView
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

    companion object {
        private const val REQ_AUDIO = 1002

        fun newInstance(m: Module): ListFragment {
            val f = ListFragment()
            f.arguments = Bundle().apply { putString("module", m.name) }
            return f
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        module = Module.valueOf(requireArguments().getString("module") ?: "IDEA")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_list, container, false)
        tvTitle = view.findViewById(R.id.tvTitle)
        containerNotes = view.findViewById(R.id.listNotes)
        tvTitle.text = Note.MODULE_TITLE[module]

        view.findViewById<Button>(R.id.btnAdd).setOnClickListener { addNote() }
        refresh()
        return view
    }

    private fun addNote() {
        openEditor("新建「${Note.MODULE_TITLE[module]}」", null)
    }

    /** 打开编辑弹窗（新建/编辑共用），支持图片与语音 */
    private fun openEditor(title: String, existing: Note?) {
        val isNew = existing == null
        val note = existing ?: Note(module = module, quadZone = 1)
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
        // 共享列表：与其他 Fragment 共用同一份引用，避免各自快照互相覆盖
        notes = NotesStore.all()
        containerNotes.removeAllViews()
        notes.filter { it.module == module }
            .sortedByDescending { it.createdAt }
            .forEach { note ->
                containerNotes.addView(
                    Cards.create(
                        requireContext(),
                        note,
                        onEdit = {
                            openEditor("编辑", note)
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

    private fun showMove(note: Note) {
        val options = if (module == Module.IDEA) {
            arrayOf("移到「四象限归纳」", "移到「未想清楚的事」")
        } else {
            arrayOf("移到「四象限归纳」", "移到「点子存放处」")
        }
        AlertDialog.Builder(requireContext())
            .setTitle("移动便签")
            .setItems(options) { _, which ->
                if (which == 0) {
                    note.module = Module.QUAD
                    note.quadZone = 1
                } else {
                    note.module = if (module == Module.IDEA) Module.UNDECIDED else Module.IDEA
                    note.quadZone = 1
                }
                persist()
                refresh()
                (activity as? MainActivity)?.refreshAll()
            }
            .show()
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
}
