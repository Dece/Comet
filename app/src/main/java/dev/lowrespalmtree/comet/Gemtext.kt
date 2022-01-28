package dev.lowrespalmtree.comet

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset

interface Line

class EmptyLine : Line
class ParagraphLine(val text: String) : Line
class TitleLine(val level: Int, val text: String) : Line
class LinkLine(val url: String, val label: String) : Line
class PreFenceLine(val caption: String) : Line
class PreTextLine(val text: String) : Line
class BlockquoteLine(val text: String) : Line
class ListItemLine(val text: String) : Line

private const val TAG = "Gemtext"

/** Pipe incoming gemtext data into parsed Lines. */
fun parseData(
    inChannel: Channel<ByteArray>,
    charset: Charset,
    scope: CoroutineScope
): Channel<Line> {
    val channel = Channel<Line>()
    scope.launch {
        var isPref = false
        var buffer = ByteArray(0)
        Log.d(TAG, "parseData: start getting data from channel")
        for (data in inChannel) {
            buffer += data
            var nextLineFeed: Int = -1
            while (buffer.isNotEmpty() && buffer.indexOf(0x0A).also { nextLineFeed = it } > -1) {
                val lineData = buffer.sliceArray(0 until nextLineFeed)
                buffer = buffer.sliceArray(nextLineFeed + 1 until buffer.size)
                val lineString = charset.decode(ByteBuffer.wrap(lineData))
                val line = parseLine(lineString, isPref)
                when (line) {
                    is PreFenceLine -> isPref = !isPref
                }
                channel.send(line)
            }
        }
        Log.d(TAG, "parseData: channel closed")
        channel.close()
    }
    return channel
}

/** Parse a single line into a Line object. */
private fun parseLine(line: CharBuffer, isPreformatted: Boolean): Line =
    when {
        line.startsWith("```") -> PreFenceLine(getCharsFrom(line, 3))
        isPreformatted -> PreTextLine(line.toString())
        line.startsWith("###") -> TitleLine(3, getCharsFrom(line, 3))
        line.startsWith("##") -> TitleLine(2, getCharsFrom(line, 2))
        line.startsWith("#") -> TitleLine(1, getCharsFrom(line, 1))
        line.startsWith("* ") -> ListItemLine(getCharsFrom(line, 2))
        line.startsWith(">") -> BlockquoteLine(getCharsFrom(line, 1))
        line.startsWith("=>") -> getCharsFrom(line, 2)
            .split(" ", "\t", limit = 2)
            .run { LinkLine(get(0), if (size == 2) get(1).trimStart() else "") }
        line.isEmpty() -> EmptyLine()
        else -> ParagraphLine(line.toString())
    }

/** Get trimmed string from the char buffer starting from this position. */
private fun getCharsFrom(line: CharBuffer, index: Int) = line.substring(index).trim()