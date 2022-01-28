package dev.lowrespalmtree.comet

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import dev.lowrespalmtree.comet.Identities.Identity
import dev.lowrespalmtree.comet.databinding.FragmentIdentityBinding

class IdentitiesAdapter : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {
    private var identities = listOf<Identity>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryAdapter.ViewHolder {
        TODO("Not yet implemented")
    }

    override fun onBindViewHolder(holder: HistoryAdapter.ViewHolder, position: Int) {
        TODO("Not yet implemented")
    }

    override fun getItemCount(): Int {
        TODO("Not yet implemented")
    }

    inner class ViewHolder(val binding: FragmentIdentityBinding) : RecyclerView.ViewHolder(binding.root)
}