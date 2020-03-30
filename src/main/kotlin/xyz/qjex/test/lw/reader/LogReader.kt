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
    private val bufferArray = ByteArray(BUFFER_SIZE)

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
        readingLoop@ while (true) {
            val buffer = ByteBuffer.wrap(bufferArray)
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
        var prevLeftBound = block.start
        var prevStart = 0L
        while (prevLeftBound > 0) {
            val leftBound = max(0, prevLeftBound - BUFFER_SIZE)
            val capacity = if (leftBound + BUFFER_SIZE >= prevLeftBound) (prevLeftBound - leftBound).toInt() else BUFFER_SIZE
            val buffer = ByteBuffer.allocate(capacity)
            fileChannel.read(buffer, leftBound)
            var firstNewLine = -1
            if (leftBound != 0L) {
                for (i in 0 until capacity) {
                    if (buffer[i].toChar() == '\n') {
                        firstNewLine = i
                        break
                    }
                }
            }
            for (i in firstNewLine + 1 until capacity) {
                if (buffer[i].toChar() == '\n') {
                    prevLine--
                }
            }
            if (leftBound + firstNewLine + 1 == prevLeftBound) {
                prevLine--
            }
            if (firstNewLine != -1 && leftBound + firstNewLine + 1 != prevLeftBound) {
                prevStart = leftBound + firstNewLine + 1
                break
            }
            prevLeftBound = leftBound
        }

        var curBlock = createBlock(prevStart, prevLine, min(BUFFER_SIZE, (block.start - prevStart).toInt()), true)

        while (true) {
            if (curBlock.end > block.start) {
                error("Consistency error: ${curBlock.start} ${curBlock.end} ${block.start}")
            }
            if (curBlock.end == block.start) {
                break
            }
            val nextStart = curBlock.end
            val lastLine = curBlock.parts.lastOrNull()?.line ?: 1
            val nextStartLine = if (curBlock.parts.lastOrNull()?.containsNewLine == true) lastLine + 1 else lastLine
            curBlock = createBlock(nextStart, nextStartLine, min(BUFFER_SIZE, (block.start - nextStart).toInt()), true)
        }
        return NextBlock(true, curBlock)
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

    private fun createBlock(startPosition: Long, startLine: Int, length: Int = BUFFER_SIZE, allowNonFullTailPart: Boolean = false): Block {
        val buffer = ByteBuffer.allocate(length)
        val read = fileChannel.read(buffer, startPosition)
        val processingResult = processBuffer(buffer, read, startLine, allowNonFullTailPart)
        return Block(startPosition, processingResult.first, processingResult.second)
    }

    private fun processBuffer(buffer: ByteBuffer, read: Int, startLine: Int, allowNonFullTailPart: Boolean): Pair<Int, List<Part>> {
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
                parts += Part(currentLine, String(
                        buffer.array(),
                        currentStart,
                        i - currentStart + 1,
                        StandardCharsets.UTF_8
                ), true)
                currentLine++
                currentStart = i + 1
            } else if (i - currentStart == PART_LIMIT) {
                parts += Part(currentLine, String(
                        buffer.array(),
                        currentStart,
                        lastUtf8SequenceStart - currentStart,
                        StandardCharsets.UTF_8
                ), false)
                currentStart = lastUtf8SequenceStart
            }
        }
        if (currentStart != read && allowNonFullTailPart) {
            parts += Part(currentLine, String(
                    buffer.array(),
                    currentStart,
                    read - currentStart,
                    StandardCharsets.UTF_8
            ), false)
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