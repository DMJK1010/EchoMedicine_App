package com.echomedicine.app.presentation.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.echomedicine.app.R
import com.echomedicine.app.databinding.FragmentDashboardBinding
import com.echomedicine.app.databinding.ItemSlotCardBinding
import com.echomedicine.app.domain.model.ConnectionState
import com.echomedicine.app.domain.model.MedicineSlot
import com.echomedicine.app.domain.model.SlotStatus
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * 대시보드 화면 Fragment.
 *
 * 3개 Slot의 현재 상태, 블루투스 연결 상태, 최근 7일 복용률을 표시한다.
 */
@AndroidEntryPoint
class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        observeState()
    }

    private fun setupToolbar() {
        binding.toolbar.inflateMenu(R.menu.menu_dashboard)
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_connection_status -> {
                    navigateToConnection()
                    true
                }
                else -> false
            }
        }
    }

    private fun navigateToConnection() {
        findNavController().navigate(R.id.action_dashboard_to_connection)
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { observeSlots() }
                launch { observeConnectionState() }
                launch { observeTakenRate() }
            }
        }
    }

    private suspend fun observeSlots() {
        viewModel.slots.collect { slots ->
            val cardBindings = listOf(
                ItemSlotCardBinding.bind(binding.slotCard0.root),
                ItemSlotCardBinding.bind(binding.slotCard1.root),
                ItemSlotCardBinding.bind(binding.slotCard2.root)
            )
            slots.forEachIndexed { index, slot ->
                if (index < cardBindings.size) {
                    bindSlotCard(cardBindings[index], slot)
                }
            }
            
            // For testing AI button visibility if slots are empty
            if (slots.all { it.status == SlotStatus.EMPTY }) {
                cardBindings.forEachIndexed { index, binding ->
                    binding.btnDetect.visibility = View.VISIBLE
                    binding.btnDetect.setOnClickListener {
                        val action = DashboardFragmentDirections.actionDashboardToAiDetection(index)
                        findNavController().navigate(action)
                    }
                }
            }
        }
    }

    private suspend fun observeConnectionState() {
        viewModel.connectionState.collect { state ->
            updateConnectionIcon(state)
        }
    }

    private suspend fun observeTakenRate() {
        viewModel.takenRate.collect { rate ->
            binding.tvTakenRate.text = getString(R.string.taken_rate_value, rate.toInt())
        }
    }

    private fun bindSlotCard(cardBinding: ItemSlotCardBinding, slot: MedicineSlot) {
        cardBinding.tvSlotNumber.text = getString(R.string.slot_number_label, slot.slotNumber + 1)

        cardBinding.btnDetect.setOnClickListener {
            val action = DashboardFragmentDirections.actionDashboardToAiDetection(slot.slotNumber)
            findNavController().navigate(action)
        }

        when (slot.status) {
            SlotStatus.EMPTY -> {
                cardBinding.tvMedicineName.text = getString(R.string.slot_empty)
                cardBinding.tvScheduleTime.visibility = View.GONE
                cardBinding.ivStatusIcon.setImageResource(R.drawable.ic_slot_empty)
                cardBinding.ivStatusIcon.setColorFilter(
                    ContextCompat.getColor(requireContext(), R.color.slot_status_empty)
                )
                cardBinding.tvStatusText.text = getString(R.string.status_empty)
                cardBinding.tvStatusText.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.slot_status_empty)
                )
                cardBinding.tvEmptyHint.visibility = View.VISIBLE
                cardBinding.btnDetect.visibility = View.GONE
            }
            SlotStatus.WAITING -> {
                cardBinding.tvMedicineName.text = slot.medicineName
                cardBinding.tvScheduleTime.visibility = View.VISIBLE
                cardBinding.tvScheduleTime.text = formatTime(slot.hour, slot.minute)
                cardBinding.ivStatusIcon.setImageResource(R.drawable.ic_slot_waiting)
                cardBinding.ivStatusIcon.setColorFilter(
                    ContextCompat.getColor(requireContext(), R.color.slot_status_waiting)
                )
                cardBinding.tvStatusText.text = getString(R.string.status_waiting)
                cardBinding.tvStatusText.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.slot_status_waiting)
                )
                cardBinding.tvEmptyHint.visibility = View.GONE
                cardBinding.btnDetect.visibility = View.VISIBLE
            }
            SlotStatus.TAKEN -> {
                cardBinding.tvMedicineName.text = slot.medicineName
                cardBinding.tvScheduleTime.visibility = View.VISIBLE
                cardBinding.tvScheduleTime.text = formatTime(slot.hour, slot.minute)
                cardBinding.ivStatusIcon.setImageResource(R.drawable.ic_slot_taken)
                cardBinding.ivStatusIcon.setColorFilter(
                    ContextCompat.getColor(requireContext(), R.color.slot_status_taken)
                )
                cardBinding.tvStatusText.text = getString(R.string.status_taken)
                cardBinding.tvStatusText.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.slot_status_taken)
                )
                cardBinding.tvEmptyHint.visibility = View.GONE
                cardBinding.btnDetect.visibility = View.GONE
            }
            SlotStatus.MISSED -> {
                cardBinding.tvMedicineName.text = slot.medicineName
                cardBinding.tvScheduleTime.visibility = View.VISIBLE
                cardBinding.tvScheduleTime.text = formatTime(slot.hour, slot.minute)
                cardBinding.ivStatusIcon.setImageResource(R.drawable.ic_slot_missed)
                cardBinding.ivStatusIcon.setColorFilter(
                    ContextCompat.getColor(requireContext(), R.color.slot_status_missed)
                )
                cardBinding.tvStatusText.text = getString(R.string.status_missed)
                cardBinding.tvStatusText.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.slot_status_missed)
                )
                cardBinding.tvEmptyHint.visibility = View.GONE
                cardBinding.btnDetect.visibility = View.VISIBLE
            }
        }
    }

    private fun updateConnectionIcon(state: ConnectionState) {
        val (iconRes, contentDesc) = when (state) {
            is ConnectionState.Connected -> R.drawable.ic_bluetooth_connected to getString(R.string.status_connected)
            is ConnectionState.Connecting -> R.drawable.ic_bluetooth_connecting to getString(R.string.status_connecting)
            is ConnectionState.Reconnecting -> R.drawable.ic_bluetooth_connecting to getString(R.string.status_reconnecting, state.attempt, state.maxAttempts)
            is ConnectionState.Disconnected -> R.drawable.ic_bluetooth_disconnected to getString(R.string.status_disconnected)
        }

        binding.toolbar.menu.findItem(R.id.action_connection_status)?.let { menuItem ->
            menuItem.setIcon(iconRes)
            menuItem.title = contentDesc
        }
    }

    private fun formatTime(hour: Int, minute: Int): String {
        return String.format("%02d:%02d", hour, minute)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
