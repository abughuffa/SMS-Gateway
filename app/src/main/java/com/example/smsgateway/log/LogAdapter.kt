package com.example.smsgateway.log

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.smsgateway.R

class LogAdapter : RecyclerView.Adapter<LogAdapter.VH>() {

    private val items = ArrayList<LogBuffer.Entry>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_log_entry, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    /** Replace the entire list (used for filter changes). */
    fun submit(list: List<LogBuffer.Entry>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    /** Append one entry (used by the live listener). */
    fun append(entry: LogBuffer.Entry) {
        items.add(entry)
        notifyItemInserted(items.size - 1)
    }

    fun clear() {
        val n = items.size
        items.clear()
        notifyItemRangeRemoved(0, n)
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val tvTime: TextView    = view.findViewById(R.id.tvTime)
        private val tvLevel: TextView   = view.findViewById(R.id.tvLevel)
        private val tvTag: TextView     = view.findViewById(R.id.tvTag)
        private val tvMessage: TextView = view.findViewById(R.id.tvMessage)

        fun bind(e: LogBuffer.Entry) {
            tvTime.text    = e.formattedTime()
            tvLevel.text   = e.level.label
            tvTag.text     = e.tag
            tvMessage.text = e.message

            val (levelColor, textColor) = when (e.level) {
                LogBuffer.Level.DEBUG -> Color.parseColor("#9E9E9E") to Color.parseColor("#000000")
                LogBuffer.Level.INFO  -> Color.parseColor("#42A5F5") to Color.parseColor("#0000FF")
                LogBuffer.Level.WARN  -> Color.parseColor("#FFB300") to Color.parseColor("#FFD176")
                LogBuffer.Level.ERROR -> Color.parseColor("#EF5350") to Color.parseColor("#E95600")
            }
            tvLevel.setTextColor(levelColor)
            tvMessage.setTextColor(textColor)
            tvTag.setTextColor(levelColor)
        }
    }
}