package com.workbuddy.notes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 记账页（第 2、3 层）：
 * - 顶部月度汇总卡片（收入/支出/结余 + 月份切换）
 * - 类型 / 账户筛选
 * - 自绘图表：本月支出分类饼图 + 近 6 月收支趋势（见 PieChartView / TrendChartView）
 * - 交易列表（按天分隔，支持删除）
 * - 右下角「＋」记一笔（见 TxnEditor）
 *
 * 颜色约定（红进绿出，贴合支付宝/微信账单主流视觉）：收入=红、支出=绿、转账=灰。
 * 如你想反过来（支出红、收入绿），告诉我即可一行翻转。
 */
class BookkeepingFragment : Fragment() {

    private var selectedYM: Int = ymNow()
    private var typeFilter: Int = -1   // -1 全部 / 0 支出 / 1 收入 / 2 转账
    private var accountFilter: String = ""

    private lateinit var tvMonthLabel: TextView
    private lateinit var tvIncome: TextView
    private lateinit var tvExpense: TextView
    private lateinit var tvBalance: TextView
    private lateinit var spinnerType: Spinner
    private lateinit var spinnerAccount: Spinner
    private lateinit var containerChart: LinearLayout
    private lateinit var listBook: LinearLayout
    private lateinit var fabAdd: Button

    companion object {
        private val CAT_COLORS = mapOf(
            "餐饮" to 0xFFEF5350.toInt(),
            "交通" to 0xFF42A5F5.toInt(),
            "购物" to 0xFFAB47BC.toInt(),
            "居家" to 0xFF26A69A.toInt(),
            "娱乐" to 0xFFF39C12.toInt(),
            "医疗" to 0xFFEC407A.toInt(),
            "教育" to 0xFF5C6BC0.toInt(),
            "工资" to 0xFF66BB6A.toInt(),
            "红包" to 0xFFD4E157.toInt(),
            "其他" to 0xFF78909C.toInt()
        )

        private fun catColor(cat: String): Int = CAT_COLORS[cat] ?: 0xFF90A4AE.toInt()

        fun ymNow(): Int {
            val c = Calendar.getInstance()
            return c.get(Calendar.YEAR) * 100 + (c.get(Calendar.MONTH) + 1)
        }

        fun ymOf(time: Long): Int {
            val c = Calendar.getInstance().apply { timeInMillis = time }
            return c.get(Calendar.YEAR) * 100 + (c.get(Calendar.MONTH) + 1)
        }

        fun addMonth(ym: Int, delta: Int): Int {
            var y = ym / 100
            var m = ym % 100 + delta
            while (m > 12) { m -= 12; y += 1 }
            while (m < 1) { m += 12; y -= 1 }
            return y * 100 + m
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_book, container, false)
        tvMonthLabel = view.findViewById(R.id.tvMonthLabel)
        tvIncome = view.findViewById(R.id.tvIncome)
        tvExpense = view.findViewById(R.id.tvExpense)
        tvBalance = view.findViewById(R.id.tvBalance)
        spinnerType = view.findViewById(R.id.spinnerType)
        spinnerAccount = view.findViewById(R.id.spinnerAccount)
        containerChart = view.findViewById(R.id.containerChart)
        listBook = view.findViewById(R.id.listBook)
        fabAdd = view.findViewById(R.id.fabAdd)

        // 类型筛选
        val typeOpts = arrayOf("全部", "支出", "收入", "转账")
        spinnerType.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, typeOpts)
        (spinnerType.adapter as ArrayAdapter<*>).setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                typeFilter = when (pos) {
                    1 -> Transaction.TYPE_EXPENSE
                    2 -> Transaction.TYPE_INCOME
                    3 -> Transaction.TYPE_TRANSFER
                    else -> -1
                }
                refresh()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        // 账户筛选
        val accOpts = arrayOf("全部账户") + Transaction.ACCOUNTS
        spinnerAccount.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, accOpts)
        (spinnerAccount.adapter as ArrayAdapter<*>).setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerAccount.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                accountFilter = if (pos == 0) "" else Transaction.ACCOUNTS[pos - 1]
                refresh()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        view.findViewById<TextView>(R.id.btnPrevMonth).setOnClickListener {
            selectedYM = addMonth(selectedYM, -1); refresh()
        }
        view.findViewById<TextView>(R.id.btnNextMonth).setOnClickListener {
            selectedYM = addMonth(selectedYM, 1); refresh()
        }
        fabAdd.setOnClickListener { TxnEditor.show(requireContext()) { refresh() } }

        refresh()
        return view
    }

    /** 刷新整个页面（汇总 / 图表 / 列表）。供 Activity.refreshAll 在 onResume 时调用。 */
    fun refresh() {
        view ?: return
        val all = BookkeepingStore.all()
        val monthTxns = all.filter { ymOf(it.time) == selectedYM }

        val income = monthTxns.filter { it.type == Transaction.TYPE_INCOME }.sumOf { it.amount }
        val expense = monthTxns.filter { it.type == Transaction.TYPE_EXPENSE }.sumOf { it.amount }
        val balance = income - expense

        tvMonthLabel.text = "%04d-%02d".format(selectedYM / 100, selectedYM % 100)
        tvIncome.text = fmtYuan(income)
        tvExpense.text = fmtYuan(expense)
        tvBalance.text = fmtYuan(balance)

        buildCharts(all)
        buildList(monthTxns)
    }

    private fun fmtYuan(v: Long): String =
        "¥" + String.format(Locale.US, "%.2f", v / 100.0)

    private fun buildCharts(all: List<Transaction>) {
        val dp = resources.displayMetrics.density
        containerChart.removeAllViews()

        // 饼图：本月支出分类（受账户筛选影响）
        val exp = all.filter {
            it.type == Transaction.TYPE_EXPENSE && ymOf(it.time) == selectedYM &&
                (accountFilter.isBlank() || it.account == accountFilter)
        }
        val byCat = exp.groupBy { it.category.ifBlank { "其他" } }
            .mapValues { it.value.sumOf { t -> t.amount } }
            .toList().sortedByDescending { it.second }
        val pieEntries = byCat.map { (cat, v) -> PieChartView.PieEntry(cat, v, catColor(cat)) }
        containerChart.addView(sectionTitle("本月支出分类"))
        val pie = PieChartView(requireContext(), pieEntries)
        pie.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, (200 * dp).toInt()
        )
        containerChart.addView(pie)

        // 趋势：近 6 月（含当前所选月）
        val trend = mutableListOf<TrendChartView.TrendEntry>()
        for (i in 5 downTo 0) {
            val ym = addMonth(selectedYM, -i)
            val ms = all.filter { ymOf(it.time) == ym }
            val inc = ms.filter { it.type == Transaction.TYPE_INCOME }.sumOf { it.amount }
            val expv = ms.filter { it.type == Transaction.TYPE_EXPENSE }.sumOf { it.amount }
            trend.add(TrendChartView.TrendEntry("%02d月".format(ym % 100), inc, expv))
        }
        containerChart.addView(sectionTitle("近 6 月收支趋势"))
        val trendView = TrendChartView(requireContext(), trend)
        trendView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, (200 * dp).toInt()
        )
        containerChart.addView(trendView)
    }

    private fun sectionTitle(text: String): TextView {
        val dp = resources.displayMetrics.density
        return TextView(requireContext()).apply {
            this.text = text
            textSize = 13f
            setTextColor(0xFF757575.toInt())
            setPadding(0, (10 * dp).toInt(), 0, (4 * dp).toInt())
        }
    }

    private fun buildList(monthTxns: List<Transaction>) {
        val dp = resources.displayMetrics.density
        listBook.removeAllViews()
        val filtered = monthTxns.filter {
            (typeFilter == -1 || it.type == typeFilter) &&
                (accountFilter.isBlank() || it.account == accountFilter)
        }.sortedByDescending { it.time }

        if (filtered.isEmpty()) {
            val empty = TextView(requireContext()).apply {
                text = "本月还没有账单，点右下角 ＋ 记一笔吧"
                setTextColor(0xFF9E9E9E.toInt())
                textSize = 14f
                gravity = android.view.Gravity.CENTER
                setPadding(0, (30 * dp).toInt(), 0, (30 * dp).toInt())
            }
            listBook.addView(empty)
            return
        }

        var lastDay = ""
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        filtered.forEach { t ->
            val day = sdf.format(java.util.Date(t.time))
            if (day != lastDay) {
                lastDay = day
                val sep = TextView(requireContext()).apply {
                    text = day
                    textSize = 12f
                    setTextColor(0xFF9E9E9E.toInt())
                    setPadding(0, (10 * dp).toInt(), 0, (4 * dp).toInt())
                }
                listBook.addView(sep)
            }
            listBook.addView(buildRow(t, dp))
        }
    }

    private fun buildRow(t: Transaction, dp: Float): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding((10 * dp).toInt(), (10 * dp).toInt(), (10 * dp).toInt(), (10 * dp).toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 10 * dp
                setColor(0xFFFFFFFF.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (8 * dp).toInt() }
        }

        val colorFor = when (t.type) {
            Transaction.TYPE_INCOME -> 0xFFC62828.toInt()
            Transaction.TYPE_TRANSFER -> 0xFF757575.toInt()
            else -> 0xFF2E7D32.toInt()
        }

        val icon = TextView(requireContext()).apply {
            text = when (t.type) {
                Transaction.TYPE_INCOME -> "↘"
                Transaction.TYPE_TRANSFER -> "⇄"
                else -> "↗"
            }
            textSize = 20f
            setTextColor(colorFor)
            gravity = android.view.Gravity.CENTER
            width = (36 * dp).toInt()
        }

        val mid = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val title = TextView(requireContext()).apply {
            text = (if (t.type == Transaction.TYPE_TRANSFER) "转账" else t.category.ifBlank { "其他" }) +
                (if (t.auto) "  ·自动" else "")
            textSize = 15f
            setTextColor(0xFF212121.toInt())
        }
        val sub = TextView(requireContext()).apply {
            text = buildString {
                append(t.account)
                if (t.merchant.isNotBlank()) append(" · " + t.merchant)
                if (t.note.isNotBlank()) append(" · " + t.note)
            }
            textSize = 12f
            setTextColor(0xFF757575.toInt())
        }
        mid.addView(title)
        mid.addView(sub)

        val amt = TextView(requireContext()).apply {
            text = when (t.type) {
                Transaction.TYPE_INCOME -> "+" + fmtYuan(t.amount)
                else -> fmtYuan(t.amount)
            }
            textSize = 16f
            setTextColor(colorFor)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.END
        }

        val del = TextView(requireContext()).apply {
            text = "删除"
            textSize = 13f
            setTextColor(0xFFB71C1C.toInt())
            setPadding((10 * dp).toInt(), 0, 0, 0)
            gravity = android.view.Gravity.CENTER
        }
        del.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("删除这笔账单")
                .setMessage("确定删除吗？自动捕获的误抓项可在此移除。")
                .setPositiveButton("删除") { _, _ ->
                    BookkeepingStore.remove(t.id)
                    refresh()
                }
                .setNegativeButton("取消", null)
                .show()
        }

        row.addView(icon)
        row.addView(mid)
        row.addView(amt)
        row.addView(del)
        return row
    }
}
