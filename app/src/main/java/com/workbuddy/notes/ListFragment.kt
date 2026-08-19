package com.workbuddy.notes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment

/**
 * 「点子存放处」与「未想清楚的事」共用此列表页（借鉴 ANotes 的分类收纳思路）。
 * 通过 [module] 区分两类，UI 完全一致。
 */
class ListFragment : Fragment() {

    private lateinit var module: Module
    private lateinit var containerNotes: ViewGroup
    private lateinit var tvTitle: TextView
    private var notes = mutableListOf<Note>()

    companion object {
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
        Ui.showEditor(requireContext(), "新建「${Note.MODULE_TITLE[module]}」", "", "#FFFFFF") { text, color ->
            notes.add(
                Note(
                    text = text,
                    colorHex = color,
                    module = module,
                    quadZone = 1
                )
            )
            persist()
            refresh()
        }
    }

    fun refresh() {
        notes = NotesRepository.load(requireContext())
        containerNotes.removeAllViews()
        notes.filter { it.module == module }
            .sortedByDescending { it.createdAt }
            .forEach { note ->
                containerNotes.addView(
                    Cards.create(
                        requireContext(),
                        note,
                        onEdit = {
                            Ui.showEditor(
                                requireContext(),
                                "编辑",
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
                            AlertDialog.Builder(requireContext())
                                .setTitle("确认删除")
                                .setMessage("确定要删除这条便签吗？")
                                .setPositiveButton("删除") { _, _ ->
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
        NotesRepository.save(requireContext(), notes)
    }
}
