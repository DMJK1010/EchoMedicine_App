package com.echomedicine.app.presentation.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.echomedicine.app.R
import com.echomedicine.app.databinding.ItemHistoryRecordBinding
import com.echomedicine.app.domain.model.HistoryRecord
import com.echomedicine.app.domain.model.SlotStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 복용 이력 목록의 RecyclerView Adapter.
 *
 * 각 HistoryRecord를 카드 형태로 표시하며,
 * 약 이름, 상태 아이콘, 예정 시간, 실제 복용 시간을 보여준다.
 */
class HistoryAdapter : ListAdapter<HistoryRecord, HistoryAdapter.HistoryViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemHistoryRecordBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class HistoryViewHolder(
        private val binding: ItemHistoryRecordBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        fun bind(record: HistoryRecord) {
            val context = binding.root.context

            // 약 이름
            binding.tvMedicineName.text = record.medicineName

            // 칸 번호
            binding.tvSlotNumber.text = context.getString(R.string.slot_number_label, record.slotNumber + 1)

            // 예정 시간
            binding.tvScheduledTime.text = context.getString(
                R.string.history_scheduled_time,
                String.format(Locale.getDefault(), "%02d:%02d", record.scheduledHour, record.scheduledMinute)
            )

            // 실제 복용 시간
            if (record.actualTakenTime != null) {
                binding.tvActualTime.visibility = View.VISIBLE
                binding.tvActualTime.text = context.getString(
                    R.string.history_actual_time,
                    timeFormat.format(Date(record.actualTakenTime))
                )
            } else {
                binding.tvActualTime.visibility = View.GONE
            }

            // 상태 아이콘 및 텍스트
            when (record.status) {
                SlotStatus.TAKEN -> {
                    binding.ivStatusIcon.setImageResource(R.drawable.ic_slot_taken)
                    binding.ivStatusIcon.setColorFilter(
                        ContextCompat.getColor(context, R.color.slot_status_taken)
                    )
                    binding.tvStatusText.text = context.getString(R.string.status_taken)
                    binding.tvStatusText.setTextColor(
                        ContextCompat.getColor(context, R.color.slot_status_taken)
                    )
                }
                SlotStatus.MISSED -> {
                    binding.ivStatusIcon.setImageResource(R.drawable.ic_slot_missed)
                    binding.ivStatusIcon.setColorFilter(
                        ContextCompat.getColor(context, R.color.slot_status_missed)
                    )
                    binding.tvStatusText.text = context.getString(R.string.status_missed)
                    binding.tvStatusText.setTextColor(
                        ContextCompat.getColor(context, R.color.slot_status_missed)
                    )
                }
                else -> {
                    binding.ivStatusIcon.setImageResource(R.drawable.ic_slot_waiting)
                    binding.ivStatusIcon.setColorFilter(
                        ContextCompat.getColor(context, R.color.slot_status_waiting)
                    )
                    binding.tvStatusText.text = context.getString(R.string.status_waiting)
                    binding.tvStatusText.setTextColor(
                        ContextCompat.getColor(context, R.color.slot_status_waiting)
                    )
                }
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<HistoryRecord>() {
        override fun areItemsTheSame(oldItem: HistoryRecord, newItem: HistoryRecord): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: HistoryRecord, newItem: HistoryRecord): Boolean {
            return oldItem == newItem
        }
    }
}
