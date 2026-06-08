package com.echomedicine.app.presentation.schedule

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.echomedicine.app.R
import com.echomedicine.app.databinding.FragmentScheduleBinding
import com.echomedicine.app.domain.model.Schedule
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * 스케줄 설정 화면 Fragment.
 *
 * 3개 Slot의 약 이름 및 복용 시간을 설정하고, GET 명령으로 현재 스케줄을 조회한다.
 * 입력 유효성 검증 결과를 인라인 에러로 표시하고, 설정 성공/실패를 Toast/Snackbar로 안내한다.
 *
 * Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8, 4.1, 4.2, 4.3, 4.4
 */
@AndroidEntryPoint
class ScheduleFragment : Fragment() {

    private var _binding: FragmentScheduleBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ScheduleViewModel by viewModels()

    /** 각 Slot별 선택된 시간 (hour, minute) */
    private val selectedTimes = arrayOf(
        intArrayOf(-1, -1),  // Slot 0
        intArrayOf(-1, -1),  // Slot 1
        intArrayOf(-1, -1)   // Slot 2
    )

    /** 캐시 값으로 입력란을 초기 1회만 채웠는지 여부 (사용자 입력 덮어쓰기 방지) */
    private var schedulePrefilled = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScheduleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupClickListeners()
        observeViewModel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupClickListeners() {
        // 조회 버튼
        binding.btnRefreshSchedules.setOnClickListener {
            viewModel.refreshSchedules()
        }

        // Slot 0
        binding.btnTimePicker0.setOnClickListener { showTimePicker(0) }
        binding.btnSave0.setOnClickListener { saveSlot(0) }

        // Slot 1
        binding.btnTimePicker1.setOnClickListener { showTimePicker(1) }
        binding.btnSave1.setOnClickListener { saveSlot(1) }

        // Slot 2
        binding.btnTimePicker2.setOnClickListener { showTimePicker(2) }
        binding.btnSave2.setOnClickListener { saveSlot(2) }
    }

    private fun observeViewModel() {
        // UI 이벤트 관찰
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiEvent.collect { event ->
                    handleUiEvent(event)
                }
            }
        }

        // 로딩 상태 관찰
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isLoading.collect { isLoading ->
                    binding.progressLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
                    setButtonsEnabled(!isLoading)
                }
            }
        }

        // 캐싱된 스케줄 관찰 → UI에 반영
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.schedules.collect { schedules ->
                    updateScheduleUI(schedules)
                }
            }
        }
    }

    /**
     * MaterialTimePicker를 표시한다.
     * Requirement 3.6: 시간 범위는 TimePicker로 제한 (UI 레벨 방지).
     */
    private fun showTimePicker(slotNumber: Int) {
        val currentHour = if (selectedTimes[slotNumber][0] >= 0) selectedTimes[slotNumber][0] else 9
        val currentMinute = if (selectedTimes[slotNumber][1] >= 0) selectedTimes[slotNumber][1] else 0

        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(currentHour)
            .setMinute(currentMinute)
            .setTitleText(getString(R.string.schedule_time_picker_title))
            .build()

        picker.addOnPositiveButtonClickListener {
            selectedTimes[slotNumber][0] = picker.hour
            selectedTimes[slotNumber][1] = picker.minute
            updateTimeDisplay(slotNumber, picker.hour, picker.minute)
        }

        picker.show(parentFragmentManager, "time_picker_$slotNumber")
    }

    /**
     * 선택된 시간을 화면에 표시한다.
     */
    private fun updateTimeDisplay(slotNumber: Int, hour: Int, minute: Int) {
        val timeText = String.format("%02d:%02d", hour, minute)
        when (slotNumber) {
            0 -> {
                binding.tvSelectedTime0.text = timeText
                binding.tvSelectedTime0.visibility = View.VISIBLE
                binding.btnTimePicker0.text = timeText
            }
            1 -> {
                binding.tvSelectedTime1.text = timeText
                binding.tvSelectedTime1.visibility = View.VISIBLE
                binding.btnTimePicker1.text = timeText
            }
            2 -> {
                binding.tvSelectedTime2.text = timeText
                binding.tvSelectedTime2.visibility = View.VISIBLE
                binding.btnTimePicker2.text = timeText
            }
        }
    }

    /**
     * 지정된 Slot을 저장한다.
     */
    private fun saveSlot(slotNumber: Int) {
        clearError(slotNumber)

        val medicineName = getMedicineName(slotNumber)
        val hour = selectedTimes[slotNumber][0]
        val minute = selectedTimes[slotNumber][1]

        // 시간 미선택 검증
        if (hour < 0 || minute < 0) {
            showSlotError(slotNumber, getString(R.string.schedule_error_time_not_selected))
            return
        }

        viewModel.saveSchedule(slotNumber, medicineName, hour, minute)
    }

    /**
     * Slot 번호에 해당하는 약 이름 입력값을 가져온다.
     */
    private fun getMedicineName(slotNumber: Int): String {
        return when (slotNumber) {
            0 -> binding.etMedicineName0.text?.toString() ?: ""
            1 -> binding.etMedicineName1.text?.toString() ?: ""
            2 -> binding.etMedicineName2.text?.toString() ?: ""
            else -> ""
        }
    }

    /**
     * UI 이벤트 처리.
     */
    private fun handleUiEvent(event: ScheduleUiEvent) {
        when (event) {
            is ScheduleUiEvent.SetSuccess -> {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.schedule_set_success, event.slotNumber + 1),
                    Toast.LENGTH_SHORT
                ).show()
            }
            is ScheduleUiEvent.GetSuccess -> {
                // 명시적 조회 성공 시, 다음 캐시 방출에서 입력란을 강제로 갱신하도록 플래그 해제
                schedulePrefilled = false
                Toast.makeText(
                    requireContext(),
                    getString(R.string.schedule_get_success),
                    Toast.LENGTH_SHORT
                ).show()
            }
            is ScheduleUiEvent.ValidationError -> {
                showSlotError(event.slotNumber, event.message)
            }
            is ScheduleUiEvent.NotConnectedError -> {
                Snackbar.make(
                    binding.root,
                    R.string.schedule_error_not_connected,
                    Snackbar.LENGTH_LONG
                ).show()
            }
            is ScheduleUiEvent.TimeoutError -> {
                Snackbar.make(
                    binding.root,
                    R.string.schedule_error_set_timeout,
                    Snackbar.LENGTH_LONG
                ).show()
            }
            is ScheduleUiEvent.GetTimeoutError -> {
                Snackbar.make(
                    binding.root,
                    R.string.schedule_error_get_timeout,
                    Snackbar.LENGTH_LONG
                ).show()
            }
            is ScheduleUiEvent.SendError -> {
                Snackbar.make(
                    binding.root,
                    event.message,
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * TextInputLayout에 인라인 에러를 표시한다.
     */
    private fun showSlotError(slotNumber: Int, message: String) {
        when (slotNumber) {
            0 -> binding.tilMedicineName0.error = message
            1 -> binding.tilMedicineName1.error = message
            2 -> binding.tilMedicineName2.error = message
        }
    }

    /**
     * TextInputLayout의 에러를 초기화한다.
     */
    private fun clearError(slotNumber: Int) {
        when (slotNumber) {
            0 -> binding.tilMedicineName0.error = null
            1 -> binding.tilMedicineName1.error = null
            2 -> binding.tilMedicineName2.error = null
        }
    }

    /**
     * 버튼 활성화/비활성화.
     */
    private fun setButtonsEnabled(enabled: Boolean) {
        binding.btnRefreshSchedules.isEnabled = enabled
        binding.btnSave0.isEnabled = enabled
        binding.btnSave1.isEnabled = enabled
        binding.btnSave2.isEnabled = enabled
        binding.btnTimePicker0.isEnabled = enabled
        binding.btnTimePicker1.isEnabled = enabled
        binding.btnTimePicker2.isEnabled = enabled
    }

    /**
     * 캐싱된 스케줄 데이터를 UI에 반영한다.
     *
     * 자동 캐시 갱신(Flow)으로 인해 사용자가 타이핑 중인 입력이 덮어써지지 않도록,
     * 최초 1회만 입력란을 채운다. 명시적 조회(GET 성공) 시에는 forceUpdate=true로 강제 반영한다.
     */
    private fun updateScheduleUI(schedules: List<Schedule>, forceUpdate: Boolean = false) {
        if (schedules.isEmpty()) return
        // 이미 한 번 채웠고 강제 갱신이 아니면, 사용자 입력 보호를 위해 덮어쓰지 않음
        if (schedulePrefilled && !forceUpdate) return

        for (schedule in schedules) {
            when (schedule.slotNumber) {
                0 -> {
                    binding.etMedicineName0.setText(schedule.medicineName)
                    selectedTimes[0][0] = schedule.hour
                    selectedTimes[0][1] = schedule.minute
                    updateTimeDisplay(0, schedule.hour, schedule.minute)
                }
                1 -> {
                    binding.etMedicineName1.setText(schedule.medicineName)
                    selectedTimes[1][0] = schedule.hour
                    selectedTimes[1][1] = schedule.minute
                    updateTimeDisplay(1, schedule.hour, schedule.minute)
                }
                2 -> {
                    binding.etMedicineName2.setText(schedule.medicineName)
                    selectedTimes[2][0] = schedule.hour
                    selectedTimes[2][1] = schedule.minute
                    updateTimeDisplay(2, schedule.hour, schedule.minute)
                }
            }
        }
        schedulePrefilled = true
    }
}
