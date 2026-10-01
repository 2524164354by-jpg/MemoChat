package com.memochat.app.ui.filter

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.memochat.app.R
import com.memochat.app.data.AppDatabase
import com.memochat.app.data.Entry
import com.memochat.app.databinding.FragmentFilterBinding
import com.memochat.app.ui.chat.ChatActivity
import com.memochat.app.util.WeatherMood
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FilterFragment : Fragment() {

    private var _binding: FragmentFilterBinding? = null
    private val binding get() = _binding!!
    private val adapter = FilterEntryAdapter()

    private var activeDim: String? = null   // "date"/"mood"/"weather"
    private var keyword = ""
    private var filterDate = ""
    private var filterMood: String? = null
    private var filterWeather: String? = null
    private var searchJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFilterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter
        adapter.onEntryClick = { e -> ChatActivity.start(requireContext(), e.id) }

        binding.search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                keyword = s?.toString()?.trim() ?: ""
                debounceSearch()
            }
        })
        binding.dateInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterDate = s?.toString()?.trim() ?: ""
                debounceSearch()
            }
        })

        setupChips()
        reload()
    }

    private fun setupChips() {
        val date = binding.chipDate
        val mood = binding.chipMood
        val weather = binding.chipWeather
        date.setOnClickListener { toggleDim("date") }
        mood.setOnClickListener { toggleDim("mood") }
        weather.setOnClickListener { toggleDim("weather") }
    }

    private fun toggleDim(dim: String) {
        activeDim = if (activeDim == dim) null else dim
        renderValueArea()
        updateChipUi()
        reload()
    }

    private fun updateChipUi() {
        listOf(
            Pair(binding.chipDate, "date"),
            Pair(binding.chipMood, "mood"),
            Pair(binding.chipWeather, "weather")
        ).forEach { (chip, dim) ->
            chip.isSelected = activeDim == dim
            chip.setTextColor(
                if (chip.isSelected) Color.WHITE
                else requireContext().getColor(R.color.text_primary)
            )
        }
    }

    private fun renderValueArea() {
        val area = binding.valueArea
        val optionRow = binding.optionRow
        val dateInput = binding.dateInput
        optionRow.removeAllViews()
        when (activeDim) {
            "date" -> {
                area.visibility = View.VISIBLE
                dateInput.visibility = View.VISIBLE
                optionRow.visibility = View.GONE
            }
            "mood" -> {
                area.visibility = View.VISIBLE
                dateInput.visibility = View.GONE
                optionRow.visibility = View.VISIBLE
                buildOptionChips(optionRow, WeatherMood.moodOptions, filterMood) {
                    filterMood = it; reload()
                }
            }
            "weather" -> {
                area.visibility = View.VISIBLE
                dateInput.visibility = View.GONE
                optionRow.visibility = View.VISIBLE
                buildOptionChips(optionRow, WeatherMood.weatherOptions, filterWeather) {
                    filterWeather = it; reload()
                }
            }
            else -> {
                area.visibility = View.GONE
                filterMood = null
                filterWeather = null
                filterDate = ""
                dateInput.setText("")
            }
        }
    }

    private fun buildOptionChips(
        row: LinearLayout, options: List<String>, current: String?, onPick: (String) -> Unit
    ) {
        options.forEach { opt ->
            val chip = TextView(requireContext()).apply {
                text = opt
                textSize = 13f
                setTextColor(requireContext().getColor(R.color.text_primary))
                background = requireContext().getDrawable(R.drawable.bg_chip)
                setPadding(dp(16), dp(6), dp(16), dp(6))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(8) }
                isSelected = opt == current
                if (opt == current) setTextColor(Color.WHITE)
                isClickable = true
                setOnClickListener {
                    val wasSelected = opt == current
                    if (wasSelected) {
                        if (opt == filterMood) filterMood = null
                        if (opt == filterWeather) filterWeather = null
                    } else {
                        onPick(opt)
                    }
                    renderValueArea()
                    updateChipUi()
                    reload()
                }
            }
            row.addView(chip)
        }
    }

    private fun debounceSearch() {
        searchJob?.cancel()
        searchJob = CoroutineScope(Dispatchers.Main).launch {
            delay(200)
            reload()
        }
    }

    fun reload() {
        val db = AppDatabase.get(requireContext())
        CoroutineScope(Dispatchers.IO).launch {
            val all = db.entryDao().getAll()
            val messages = db.messageDao().getAll()
            val contentMap = messages.groupBy { it.entryId }
                .mapValues { (_, list) -> list.joinToString(" ") { it.content } }
            val filtered = all.filter { e ->
                matches(e, contentMap[e.id].orEmpty())
            }
            val previews = mutableMapOf<Long, String>()
            for (e in filtered) {
                previews[e.id] = db.messageDao().lastContent(e.id) ?: ""
            }
            withContext(Dispatchers.Main) {
                adapter.submit(filtered, previews)
                binding.emptyView.visibility =
                    if (filtered.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun matches(e: Entry, content: String): Boolean {
        val kw = keyword
        if (kw.isNotEmpty()) {
            val hay = listOf(e.title, e.date, e.weather, e.mood, content)
                .joinToString(" ").lowercase()
            if (!hay.contains(kw.lowercase())) return false
        }
        if (filterMood != null && e.mood != filterMood) return false
        if (filterWeather != null && e.weather != filterWeather) return false
        if (filterDate.isNotEmpty() && !e.date.contains(filterDate)) return false
        return true
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onResume() {
        super.onResume()
        reload()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
