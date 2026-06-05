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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 날짜별 복용 이력 상세 화면 Fragment.
 *
 * 선택된 날짜의 상세 복용 기록(약 이름, 상태, 예정 시간, 실제 복용 시간)을
 * Slot 순서대로 표시한다. 기록이 없으면 안내 메시지를 표시한다.
 *
 * Requirements: 6.5, 6.6
 */
@AndroidEntryPoint
class HistoryDetailFragment : Fragment() {

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

        historyAdapter = HistoryAdapter()
        binding.rvHistoryRecords.adapter = historyAdapter

        // Navigation SafeArgs에서 date 인자를 받아 ViewModel에 전달
        val dateArg = arguments?.getLong("date") ?: System.currentTimeMillis()
        viewModel.selectDate(dateArg)

        // 날짜 선택 버튼 숨김 (이미 날짜가 전달됨)
        binding.btnSelectDate.visibility = View.GONE

        observeState()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.selectedDate.collect { dateMillis ->
                        binding.tvSelectedDate.text = dateFormat.format(Date(dateMillis))
                    }
                }
                launch {
                    viewModel.selectedDateHistory.collect { records ->
                        historyAdapter.submitList(records)
                    }
                }
                launch {
                    viewModel.isEmpty.collect { empty ->
                        binding.tvEmptyMessage.visibility = if (empty) View.VISIBLE else View.GONE
                        binding.rvHistoryRecords.visibility = if (empty) View.GONE else View.VISIBLE
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
