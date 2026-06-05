package com.echomedicine.app.presentation.connection

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.echomedicine.app.databinding.ItemPairedDeviceBinding

/**
 * 페어링된 블루투스 기기 목록을 표시하는 RecyclerView Adapter.
 *
 * @param onDeviceClick 기기 선택(연결) 콜백
 */
class PairedDeviceAdapter(
    private val onDeviceClick: (BluetoothDevice) -> Unit
) : ListAdapter<BluetoothDevice, PairedDeviceAdapter.DeviceViewHolder>(DeviceDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemPairedDeviceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class DeviceViewHolder(
        private val binding: ItemPairedDeviceBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("MissingPermission")
        fun bind(device: BluetoothDevice) {
            binding.tvDeviceName.text = device.name ?: "알 수 없는 기기"
            binding.tvDeviceAddress.text = device.address
            binding.btnConnect.setOnClickListener {
                onDeviceClick(device)
            }
            binding.root.setOnClickListener {
                onDeviceClick(device)
            }
        }
    }

    private object DeviceDiffCallback : DiffUtil.ItemCallback<BluetoothDevice>() {
        override fun areItemsTheSame(oldItem: BluetoothDevice, newItem: BluetoothDevice): Boolean {
            return oldItem.address == newItem.address
        }

        @SuppressLint("DiffUtilEquals")
        override fun areContentsTheSame(oldItem: BluetoothDevice, newItem: BluetoothDevice): Boolean {
            return oldItem.address == newItem.address
        }
    }
}
