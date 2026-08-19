package com.workbuddy.notes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment

/**
 * 四象限归纳（借鉴 Einsen 的 Eisenhower 矩阵）。
 * 四个象限：① 重要·紧急 ② 重要·不紧急 ③ 不重要·紧急 ④ 不重要·不紧急。
 * 每条便签可在象限间「移」，也能跨区移进「点子 / 未想清」。
 */
class QuadFragment : Fragment() {

    private val zones = mutableMapOf<Int, ViewGroup>()
    private var notes = mutableListOf<Note>()

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
        Ui.showEditor(requireContext(), Note.QUAD_ZONES[zone - 1], "", "#FFFFFF") { text, color ->
            notes.add(
                Note(
                    text = text,
                    colorHex = color,
                    module = Module.QUAD,
                    quadZone = zone
                )
            )
            persist()
            refresh()
        }
    }

    fun refresh() {
        notes = NotesRepository.load(requireContext())
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
                                Ui.showEditor(
                                    requireContext(),
                                    Note.QUAD_ZONES[zone - 1],
                                    note.text,
                                    note.colorHex
                                ) { t, c ->
                                    note.text = t
                                    note.colorHex = c
                                    persist()
                                    refresh()
                                }
                            },
                            onColor = { cycleColor(note) },
                            onMove = { showMove(note) },
                            onDelete = {
                                notes.remove(note)
                                persist()
                                refresh()
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
        NotesRepository.save(requireContext(), notes)
    }
}
