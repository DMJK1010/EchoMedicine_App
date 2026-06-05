package com.echomedicine.app.presentation.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.echomedicine.app.R
import com.echomedicine.app.databinding.FragmentHistoryBinding
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 복용 이력 화면 Fragment.
 *
 * 최근 7일 복용률, 날짜 선택(MaterialDatePicker), 선택된 날짜의 상세 기록을 표시한다.
 * 선택한 날짜에 기록이 없으면 안내 메시지를 표시한다.
 */
@AndroidEntryPoint
class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HistoryViewModel by viewModels()
    private lateinit var historyAdapter: HistoryAdapter

    private val dateFormat = SimpleDateFormat("yyyy년 MM월 dd일", Locale.KOREAN)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupDateSelector()
        observeState()
    }

    private fun setupRecyclerView() {
        historyAdapter = HistoryAdapter()
        binding.rvHistoryRecords.adapter = historyAdapter
    }

    private fun setupDateSelector() {
        binding.btnSelectDate.setOnClickListener {
            showDatePicker()
        }
    }

    private fun showDatePicker() {
        val constraintsBuilder = CalendarConstraints.Builder()
            .setValidator(DateValidatorPointBackward.now())
            .setStart(ninetyDaysAgoMillis())
            .setEnd(MaterialDatePicker.todayInUtcMilliseconds())

        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(getString(R.string.history_date_picker_title))
            .setSelection(viewModel.selectedDate.value)
            .setCalendarConstraints(constraintsBuilder.build())
            .build()

        datePicker.addOnPositiveButtonClickListener { selection ->
            viewModel.selectDate(selection)
        }

        datePicker.show(parentFragmentManager, DATE_PICKER_TAG)
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { observeSelectedDate() }
                launch { observeSelectedDateHistory() }
                launch { observeEmptyState() }
                launch { observeTakenRate() }
            }
        }
    }

    private suspend fun observeSelectedDate() {
        viewModel.selectedDate.collect { dateMillis ->
            binding.tvSelectedDate.text = dateFormat.format(Date(dateMillis))
        }
    }

    private suspend fun observeSelectedDateHistory() {
        viewModel.selectedDateHistory.collect { records ->
            historyAdapter.submitList(records)
        }
    }

    private suspend fun observeEmptyState() {
        viewModel.isEmpty.collect { empty ->
            binding.tvEmptyMessage.visibility = if (empty) View.VISIBLE else View.GONE
            binding.rvHistoryRecords.visibility = if (empty) View.GONE else View.VISIBLE
        }
    }

    private suspend fun observeTakenRate() {
        viewModel.takenRate.collect { rate ->
            binding.tvTakenRate.text = getString(R.string.taken_rate_value, rate.toInt())
        }
    }

    private fun ninetyDaysAgoMillis(): Long {
        val calendar = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -90)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val DATE_PICKER_TAG = "history_date_picker"
    }
}
