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
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import java.util.Locale

/**
 * 四象限归纳（借鉴 Einsen 的 Eisenhower 矩阵）。
 * 支持图文 + 语音 + 标签 + 加密；删除进入回收站（软删除），支持全文搜索过滤。
 */
class QuadFragment : Fragment() {

    private val zones = mutableMapOf<Int, ViewGroup>()
    private var notes: MutableList<Note> = mutableListOf()
    private var searchQuery: String = ""

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

    private fun openEditor(title: String, zone: Int, existing: Note?) {
        val isNew = existing == null
        val note = existing ?: Note(module = Module.QUAD, quadZone = zone)
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

    // ---------- 搜索 ----------
    fun setSearch(q: String) {
        searchQuery = q.trim()
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

    // ---------- 纪念日 ----------
    private fun showDatePicker() {
        val now = java.util.Calendar.getInstance()
        val dp = android.app.DatePickerDialog(
            requireContext(),
            { _, y, m, d ->
                val cal = java.util.Calendar.getInstance().apply { set(y, m, d, 0, 0, 0) }
                editingNote?.eventDate = cal.timeInMillis
                editingNote?.eventLabel = "纪念日"
                Ui.updateDatePreview(editingDialog, editingNote!!)
            },
            now.get(java.util.Calendar.YEAR),
            now.get(java.util.Calendar.MONTH),
            now.get(java.util.Calendar.DAY_OF_MONTH)
        )
        dp.show()
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
        zones.forEach { (zone, container) ->
            container.removeAllViews()
            notes.filter { it.module == Module.QUAD && it.quadZone == zone && !it.deleted }
                .filter { matchSearch(it) }
                .sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.createdAt })
                .forEach { note ->
                    container.addView(
                        Cards.create(
                            requireContext(),
                            note,
                            onEdit = { openEditor(Note.QUAD_ZONES[zone - 1], zone, note) },
                            onColor = { cycleColor(note) },
                            onMove = { showMove(note) },
                            onDelete = { softDelete(note) },
                            onShare = { ShareUtil.shareNoteImage(requireContext(), note) },
                            onUnlock = { Unlock.verify(requireContext()) { openEditor(Note.QUAD_ZONES[zone - 1], zone, note) } },
                            onLocation = { openMap(note) }
                        )
                    )
                }
        }
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
        (activity as? MainActivity)?.updateWidget()
    }

    companion object {
        private const val REQ_AUDIO = 1001
        private const val REQ_LOC = 1003
    }
}
