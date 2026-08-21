package com.workbuddy.notes

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
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
import java.util.Locale

/**
 * 「点子存放处」与「未想清楚的事」共用此列表页（借鉴 ANotes 的分类收纳思路）。
 * 通过 [module] 区分两类，UI 完全一致。支持图文/语音/标签/加密。
 */
class ListFragment : Fragment() {

    private lateinit var module: Module
    private lateinit var containerNotes: ViewGroup
    private lateinit var tvTitle: TextView
    private var notes: MutableList<Note> = mutableListOf()
    private var searchQuery: String = ""
    private var favOnly: Boolean = false

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

    private val drawLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val path = result.data?.getStringExtra("path")
        if (!path.isNullOrBlank()) {
            editingNote?.drawingPath = path
            Ui.updateDrawPreview(editingDialog, path)
        }
    }

    companion object {
        private const val REQ_AUDIO = 1002
        private const val REQ_LOC = 1004

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
        styleTitleBar()

        view.findViewById<Button>(R.id.btnAdd).setOnClickListener { addNote() }
        refresh()
        return view
    }

    private fun addNote() {
        openEditor("新建「${Note.MODULE_TITLE[module]}」", null)
    }

    /** 点子 / 未想清 页标题改为醒目的彩色标题条（与背景图拉开对比，两页颜色不同便于区分）。 */
    private fun styleTitleBar() {
        val dp = resources.displayMetrics.density
        val isIdea = module == Module.IDEA
        tvTitle.text = (if (isIdea) "💡 " else "❓ ") + Note.MODULE_TITLE[module]
        tvTitle.setTextColor(android.graphics.Color.WHITE)
        tvTitle.textSize = 18f
        val bg = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = (10 * dp)
            setColor(
                androidx.core.content.ContextCompat.getColor(
                    requireContext(),
                    if (isIdea) R.color.idea_header else R.color.und_header
                )
            )
        }
        tvTitle.background = bg
        tvTitle.setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
    }

    private fun openEditor(title: String, existing: Note?) {
        val isNew = existing == null
        val note = existing ?: Note(module = module, quadZone = 1)
        editingNote = note
        editingDialog = Ui.showEditor(
            requireContext(),
            title,
            note,
            onPickImage = {
                pickImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onRecordAudio = { requestRecordPermission() },
            onDraw = {
                drawLauncher.launch(android.content.Intent(requireContext(), DrawActivity::class.java))
            },
            onPickLocation = { requestLocationPermission() },
            onPickDate = { showDatePicker() },
            onPickTemplate = { showTemplatePicker() },
            onOk = {
                if (isNew) notes.add(note)
                persist()
                refresh()
            }
        )
    }

    fun setSearch(q: String) {
        searchQuery = q.trim()
        refresh()
    }

    /** 只看收藏过滤开关（菜单「⭐ 只看收藏」触发） */
    fun setFavOnly(on: Boolean) {
        favOnly = on
        refresh()
    }

    // ---------- 模板 ----------
    private fun showTemplatePicker() {
        val names = Templates.LIST.map { it.name }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("选择模板")
            .setItems(names) { _, which ->
                Ui.setEditorText(editingDialog, Templates.LIST[which].body)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ---------- 定位 ----------
    private fun requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            captureLocation()
        } else {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQ_LOC)
        }
    }

    private fun captureLocation() {
        val lm = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val loc = try {
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) {
            null
        }
        if (loc != null) {
            editingNote?.latitude = loc.latitude
            editingNote?.longitude = loc.longitude
            editingNote?.locationName = reverseGeocode(loc.latitude, loc.longitude)
            Ui.updateLocationPreview(editingDialog, editingNote!!)
        } else {
            AlertDialog.Builder(requireContext())
                .setTitle("暂无可用的位置")
                .setMessage("请确认已开启定位服务后重试。")
                .setPositiveButton("知道了", null)
                .show()
        }
    }

    private fun reverseGeocode(lat: Double, lng: Double): String? = try {
        val gc = Geocoder(requireContext(), Locale.CHINA)
        @Suppress("DEPRECATION")
        gc.getFromLocation(lat, lng, 1)?.firstOrNull()?.getAddressLine(0)?.take(40)
    } catch (e: Exception) {
        null
    }

    // ---------- 纪念日（周期/按间隔/单次 三选一 + 时间） ----------
    private fun showDatePicker() {
        val n = editingNote ?: return
        EventEditor.show(requireContext(), n) { Ui.updateDatePreview(editingDialog, it) }
    }

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
        when (requestCode) {
            REQ_AUDIO -> if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showRecorderDialog()
            } else {
                AlertDialog.Builder(requireContext())
                    .setTitle("需要麦克风权限")
                    .setMessage("录音便签需要麦克风权限，请在系统设置中开启。")
                    .setPositiveButton("知道了", null)
                    .show()
            }
            REQ_LOC -> if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                captureLocation()
            } else {
                AlertDialog.Builder(requireContext())
                    .setTitle("需要定位权限")
                    .setMessage("记录位置需要定位权限，已取消。")
                    .setPositiveButton("知道了", null)
                    .show()
            }
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
            if (recorder.isRecording) recorder.cancel()
        }
    }

    fun refresh() {
        notes = NotesStore.all()
        containerNotes.removeAllViews()
        notes.filter { it.module == module && !it.deleted }
            .filter { !favOnly || it.favorite }
            .filter { matchSearch(it) }
            .sortedWith(
                // 置顶 > 未完成(创建时间倒序) > 已完成(创建时间倒序)
                compareByDescending<Note> { it.pinned }
                    .thenBy { it.done }
                    .thenByDescending { it.createdAt }
            )
            .forEach { note ->
                containerNotes.addView(
                    Cards.create(
                        requireContext(),
                        note,
                        onEdit = { openEditor("编辑", note) },
                        onColor = { cycleColor(note) },
                        onMove = { showMove(note) },
                        onDelete = { softDelete(note) },
                        onShare = { ShareUtil.shareNoteImage(requireContext(), note) },
                        onUnlock = { Unlock.verify(requireContext()) { openEditor("编辑", note) } },
                        onLocation = { openMap(note) },
                        highlight = searchQuery,
                        showDone = true,
                        onToggleDone = { toggleDone(note) }
                    )
                )
            }
    }

    /** 切换「完成」状态：toggle 后 persist+refresh（排序让完成的便签自然后移） */
    private fun toggleDone(note: Note) {
        note.done = !note.done
        NotesStore.save()
        refresh()
    }

    private fun matchSearch(note: Note): Boolean {
        if (searchQuery.isEmpty()) return true
        val q = searchQuery.lowercase()
        if (note.text.lowercase().contains(q)) return true
        if (note.tagList().any { it.lowercase().contains(q) }) return true
        if (!note.eventLabel.isNullOrBlank() && note.eventLabel!!.lowercase().contains(q)) return true
        return false
    }

    private fun softDelete(note: Note) {
        AlertDialog.Builder(requireContext())
            .setTitle("移入回收站")
            .setMessage("确定删除这条便签吗？将进入回收站，7 天内可恢复。")
            .setPositiveButton("删除") { _, _ ->
                note.deleted = true
                note.deletedAt = System.currentTimeMillis()
                persist()
                refresh()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun openMap(note: Note) {
        if (note.latitude == null || note.longitude == null) return
        val uri = android.net.Uri.parse("geo:${note.latitude},${note.longitude}?q=${note.latitude},${note.longitude}(${android.net.Uri.encode(note.locationName ?: "便签位置")})")
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            AlertDialog.Builder(requireContext())
                .setTitle("无法打开地图")
                .setMessage("设备上没有可用的地图应用。")
                .setPositiveButton("知道了", null)
                .show()
        }
    }

    private fun showMove(note: Note) {
        val other = when (module) {
            Module.IDEA -> arrayOf("移到「未想清楚的事」", "移到「碎碎念」")
            Module.UNDECIDED -> arrayOf("移到「点子存放处」", "移到「碎碎念」")
            else -> arrayOf("移到「点子存放处」", "移到「未想清楚的事」")
        }
        val options = arrayOf("移到「四象限归纳」") + other
        AlertDialog.Builder(requireContext())
            .setTitle("移动便签")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        note.module = Module.QUAD
                        note.quadZone = 1
                    }
                    1 -> {
                        note.module = when (module) {
                            Module.IDEA -> Module.UNDECIDED
                            Module.UNDECIDED -> Module.IDEA
                            else -> Module.IDEA
                        }
                        note.quadZone = 1
                    }
                    else -> {
                        note.module = Module.MUMBLE
                        note.quadZone = 1
                    }
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
        (activity as? MainActivity)?.updateWidget()
    }
}
