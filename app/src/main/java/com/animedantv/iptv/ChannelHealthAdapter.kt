package com.animedantv.iptv

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

/**
 * Simple list adapter for [ChannelHealth] rows. Supports a free-text query
 * filter (matched case-insensitively against channel name, group, and URL).
 */
class ChannelHealthAdapter(
    private val onClick: (ChannelHealth) -> Unit,
    private val onLongClick: (ChannelHealth) -> Boolean,
) : RecyclerView.Adapter<ChannelHealthAdapter.VH>() {

    private var all: List<ChannelHealth> = emptyList()
    private var visible: List<ChannelHealth> = emptyList()
    private var query: String = ""

    fun submit(list: List<ChannelHealth>) {
        all = list
        applyFilter()
    }

    fun setQuery(q: String) {
        query = q.trim()
        applyFilter()
    }

    private fun applyFilter() {
        visible = if (query.isEmpty()) {
            all
        } else {
            val needle = query.lowercase(Locale.ROOT)
            all.filter { h ->
                h.channel.name.lowercase(Locale.ROOT).contains(needle) ||
                    (h.channel.group?.lowercase(Locale.ROOT)?.contains(needle) == true) ||
                    h.channel.streamUrl.lowercase(Locale.ROOT).contains(needle)
            }
        }
        notifyDataSetChanged()
    }

    fun getItem(position: Int): ChannelHealth = visible[position]

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_channel_health, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = visible[position]
        val ch = item.channel
        holder.name.text = ch.name.ifBlank { ch.streamUrl }
        val subtitle = ch.group?.takeIf { it.isNotBlank() }
        if (subtitle.isNullOrBlank()) {
            holder.subtitle.visibility = View.GONE
        } else {
            holder.subtitle.visibility = View.VISIBLE
            holder.subtitle.text = subtitle
        }
        val tint = ContextCompat.getColor(
            holder.itemView.context,
            if (item.isWorking) R.color.health_working else R.color.health_error,
        )
        holder.dot.setColorFilter(tint)
        holder.itemView.setOnClickListener { onClick(item) }
        holder.itemView.setOnLongClickListener { onLongClick(item) }
    }

    override fun getItemCount(): Int = visible.size

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.txt_name)
        val subtitle: TextView = v.findViewById(R.id.txt_subtitle)
        val dot: ImageView = v.findViewById(R.id.img_status)
    }
}
