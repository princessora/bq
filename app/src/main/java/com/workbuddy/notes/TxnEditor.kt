package com.workbuddy.notes

import android.app.DatePickerDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import java.math.BigDecimal
import java.util.Calendar

/**
 * 手动记账录入弹窗。
 *
 * 金额以「元」输入，用 BigDecimal 转「分」(Long) 落库，杜绝浮点累加误差。
 * 类型用分段 Tab（支出/收入/转账）；转账不要求分类（置空）。
 * 保存校验金额有效性，无效时弹 Toast 且不关闭对话框，避免误丢已填内容。
 */
object TxnEditor {

    fun show(context: Context, onSaved: () -> Unit) {
        val dp = context.resources.displayMetrics.density
        var type = Transaction.TYPE_EXPENSE
        var category = Transaction.CATEGORIES[0]
        var account = Transaction.ACC_ALIPAY
        var date = System.currentTimeMillis()
        var note = ""

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), (8 * dp).toInt(), (16 * dp).toInt(), 0)
        }

        // 金额
        val amountEdit = android.widget.EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "金额（元）"
            textSize = 22f
            background = GradientDrawable().apply { cornerRadius = 8 * dp; setColor(0xFFF5F5F5.toInt()) }
            setPadding((12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt())
        }
        root.addView(amountEdit)

        // 分类（先声明，供后面分段 Tab 的回调引用）
        val spCat = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, Transaction.CATEGORIES)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    category = Transaction.CATEGORIES[pos]
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        }
        // 账户
        val spAcc = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, Transaction.ACCOUNTS)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    account = Transaction.ACCOUNTS[pos]
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        }
        // 日期
        val btnDate = TextView(context).apply {
            text = "📅 " + fmtDate(date)
            textSize = 14f
            setPadding((10 * dp).toInt(), (10 * dp).toInt(), (10 * dp).toInt(), (10 * dp).toInt())
            background = GradientDrawable().apply { cornerRadius = 8 * dp; setColor(0xFFF5F5F5.toInt()) }
            setOnClickListener {
                val c = Calendar.getInstance().apply { timeInMillis = date }
                DatePickerDialog(context, { _, y, m, d ->
                    val nc = Calendar.getInstance().apply {
                        set(y, m, d, 12, 0, 0); set(Calendar.MILLISECOND, 0)
                    }
                    date = nc.timeInMillis
                    text = "📅 " + fmtDate(date)
                }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
            }
        }
        // 备注
        val etNote = android.widget.EditText(context).apply {
            hint = "备注（可选）"
            textSize = 14f
            background = GradientDrawable().apply { cornerRadius = 8 * dp; setColor(0xFFF5F5F5.toInt()) }
            setPadding((12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt())
        }

        // 类型分段 Tab
        val seg = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, (12 * dp).toInt(), 0, 0)
            background = GradientDrawable().apply { cornerRadius = 18 * dp; setColor(0xFFEEEEEE.toInt()) }
        }
        val kinds = listOf(
            Transaction.TYPE_EXPENSE to "支出",
            Transaction.TYPE_INCOME to "收入",
            Transaction.TYPE_TRANSFER to "转账"
        )
        val tabViews = mutableListOf<TextView>()
        val updateTabs = {
            tabViews.forEachIndexed { i, tv ->
                val sel = kinds[i].first == type
                (tv.background as GradientDrawable).setColor(if (sel) Color.WHITE else Color.TRANSPARENT)
                tv.setTextColor(if (sel) 0xFF1A1A1A.toInt() else 0xFF888888.toInt())
            }
        }
        kinds.forEach { (t, label) ->
            val tv = TextView(context).apply {
                text = label
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(0, (8 * dp).toInt(), 0, (8 * dp).toInt())
                background = GradientDrawable().apply { cornerRadius = 18 * dp }
                setOnClickListener {
                    type = t
                    updateTabs()
                    spCat.isEnabled = (type != Transaction.TYPE_TRANSFER)
                }
            }
            seg.addView(
                tv,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    leftMargin = (2 * dp).toInt(); rightMargin = (2 * dp).toInt()
                }
            )
            tabViews += tv
        }

        // 标签 + 控件 顺序加入
        root.addView(seg)
        root.addView(labelView(context, "分类", dp))
        root.addView(spCat)
        root.addView(labelView(context, "账户", dp))
        root.addView(spAcc)
        root.addView(labelView(context, "日期", dp))
        root.addView(btnDate)
        root.addView(labelView(context, "备注", dp))
        root.addView(etNote)
        updateTabs()

        val dialog = AlertDialog.Builder(context)
            .setTitle("记一笔")
            .setView(root)
            .setPositiveButton("保存", null)
            .setNegativeButton("取消", null)
            .create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val amtStr = amountEdit.text.toString().trim()
            val amount = try {
                BigDecimal(amtStr).multiply(BigDecimal(100)).toLong()
            } catch (_: Exception) {
                0L
            }
            if (amount <= 0) {
                android.widget.Toast.makeText(context, "请输入有效金额", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val t = Transaction(
                amount = amount,
                type = type,
                category = if (type == Transaction.TYPE_TRANSFER) "" else category,
                account = account,
                note = etNote.text.toString().trim(),
                time = date,
                source = Transaction.SRC_MANUAL,
                auto = false
            )
            BookkeepingStore.add(t)
            dialog.dismiss()
            onSaved()
        }
    }

    private fun labelView(context: Context, text: String, dp: Float): TextView {
        return TextView(context).apply {
            this.text = text
            textSize = 12f
            setTextColor(0xFF888888.toInt())
            setPadding(0, (12 * dp).toInt(), 0, (4 * dp).toInt())
        }
    }

    private fun fmtDate(d: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = d }
        return "%04d-%02d-%02d".format(
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)
        )
    }
}
