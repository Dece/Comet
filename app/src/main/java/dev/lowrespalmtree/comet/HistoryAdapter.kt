package dev.lowrespalmtree.comet

import android.annotation.SuppressLint
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import dev.lowrespalmtree.comet.History.HistoryEntry
import dev.lowrespalmtree.comet.databinding.FragmentHistoryItemBinding
import dev.lowrespalmtree.comet.utils.getFancySelectBgRes
import java.util.*

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
        val entry = items[position]
        // URI is always show.
        holder.binding.uriText.text = entry.uri
        // Main title is shown if one was found when record the entry.
        holder.binding.titleText.text = entry.title ?: ""
        // Last visited date is properly formatted.
        val lastVisit = Date(entry.lastVisit)
        val dateFormatter = DateFormat.getMediumDateFormat(holder.binding.root.context)
        holder.binding.lastVisitText.text = dateFormatter.format(lastVisit)
        // Bind the click action.
        holder.binding.container.setOnClickListener { listener.onItemClick(entry.uri) }
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