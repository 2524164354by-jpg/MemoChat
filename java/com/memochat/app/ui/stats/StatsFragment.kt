package com.memochat.app.ui.stats

import android.app.DatePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.memochat.app.R
import com.memochat.app.data.AppDatabase
import com.memochat.app.databinding.FragmentStatsBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class StatsFragment : Fragment() {

    private var _binding: FragmentStatsBinding? = null
    private val binding get() = _binding!!
    private var rangeDays = 30
    private var customStart: Long = 0
    private var customEnd: Long = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        buildRangeChips()
    }

    private fun buildRangeChips() {
        val options = listOf("近7天" to 7, "近30天" to 30, "近90天" to 90, "全部" to 0, "自定义" to -1)
        binding.rangeRow.removeAllViews()
        for ((label, days) in options) {
            val chip = TextView(requireContext()).apply {
                text = label
                textSize = 13f
                setTextColor(if (rangeDays == days) Color.WHITE else ContextCompat.getColor(requireContext(), R.color.text_primary))
                background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_chip)
                setPadding(dp(14), dp(6), dp(14), dp(6))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(8) }
                isClickable = true
                setOnClickListener {
                    if (days == -1) pickCustomRange()
                    else { rangeDays = days; buildRangeChips(); loadStats() }
                }
            }
            binding.rangeRow.addView(chip)
        }
    }

    private fun pickCustomRange() {
        val cal = Calendar.getInstance()
        DatePickerDialog(requireContext(), { _, y, m, d ->
            customStart = Calendar.getInstance().apply { set(y, m, d, 0, 0, 0) }.timeInMillis
            DatePickerDialog(requireContext(), { _, y2, m2, d2 ->
                customEnd = Calendar.getInstance().apply { set(y2, m2, d2, 23, 59, 59) }.timeInMillis
                rangeDays = -1; buildRangeChips(); loadStats()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    override fun onResume() { super.onResume(); loadStats() }

    private fun moodScore(m: String): Float = when (m) {
        "开心" -> 2f; "平静" -> 1f; "疲惫" -> 0f
        "难过" -> -1f; "生气" -> -2f; "兴奋" -> 2f
        else -> 0f
    }
    private fun moodColor(m: String): Int = when (m) {
        "开心" -> Color.parseColor("#07C160"); "平静" -> Color.parseColor("#3B82F6")
        "疲惫" -> Color.parseColor("#9E9E9E"); "难过" -> Color.parseColor("#8B5CF6")
        "兴奋" -> Color.parseColor("#F97316"); "生气" -> Color.parseColor("#EF4444")
        else -> Color.parseColor("#999999")
    }

    private fun loadStats() {
        try {
            val db = AppDatabase.get(requireContext())
            CoroutineScope(Dispatchers.IO).launch {
                val allMsgs = db.messageDao().getAll()
                val allDays = db.dayMetaDao().all()
                val allEntries = db.entryDao().getAll()

                val (start, end) = when {
                    rangeDays > 0 -> System.currentTimeMillis() - rangeDays * 86400_000L to System.currentTimeMillis()
                    rangeDays == -1 -> customStart to customEnd
                    else -> 0L to System.currentTimeMillis()
                }
                val msgs = allMsgs.filter { it.createdAt in start..end }
                val daysInRange = allDays.filter { it.dayKey >= formatDay(start) && it.dayKey <= formatDay(end) }

                // active days = days that have at least 1 message
                val daySet = msgs.map { dayKey(it.createdAt) }.toSet()
                val activeDays = daySet.size

                // streak: consecutive days up to today
                var streak = 0
                val cal = Calendar.getInstance()
                while (true) {
                    val key = dayKey(cal.timeInMillis)
                    if (daySet.contains(key)) { streak++; cal.add(Calendar.DAY_OF_YEAR, -1) }
                    else break
                }

                // mood chart points (from dayMeta)
                val fmt = SimpleDateFormat("MM/dd", Locale.getDefault())
                val sortedDays = daysInRange.sortedBy { it.dayKey }
                val pts = sortedDays.map {
                    MoodLineChartView.Point(fmt.format(parseDay(it.dayKey)), moodScore(it.mood), moodColor(it.mood), it.mood)
                }

                // type distribution
                val typeCount = allEntries.filter { it.createdAt in start..end }
                    .groupingBy { it.type }.eachCount()

                // weather distribution
                val weatherCount = daysInRange.groupingBy { it.weather }.eachCount()
                    .filterKeys { it.isNotBlank() }

                withContext(Dispatchers.Main) {
                    binding.statMessages.text = msgs.size.toString()
                    binding.statDays.text = activeDays.toString()
                    binding.statStreak.text = streak.toString()
                    binding.moodChart.points = pts
                    renderDist(binding.typeContainer, typeCount)
                    renderDist(binding.weatherContainer, weatherCount)
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun dayKey(ts: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(ts))
    private fun formatDay(ts: Long): String = dayKey(ts)
    private fun parseDay(k: String): Date =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(k) ?: Date()

    private fun renderDist(container: LinearLayout, counts: Map<String, Int>) {
        container.removeAllViews()
        if (counts.isEmpty()) {
            container.addView(TextView(requireContext()).apply {
                text = "暂无数据"; setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary)); textSize = 13f
            })
            return
        }
        val maxCount = counts.values.maxOrNull() ?: 1
        for ((k, v) in counts) {
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(8) }
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            val label = TextView(requireContext()).apply {
                text = k; textSize = 13f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                layoutParams = LinearLayout.LayoutParams(dp(56), LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            val bar = ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100; progressDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.bg_stat_bar)
                progress = v * 100 / maxCount
                layoutParams = LinearLayout.LayoutParams(0, dp(10), 1f).apply { marginStart = dp(8) }
            }
            val num = TextView(requireContext()).apply {
                text = v.toString(); textSize = 13f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = dp(8) }
            }
            row.addView(label); row.addView(bar); row.addView(num)
            container.addView(row)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
