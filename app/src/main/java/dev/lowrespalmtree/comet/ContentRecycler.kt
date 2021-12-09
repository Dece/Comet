package dev.lowrespalmtree.comet

import android.text.SpannableString
import android.text.style.UnderlineSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import dev.lowrespalmtree.comet.databinding.*

class ContentAdapter(private var content: List<Line>, private val listener: ContentAdapterListen) :
    RecyclerView.Adapter<ContentAdapter.ContentViewHolder>() {

    interface ContentAdapterListen {
        fun onLinkClick(url: String)
    }

    override fun getItemViewType(position: Int): Int =
        when (content[position]) {
            is EmptyLine -> TYPE_EMPTY
            is ParagraphLine -> TYPE_PARAGRAPH
            is LinkLine -> TYPE_LINK
            is PreFenceLine -> TYPE_PRE_FENCE
            is PreTextLine -> TYPE_PRE_TEXT
            is BlockquoteLine -> TYPE_BLOCKQUOTE
            is ListItemLine -> TYPE_LIST_ITEM
            is TitleLine -> when ((content[position] as TitleLine).level) {
                1 -> TYPE_TITLE_1
                2 -> TYPE_TITLE_2
                3 -> TYPE_TITLE_3
                else -> error("invalid title level")
            }
            else -> error("unknown line type")
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContentViewHolder {
        Log.d(TAG, "onCreateViewHolder: type $viewType")
        return LayoutInflater.from(parent.context).let {
            when (viewType) {
                TYPE_EMPTY -> ContentViewHolder.Empty(GemtextEmptyBinding.inflate(it))
                TYPE_PARAGRAPH -> ContentViewHolder.Paragraph(GemtextParagraphBinding.inflate(it))
                TYPE_TITLE_1 -> ContentViewHolder.Title1(GemtextTitle1Binding.inflate(it))
                TYPE_TITLE_2 -> ContentViewHolder.Title2(GemtextTitle2Binding.inflate(it))
                TYPE_TITLE_3 -> ContentViewHolder.Title3(GemtextTitle3Binding.inflate(it))
                TYPE_LINK -> ContentViewHolder.Link(GemtextLinkBinding.inflate(it))
                TYPE_PRE_FENCE -> ContentViewHolder.PreFence(GemtextEmptyBinding.inflate(it))
                TYPE_PRE_TEXT -> ContentViewHolder.PreText(GemtextPreformattedBinding.inflate(it))
                TYPE_BLOCKQUOTE -> ContentViewHolder.Blockquote(GemtextBlockquoteBinding.inflate(it))
                TYPE_LIST_ITEM -> ContentViewHolder.ListItem(GemtextListItemBinding.inflate(it))
                else -> error("invalid view type")
            }
        }
    }

    override fun onBindViewHolder(holder: ContentViewHolder, position: Int) {
        Log.d(TAG, "onBindViewHolder: position $position")
        val line = content[position]
        when (holder) {
            is ContentViewHolder.Paragraph -> holder.binding.textView.text =
                (line as ParagraphLine).text
            is ContentViewHolder.Title1 -> holder.binding.textView.text = (line as TitleLine).text
            is ContentViewHolder.Title2 -> holder.binding.textView.text = (line as TitleLine).text
            is ContentViewHolder.Title3 -> holder.binding.textView.text = (line as TitleLine).text
            is ContentViewHolder.Link -> {
                val text = if ((line as LinkLine).label.isNotEmpty()) line.label else line.url
                val underlined = SpannableString(text)
                underlined.setSpan(UnderlineSpan(), 0, underlined.length, 0)
                holder.binding.textView.text = underlined
                holder.binding.root.setOnClickListener { listener.onLinkClick(line.url) }
            }
            else -> {}
        }
    }

    override fun getItemCount(): Int = content.size

    fun setContent(content: List<Line>) {
        this.content = content
        notifyDataSetChanged()
    }

    sealed class ContentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        class Empty(val binding: GemtextEmptyBinding) : ContentViewHolder(binding.root)
        class Paragraph(val binding: GemtextParagraphBinding) : ContentViewHolder(binding.root)
        class Title1(val binding: GemtextTitle1Binding) : ContentViewHolder(binding.root)
        class Title2(val binding: GemtextTitle2Binding) : ContentViewHolder(binding.root)
        class Title3(val binding: GemtextTitle3Binding) : ContentViewHolder(binding.root)
        class Link(val binding: GemtextLinkBinding) : ContentViewHolder(binding.root)
        class PreFence(val binding: GemtextEmptyBinding) : ContentViewHolder(binding.root)
        class PreText(val binding: GemtextPreformattedBinding) : ContentViewHolder(binding.root)
        class Blockquote(val binding: GemtextBlockquoteBinding) : ContentViewHolder(binding.root)
        class ListItem(val binding: GemtextListItemBinding) : ContentViewHolder(binding.root)
    }

    companion object {
        const val TAG = "ContentRecycler"
        const val TYPE_EMPTY = 0
        const val TYPE_PARAGRAPH = 1
        const val TYPE_TITLE_1 = 2
        const val TYPE_TITLE_2 = 3
        const val TYPE_TITLE_3 = 4
        const val TYPE_LINK = 5
        const val TYPE_PRE_FENCE = 6
        const val TYPE_PRE_TEXT = 7
        const val TYPE_BLOCKQUOTE = 8
        const val TYPE_LIST_ITEM = 9
    }
}