package com.workbuddy.notes

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import java.util.Calendar

/**
 * 纪念日 / 提醒 设置面板。参考 Android 系统闹钟的「周期 / 按间隔 / 单次」三选一 + 时间。
 *
 * - 周期：每天 / 每周 / 每月 / 每年 循环，配起始日期与时间
 * - 按间隔：每 N 天 / 周 / 月，配起始日期与时间
 * - 单次：只在指定日期 + 时间提醒一次
 *
 * 直接修改入参 [note] 的对应字段，调用方无需处理返回值。
 * 点击「清除」会把所有事件相关字段置空。
 */
object EventEditor {
    /** "周期" 下拉选项 */
    val REPEATS = listOf("每天", "每周", "每月", "每年")
    /** "按间隔" 单位选项（与 [INTERVAL_UNIT_DAYS] 一一对应） */
    val INTERVAL_UNITS = listOf("天", "周", "月")
    val INTERVAL_UNIT_DAYS = listOf(1, 7, 30)

    fun show(context: Context, note: Note, onChanged: (Note) -> Unit = {}) {
        val dp = context.resources.displayMetrics.density
        val now = System.currentTimeMillis()

        // 初始值（拷贝 note 现有设置；旧数据无 eventKind 视为"单次"）
        var kind: String = note.eventKind ?: Note.EVENT_KIND_ONCE
        var repeatIdx: Int = REPEATS.indexOf(note.eventRepeat ?: REPEATS[0]).coerceAtLeast(0)
        // 按间隔：把存的 eventIntervalDays 拆成 (数值, 单位)
        val curInterval = note.eventIntervalDays ?: 1
        var intervalUnitIdx: Int = INTERVAL_UNIT_DAYS.indexOf(curInterval).let { if (it < 0) 0 else it }
        var intervalValue: Int = (curInterval / INTERVAL_UNIT_DAYS[intervalUnitIdx]).coerceAtLeast(1)
        var date: Long = note.eventDate ?: now
        var time: String = note.eventTime ?: "08:00"
        var label: String = note.eventLabel ?: "纪念日"

        val pad = (16 * dp).toInt()
        val rowGap = (6 * dp).toInt()

        fun fmtDate(d: Long): String {
            val c = Calendar.getInstance().apply { timeInMillis = d }
            return "%04d-%02d-%02d".format(
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)
            )
        }

        fun fmtSubtitle(): String = when (kind) {
            Note.EVENT_KIND_CYCLE -> "按周期循环提醒"
            Note.EVENT_KIND_INTERVAL -> "每隔一段时间提醒"
            else -> "只在指定日期提醒一次"
        }

        // ---- 根容器 ----
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
        }

        // ---- 顶部分段 Tab ----
        val seg = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply {
                cornerRadius = 18 * dp
                setColor(0xFFEEEEEE.toInt())
            }
        }
        val tabViews = mutableListOf<TextView>()
        fun refreshTabs() {
            tabViews.forEachIndexed { i, tv ->
                val selected = when (i) {
                    0 -> kind == Note.EVENT_KIND_CYCLE
                    1 -> kind == Note.EVENT_KIND_INTERVAL
                    else -> kind == Note.EVENT_KIND_ONCE
                }
                (tv.background as GradientDrawable).setColor(
                    if (selected) Color.WHITE else Color.TRANSPARENT
                )
                tv.setTextColor(if (selected) 0xFF1A1A1A.toInt() else 0xFF888888.toInt())
            }
        }
        val kindLabels = listOf(Note.EVENT_KIND_CYCLE, Note.EVENT_KIND_INTERVAL, Note.EVENT_KIND_ONCE)
        kindLabels.forEachIndexed { i, k ->
            val tv = TextView(context).apply {
                text = k
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(0, (8 * dp).toInt(), 0, (8 * dp).toInt())
                background = GradientDrawable().apply { cornerRadius = 18 * dp }
                setOnClickListener {
                    kind = k
                    refreshTabs()
                    refreshBody()
                }
            }
            seg.addView(
                tv,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (i == 0) leftMargin = (2 * dp).toInt()
                    if (i == kindLabels.lastIndex) rightMargin = (2 * dp).toInt()
                }
            )
            tabViews += tv
        }
        refreshTabs()
        root.addView(seg)
        root.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (10 * dp).toInt())
        })

        // ---- 副标题 ----
        val tvSub = TextView(context).apply { textSize = 12f; setTextColor(0xFF888888.toInt()) }
        root.addView(tvSub)
        root.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (4 * dp).toInt())
        })

        // ---- 主体容器（每次切 tab 都重新构造） ----
        val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        root.addView(body)

        // 日期按钮 / 时间按钮工厂
        fun mkDateBtn(): TextView = TextView(context).apply {
            text = "📅 ${fmtDate(date)}"
            textSize = 14f
            setPadding((10 * dp).toInt(), (8 * dp).toInt(), (10 * dp).toInt(), (8 * dp).toInt())
            background = GradientDrawable().apply { cornerRadius = 8 * dp; setColor(0xFFF5F5F5.toInt()) }
            setOnClickListener {
                val c = Calendar.getInstance().apply { timeInMillis = date }
                DatePickerDialog(context, { _, y, m, d ->
                    val nc = Calendar.getInstance().apply {
                        set(y, m, d, 0, 0, 0); set(Calendar.MILLISECOND, 0)
                    }
                    date = nc.timeInMillis
                    refreshBody()
                }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
            }
        }
        fun mkTimeBtn(): TextView = TextView(context).apply {
            text = "⏰ $time"
            textSize = 14f
            setPadding((10 * dp).toInt(), (8 * dp).toInt(), (10 * dp).toInt(), (8 * dp).toInt())
            background = GradientDrawable().apply { cornerRadius = 8 * dp; setColor(0xFFF5F5F5.toInt()) }
            setOnClickListener {
                val parts = time.split(":")
                val h = parts.getOrNull(0)?.toIntOrNull() ?: 8
                val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
                TimePickerDialog(context, { _, hh, mm ->
                    time = "%02d:%02d".format(hh, mm)
                    refreshBody()
                }, h, m, true).show()
            }
        }

        fun refreshBody() {
            tvSub.text = fmtSubtitle()
            body.removeAllViews()
            when (kind) {
                Note.EVENT_KIND_CYCLE -> {
                    val sp = Spinner(context).apply {
                        adapter = ArrayAdapter(
                            context, android.R.layout.simple_spinner_dropdown_item, REPEATS
                        )
                        setSelection(repeatIdx)
                        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                                repeatIdx = pos
                            }
                            override fun onNothingSelected(p: AdapterView<*>?) {}
                        }
                    }
                    body.addView(sp)
                }
                Note.EVENT_KIND_INTERVAL -> {
                    val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                    val et = EditText(context).apply {
                        inputType = InputType.TYPE_CLASS_NUMBER
                        setText(intervalValue.toString())
                        textSize = 14f
                        setPadding((10 * dp).toInt(), (8 * dp).toInt(), (10 * dp).toInt(), (8 * dp).toInt())
                        background = GradientDrawable().apply { cornerRadius = 8 * dp; setColor(0xFFF5F5F5.toInt()) }
                        addTextChangedListener(object : TextWatcher {
                            override fun afterTextChanged(s: Editable?) {
                                intervalValue = s.toString().toIntOrNull()?.coerceAtLeast(1) ?: 1
                            }
                            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                        })
                    }
                    val sp = Spinner(context).apply {
                        adapter = ArrayAdapter(
                            context, android.R.layout.simple_spinner_dropdown_item, INTERVAL_UNITS
                        )
                        setSelection(intervalUnitIdx)
                        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                                intervalUnitIdx = pos
                            }
                            override fun onNothingSelected(p: AdapterView<*>?) {}
                        }
                    }
                    row.addView(et, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        rightMargin = rowGap
                    })
                    row.addView(sp)
                    body.addView(row)
                }
                else -> { /* 单次无额外控件 */ }
            }
            // 日期 + 时间 按钮
            val dtRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, (10 * dp).toInt(), 0, 0)
            }
            dtRow.addView(mkDateBtn(), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = rowGap
            })
            dtRow.addView(mkTimeBtn(), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            body.addView(dtRow)
            // 标签
            body.addView(TextView(context).apply {
                text = "标签"
                textSize = 12f
                setTextColor(0xFF888888.toInt())
                setPadding(0, (12 * dp).toInt(), 0, (4 * dp).toInt())
            })
            val etLabel = EditText(context).apply {
                setText(label)
                hint = "纪念日"
                textSize = 14f
                background = GradientDrawable().apply { cornerRadius = 8 * dp; setColor(0xFFF5F5F5.toInt()) }
                setPadding((10 * dp).toInt(), (8 * dp).toInt(), (10 * dp).toInt(), (8 * dp).toInt())
                addTextChangedListener(object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) {
                        label = s?.toString()?.trim() ?: ""
                    }
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                })
            }
            body.addView(etLabel)
        }
        refreshBody()

        // ---- 保存 / 取消 / 清除 ----
        val dialog = AlertDialog.Builder(context)
            .setTitle("设置日期")
            .setView(root)
            .setPositiveButton("保存") { _, _ ->
                note.eventKind = kind
                note.eventDate = date
                note.eventTime = time
                note.eventLabel = label.takeIf { it.isNotBlank() } ?: "纪念日"
                when (kind) {
                    Note.EVENT_KIND_CYCLE -> {
                        note.eventRepeat = REPEATS[repeatIdx]
                        note.eventIntervalDays = null
                    }
                    Note.EVENT_KIND_INTERVAL -> {
                        note.eventRepeat = null
                        note.eventIntervalDays = intervalValue * INTERVAL_UNIT_DAYS[intervalUnitIdx]
                    }
                    else -> {
                        note.eventRepeat = null
                        note.eventIntervalDays = null
                    }
                }
            }
            .setNegativeButton("取消", null)
            .setNeutralButton("清除") { _, _ ->
                note.eventDate = null
                note.eventLabel = null
                note.eventKind = null
                note.eventRepeat = null
                note.eventIntervalDays = null
                note.eventTime = null
            }
            .create()
        // 关闭时把最新状态回传给调用方刷新预览（含取消/返回/清除/保存 所有路径）
        dialog.setOnDismissListener { onChanged(note) }
        dialog.show()
    }
}
