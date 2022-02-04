package dev.lowrespalmtree.comet

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import dev.lowrespalmtree.comet.Identities.Identity
import dev.lowrespalmtree.comet.databinding.FragmentIdentityBinding
import dev.lowrespalmtree.comet.utils.getFancySelectBgRes

class IdentitiesAdapter(private val listener: Listener) :
    RecyclerView.Adapter<IdentitiesAdapter.ViewHolder>() {
    private var identities = listOf<Identity>()

    interface Listener {
        fun onIdentityClick(identity: Identity)
        fun onIdentityLongClick(identity: Identity, view: View)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            FragmentIdentityBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = identities[position]
        holder.binding.labelText.text = item.name.orEmpty()
        holder.binding.keyText.text = item.key
        holder.binding.container.setOnClickListener {
            listener.onIdentityClick(item)
        }
        holder.binding.container.setOnLongClickListener {
            listener.onIdentityLongClick(item, holder.itemView);
            true
        }
    }

    override fun getItemCount(): Int = identities.size

    @SuppressLint("NotifyDataSetChanged")
    fun setIdentities(newIdentities: List<Identity>) {
        identities = newIdentities.toList()
        notifyDataSetChanged()
    }

    inner class ViewHolder(val binding: FragmentIdentityBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            itemView.setBackgroundResource(getFancySelectBgRes(itemView.context))
        }
    }
}