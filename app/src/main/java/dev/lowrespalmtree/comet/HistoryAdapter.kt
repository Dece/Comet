package dev.lowrespalmtree.comet

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import dev.lowrespalmtree.comet.History.HistoryEntry
import dev.lowrespalmtree.comet.databinding.FragmentHistoryItemBinding
import dev.lowrespalmtree.comet.utils.getFancySelectBgRes

class HistoryAdapter(private val listener: Listener) :
    RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {
    private var items = listOf<HistoryEntry>()

    interface Listener {
        fun onItemClick(url: String)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            FragmentHistoryItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.uriText.text = item.uri
        holder.binding.titleText.visibility =
            if (item.title.isNullOrBlank()) View.GONE else View.VISIBLE
        holder.binding.titleText.text = item.title ?: ""
        holder.binding.container.setOnClickListener { listener.onItemClick(item.uri) }
    }

    override fun getItemCount(): Int = items.size

    @SuppressLint("NotifyDataSetChanged")
    fun setItems(items: List<HistoryEntry>) {
        this.items = items.toList()
        notifyDataSetChanged()
    }

    inner class ViewHolder(val binding: FragmentHistoryItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            itemView.setBackgroundResource(getFancySelectBgRes(itemView.context))
        }
    }
}