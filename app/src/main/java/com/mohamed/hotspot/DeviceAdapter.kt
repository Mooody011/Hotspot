package com.mohamed.hotspot

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DeviceAdapter(
    private var items: List<DeviceInfo>,
    private val listener: Listener
) : RecyclerView.Adapter<DeviceAdapter.Holder>() {

    interface Listener {
        fun onLimitChanged(mac: String, minutes: Int)
        fun onBlockChanged(mac: String, blocked: Boolean)
        fun onRename(mac: String)
        fun onResetTimer(mac: String)
    }

    fun update(newItems: List<DeviceInfo>) {
        items = newItems
        notifyDataSetChanged()
    }

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val tvLabel: TextView = v.findViewById(R.id.tvDeviceLabel)
        val tvMac: TextView = v.findViewById(R.id.tvDeviceMac)
        val tvIp: TextView = v.findViewById(R.id.tvDeviceIp)
        val tvRemaining: TextView = v.findViewById(R.id.tvDeviceRemaining)
        val tvBlockedNote: TextView = v.findViewById(R.id.tvBlockedNote)
        val spLimit: Spinner = v.findViewById(R.id.spDeviceLimit)
        val swBlock: Switch = v.findViewById(R.id.swBlockDevice)
        val btnRename: Button = v.findViewById(R.id.btnRenameDevice)
        val btnReset: Button = v.findViewById(R.id.btnResetTimer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device, parent, false)
        return Holder(v)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val device = items[position]
        val ctx = holder.itemView.context
        holder.tvLabel.text = device.label
        holder.tvMac.text = "MAC: ${device.mac}"
        holder.tvIp.text = "IP: ${device.ip}"

        val now = System.currentTimeMillis()
        val remaining = device.remainingMillis(now)
        holder.tvRemaining.text = when {
            remaining == null -> "المتبقي: بدون حد"
            remaining <= 0L -> "المتبقي: انتهى وقته"
            else -> "المتبقي: ${formatRemaining(remaining)}"
        }
        if (device.isExpired(now)) {
            holder.tvRemaining.setTextColor(
                androidx.core.content.ContextCompat.getColor(ctx, R.color.gold)
            )
        } else {
            holder.tvRemaining.setTextColor(
                androidx.core.content.ContextCompat.getColor(ctx, R.color.text_primary)
            )
        }

        holder.tvBlockedNote.visibility = if (device.isBlocked) View.VISIBLE else View.GONE

        // قايمة الحد الزمني: بدون حد، 15، 30، 60، 120 دقيقة
        val limitOptions = listOf("بدون حد", "15 دقيقة", "30 دقيقة", "60 دقيقة", "120 دقيقة")
        val limitValues = listOf(0, 15, 30, 60, 120)
        val adapter = ArrayAdapter(ctx, R.layout.item_spinner, limitOptions)
        adapter.setDropDownViewResource(R.layout.item_spinner_dropdown)
        holder.spLimit.adapter = adapter
        holder.spLimit.setSelection(limitValues.indexOf(device.limitMinutes).takeIf { it >= 0 } ?: 0)
        holder.spLimit.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, v: View?, pos: Int, id: Long) {
                val chosen = limitValues[pos]
                if (chosen != device.limitMinutes) {
                    listener.onLimitChanged(device.mac, chosen)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        holder.swBlock.setOnCheckedChangeListener(null)
        holder.swBlock.isChecked = device.isBlocked
        holder.swBlock.setOnCheckedChangeListener { _, checked ->
            listener.onBlockChanged(device.mac, checked)
        }

        holder.btnRename.setOnClickListener { listener.onRename(device.mac) }
        holder.btnReset.setOnClickListener { listener.onResetTimer(device.mac) }
    }

    override fun getItemCount(): Int = items.size
}
