package dev.lowrespalmtree.comet

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import dev.lowrespalmtree.comet.databinding.FragmentIdentityBinding

class IdentityAdapter : RecyclerView.Adapter<HistoryItemAdapter.ViewHolder>() {
    private var identities = listOf<Unit>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryItemAdapter.ViewHolder {
        TODO("Not yet implemented")
    }

    override fun onBindViewHolder(holder: HistoryItemAdapter.ViewHolder, position: Int) {
        TODO("Not yet implemented")
    }

    override fun getItemCount(): Int {
        TODO("Not yet implemented")
    }

    inner class ViewHolder(val binding: FragmentIdentityBinding) : RecyclerView.ViewHolder(binding.root)
}