package com.example.smsgateway.ui.help

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.smsgateway.R

class HelpSectionAdapter(
    private val items: List<HelpSection>
) : RecyclerView.Adapter<HelpSectionAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tvSectionTitle)
        val body:  TextView = view.findViewById(R.id.tvSectionBody)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_help_section, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.title.text = item.title
        holder.body.text  = item.body
    }

    override fun getItemCount(): Int = items.size
}