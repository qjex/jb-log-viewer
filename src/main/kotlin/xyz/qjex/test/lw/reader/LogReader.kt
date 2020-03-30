package xyz.qjex.test.lw.reader

import xyz.qjex.test.lw.session.Block
import xyz.qjex.test.lw.session.Part
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.math.max
import kotlin.math.min

private const val PART_LIMIT = 256
private const val BUFFER_SIZE = 4 * 1024

class LogReader(file: Path) : Closeable {

    private val fileChannel: FileChannel = FileChannel.open(file, StandardOpenOption.READ)

    /**
     * Starts reading from [requestedLine] line
     * if [requestedLine] is bigger than the amount of lines in file,
     * the last line of the file is set as starting line for current session
     * [requestedLine] is 1-indexed
     *
     * @return block that starts on [requestedLine]
     */
    fun findBlockByLine(requestedLine: Int): Block {
        fileChannel.position(0)
        var currentLine = 0
        var bytesRead = 0L
        var lastFullLineStart = 0L
        var currentLineStart = 0L
        val buffer = ByteBuffer.allocate(BUFFER_SIZE)
        readingLoop@ while (true) {
            val currentRead = fileChannel.read(buffer)
            if (currentRead <= 0) {
                break
            }
            for (i in 0 until currentRead) {
                bytesRead++
                if (buffer[i].toChar() == '\n') {
                    currentLine++
                    lastFullLineStart = currentLineStart
                    if (currentLine == requestedLine) {
                        break@readingLoop
                    }
                    currentLineStart = bytesRead
                }
            }
            buffer.position(0)
        }
        return createBlock(lastFullLineStart, currentLine)
    }

    /**
     * Verifies if there is less than [BUFFER_SIZE] bytes between file end and block's end.
     * If such holds, new non empty block could be read after the given [block] and the file end would be reached
     */
    fun fileEndReachable(block: Block) = block.end + BUFFER_SIZE >= fileChannel.size()

    /**
     * Reads block that ends right before [block] start.
     *
     * Method finds first '\n' "anchor" in [BUFFER_SIZE] bytes before the [block].
     * If there is no such "anchor" in this segment of bytes, the algorithms searches in [BUFFER_SIZE] bytes
     * before that segment. This process stops when the "anchor" is found or the start of the file reached.
     *
     * Then the algorithm starts reading blocks after this "anchor".
     * The last block before given [block] in this chain is returned.
     *
     * @return previous block or the same block with [NextBlock.moved] flag set to false
     */
    fun nextTopBlock(block: Block): NextBlock {
        if (block.start == 0L) {
            return NextBlock(false, block)
        }

        var prevLine = block.parts.first().line
        var leftBound = block.start
        var blockChainStart = 0L
        while (leftBound > 0) {
            val currentLeftBound = max(0, leftBound - BUFFER_SIZE)
            val capacity = if (currentLeftBound + BUFFER_SIZE >= leftBound) (leftBound - currentLeftBound).toInt() else BUFFER_SIZE
            val buffer = ByteBuffer.allocate(capacity)
            fileChannel.read(buffer, currentLeftBound)

            var anchorBufferPosition = -1
            if (currentLeftBound != 0L) {
                for (i in 0 until capacity) {
                    if (buffer[i].toChar() == '\n') {
                        anchorBufferPosition = i
                        break
                    }
                }
            }
            for (i in anchorBufferPosition + 1 until capacity) {
                if (buffer[i].toChar() == '\n') { // counting new lines between "anchor" and current top block
                    prevLine--
                }
            }
            val anchorPosition = currentLeftBound + anchorBufferPosition + 1
            val isLastPartOfLine = anchorPosition == leftBound
            if (isLastPartOfLine) { // counting line if the next block start with new line
                prevLine--
            }
            if (anchorBufferPosition != -1 && !isLastPartOfLine) { // found "anchor" and the next block is not the current one at the top
                blockChainStart = anchorPosition
                break
            }
            leftBound = currentLeftBound
        }

        var currentBlockInChain = createBlock(
                blockChainStart,
                prevLine,
                min(BUFFER_SIZE, (block.start - blockChainStart).toInt()),
                true
        )
        // going down the chain
        while (true) {
            if (currentBlockInChain.end > block.start) {
                error("Consistency error: ${currentBlockInChain.start} ${currentBlockInChain.end} ${block.start}")
            }
            if (currentBlockInChain.end == block.start) {
                break
            }
            val nextBlockInChainStart = currentBlockInChain.end
            val lastLineInChain = currentBlockInChain.parts.lastOrNull()?.line ?: 1
            val nextLineInChain = if (currentBlockInChain.parts.lastOrNull()?.containsNewLine == true) lastLineInChain + 1 else lastLineInChain
            currentBlockInChain = createBlock(
                    nextBlockInChainStart,
                    nextLineInChain,
                    min(BUFFER_SIZE, (block.start - nextBlockInChainStart).toInt()),
                    true
            )
        }
        return NextBlock(true, currentBlockInChain)
    }

    /**
     * Reads next block after [block].
     *
     * The algorithm reads next [BUFFER_SIZE] bytes after [block].
     * Then those bytes are splitted in the list of [Part].
     * Each [Part] is either the entire line ending with '\n' or the string containing [PART_LIMIT] bytes.
     * If in the latter case ends with some not finished utf8 byte sequence,
     * this sequence is trimmed and would go into the next [Part]
     *
     * @return next block or the same block with [NextBlock.moved] flag set to false
     */
    fun nextBottomBlock(block: Block): NextBlock {
        val nextStart = block.end
        val lastLine = block.parts.lastOrNull()?.line ?: 1
        val nextStartLine = if (block.parts.lastOrNull()?.containsNewLine == true) lastLine + 1 else lastLine
        val nextBlock = createBlock(nextStart, nextStartLine)

        if (nextBlock.parts.isEmpty()) {
            return NextBlock(false, block)
        }

        return NextBlock(true, nextBlock)
    }

    private fun createBlock(
            startPosition: Long,
            startLine: Int,
            length: Int = BUFFER_SIZE,
            allowNonFullTailPart: Boolean = false
    ): Block {
        val buffer = ByteBuffer.allocate(length)
        val read = fileChannel.read(buffer, startPosition)
        val processingResult = processBuffer(buffer, read, startLine, allowNonFullTailPart)
        return Block(startPosition, processingResult.first, processingResult.second)
    }

    private fun processBuffer(
            buffer: ByteBuffer,
            read: Int,
            startLine: Int,
            allowNonFullTailPart: Boolean
    ): Pair<Int, List<Part>> {
        if (read <= 0) {
            return Pair(0, listOf())
        }
        val parts = mutableListOf<Part>()
        var lastUtf8SequenceStart = 0
        var currentStart = 0
        var currentLine = startLine
        for (i in 0 until read) {
            val b = buffer[i]
            if (isUtf8SequenceStart(b)) {
                lastUtf8SequenceStart = i
            }
            if (b.toChar() == '\n') {
                parts += buffer.extractPart(currentStart, i - currentStart + 1, currentLine, true)
                currentLine++
                currentStart = i + 1
            } else if (i - currentStart == PART_LIMIT) {
                parts += buffer.extractPart(currentStart, lastUtf8SequenceStart - currentStart, currentLine, false)
                currentStart = lastUtf8SequenceStart
            }
        }
        if (currentStart != read && allowNonFullTailPart) {
            parts += buffer.extractPart(currentStart, read - currentStart, currentLine, false)
            currentStart = read
        }
        return Pair(currentStart, parts)
    }

    private fun isUtf8SequenceStart(b: Byte) = (b.toInt() and 0x80) == 0 || (b.toInt() and 0x40) != 0

    override fun close() {
        fileChannel.close()
    }
}

data class NextBlock(
        val moved: Boolean,
        val block: Block
)

private fun ByteBuffer.extractPart(start: Int, length: Int, line: Int, containsNewLine: Boolean) =
        Part(
                line,
                String(
                        this.array(),
                        start,
                        length,
                        StandardCharsets.UTF_8
                ),
                containsNewLine
        )