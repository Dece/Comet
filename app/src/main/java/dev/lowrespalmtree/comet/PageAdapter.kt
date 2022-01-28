package dev.lowrespalmtree.comet

import android.annotation.SuppressLint
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import dev.lowrespalmtree.comet.databinding.*

/**
 * Adapter for a rendered Gemtext page.
 *
 * Considering each content line as a different block/View to render works until you want to render
 * preformatted lines as a whole block. This adapter approaches this issue by mapping each line to
 * a block, where most of the time it will be a 1-to-1 relation, but for blocks and possibly quotes
 * it could be a n-to-1 relation: many lines belong to the same block. This of course changes a bit
 * the way we have to render things.
 */
class PageAdapter(private val listener: ContentAdapterListener) :
    RecyclerView.Adapter<PageAdapter.ContentViewHolder>() {

    private var lines = listOf<Line>()
    private var currentLine = 0
    private var blocks = mutableListOf<ContentBlock>()
    private var lastBlockCount = 0

    interface ContentAdapterListener {
        fun onLinkClick(url: String)
    }

    sealed class ContentBlock {
        object Empty : ContentBlock()
        class Paragraph(val text: String) : ContentBlock()
        class Title(val text: String, val level: Int) : ContentBlock()
        class Link(val url: String, val label: String) : ContentBlock()
        class Pre(val caption: String, var content: String, var closed: Boolean) : ContentBlock()
        class Blockquote(var text: String) : ContentBlock()
        class ListItem(val text: String) : ContentBlock()
    }

    /** Replace the content rendered by the recycler. */
    @SuppressLint("NotifyDataSetChanged")
    fun setLines(newLines: List<Line>) {
        lines = newLines.toList()  // Shallow copy to avoid concurrent update issues.
        if (lines.isEmpty()) {
            Log.d(TAG, "setLines: empty content")
            currentLine = 0
            blocks = mutableListOf()
            lastBlockCount = 0
            notifyDataSetChanged()
        } else {
            while (currentLine < lines.size) {
                when (val line = lines[currentLine]) {
                    is EmptyLine -> blocks.add(ContentBlock.Empty)
                    is ParagraphLine -> blocks.add(ContentBlock.Paragraph(line.text))
                    is LinkLine -> blocks.add(ContentBlock.Link(line.url, line.label))
                    is ListItemLine -> blocks.add(ContentBlock.ListItem(line.text))
                    is TitleLine -> blocks.add(ContentBlock.Title(line.text, line.level))
                    is PreFenceLine -> {
                        var justClosedBlock = false
                        if (blocks.isNotEmpty()) {
                            val lastBlock = blocks.last()
                            if (lastBlock is ContentBlock.Pre && !lastBlock.closed) {
                                lastBlock.closed = true
                                justClosedBlock = true
                            }
                        }
                        if (!justClosedBlock)
                            blocks.add(ContentBlock.Pre(line.caption, "", false))
                    }
                    is PreTextLine -> {
                        val lastBlock = blocks.last()
                        if (lastBlock is ContentBlock.Pre && !lastBlock.closed) {
                            if (lastBlock.content.isNotEmpty())
                                lastBlock.content += "\n"
                            lastBlock.content += line.text
                        } else {
                            Log.e(TAG, "setLines: unexpected preformatted line")
                        }
                    }
                    is BlockquoteLine -> {
                        if (blocks.isNotEmpty() && blocks.last() is ContentBlock.Blockquote)
                            (blocks.last() as ContentBlock.Blockquote).text += "\n" + line.text
                        else
                            blocks.add(ContentBlock.Blockquote(line.text))
                    }
                }
                currentLine++
            }
            val numAdded = blocks.size - lastBlockCount
            Log.d(TAG, "setContent: added $numAdded items")
            notifyItemRangeInserted(lastBlockCount, numAdded)
        }
        lastBlockCount = blocks.size
    }

    sealed class ContentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        class Empty(binding: GemtextEmptyBinding) : ContentViewHolder(binding.root)
        class Paragraph(val binding: GemtextParagraphBinding) : ContentViewHolder(binding.root)
        class Title1(val binding: GemtextTitle1Binding) : ContentViewHolder(binding.root)
        class Title2(val binding: GemtextTitle2Binding) : ContentViewHolder(binding.root)
        class Title3(val binding: GemtextTitle3Binding) : ContentViewHolder(binding.root)
        class Link(val binding: GemtextLinkBinding) : ContentViewHolder(binding.root)
        class Pre(val binding: GemtextPreformattedBinding) : ContentViewHolder(binding.root)
        class Blockquote(val binding: GemtextBlockquoteBinding) : ContentViewHolder(binding.root)
        class ListItem(val binding: GemtextListItemBinding) : ContentViewHolder(binding.root)
    }

    override fun getItemViewType(position: Int): Int =
        when (val block = blocks[position]) {
            is ContentBlock.Empty -> TYPE_EMPTY
            is ContentBlock.Paragraph -> TYPE_PARAGRAPH
            is ContentBlock.Link -> TYPE_LINK
            is ContentBlock.Blockquote -> TYPE_BLOCKQUOTE
            is ContentBlock.ListItem -> TYPE_LIST_ITEM
            is ContentBlock.Pre -> TYPE_PREFORMATTED
            is ContentBlock.Title -> block.level  // ruse
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContentViewHolder {
        return LayoutInflater.from(parent.context).let {
            when (viewType) {
                TYPE_EMPTY -> ContentViewHolder.Empty(GemtextEmptyBinding.inflate(it))
                TYPE_PARAGRAPH -> ContentViewHolder.Paragraph(GemtextParagraphBinding.inflate(it))
                TYPE_TITLE_1 -> ContentViewHolder.Title1(GemtextTitle1Binding.inflate(it))
                TYPE_TITLE_2 -> ContentViewHolder.Title2(GemtextTitle2Binding.inflate(it))
                TYPE_TITLE_3 -> ContentViewHolder.Title3(GemtextTitle3Binding.inflate(it))
                TYPE_LINK -> ContentViewHolder.Link(GemtextLinkBinding.inflate(it))
                TYPE_PREFORMATTED -> ContentViewHolder.Pre(GemtextPreformattedBinding.inflate(it))
                TYPE_BLOCKQUOTE -> ContentViewHolder.Blockquote(GemtextBlockquoteBinding.inflate(it))
                TYPE_LIST_ITEM -> ContentViewHolder.ListItem(GemtextListItemBinding.inflate(it))
                else -> error("invalid view type")
            }
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ContentViewHolder, position: Int) {
        when (val block = blocks[position]) {
            is ContentBlock.Empty -> {}
            is ContentBlock.Paragraph ->
                (holder as ContentViewHolder.Paragraph).binding.textView.text = block.text
            is ContentBlock.Blockquote ->
                (holder as ContentViewHolder.Blockquote).binding.textView.text = block.text
            is ContentBlock.ListItem ->
                (holder as ContentViewHolder.ListItem).binding.textView.text = "\u25CF ${block.text}"
            is ContentBlock.Title -> {
                when (block.level) {
                    1 -> (holder as ContentViewHolder.Title1).binding.textView.text = block.text
                    2 -> (holder as ContentViewHolder.Title2).binding.textView.text = block.text
                    3 -> (holder as ContentViewHolder.Title3).binding.textView.text = block.text
                }
            }
            is ContentBlock.Link -> {
                val label = if (block.label.isNotBlank()) block.label else block.url
                (holder as ContentViewHolder.Link).binding.textView.text = label
                holder.binding.root.setOnClickListener { listener.onLinkClick(block.url) }
            }
            is ContentBlock.Pre ->
                (holder as ContentViewHolder.Pre).binding.textView.text = block.content
        }
    }

    override fun getItemCount(): Int = blocks.size

    companion object {
        private const val TAG = "PageAdapter"
        private const val TYPE_EMPTY = 0
        private const val TYPE_TITLE_1 = 1
        private const val TYPE_TITLE_2 = 2
        private const val TYPE_TITLE_3 = 3
        private const val TYPE_PARAGRAPH = 4
        private const val TYPE_LINK = 5
        private const val TYPE_PREFORMATTED = 6
        private const val TYPE_BLOCKQUOTE = 7
        private const val TYPE_LIST_ITEM = 8
    }
}