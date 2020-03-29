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

private const val BLOCK_LIMIT = 256
private const val BUFFER_SIZE = 4 * 1024
private const val SMALL_BUFFER_SIZE = BUFFER_SIZE

class LogReader(file: Path) : Closeable {

    private val fileChannel: FileChannel = FileChannel.open(file, StandardOpenOption.READ)
    private val bufferArray = ByteArray(BUFFER_SIZE)

    /**
     * Starts reading from [requestedLine] line
     * if [requestedLine] is bigger than the amount of lines in file,
     * the last line is set as starting line for current session
     * [requestedLine] is 1-indexed
     */
    fun findBlockByLine(requestedLine: Int): Block {
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

    fun nextTopBlock(block: Block): NextBlock {
        if (block.start == 0L) {
            return NextBlock(false, block)
        }
        var prevLineStart = findPrevLineStart(block.start)
        var prevLine = block.parts.first().line

        if (prevLineStart == block.start) { // block starts with new line
            prevLineStart = findPrevLineStart(prevLineStart - 1)
            prevLine--
        }

        var prevBlock = createBlock(prevLineStart, prevLine, min(SMALL_BUFFER_SIZE, (block.start - prevLineStart).toInt()))

        while (true) {
            if (prevBlock.end > block.start) {
                error("Consistency error: ${prevBlock.start} ${prevBlock.end} ${block.start}")
            }
            if (prevBlock.end == block.start) {
                break
            }
            val nextBlock = nextBottomBlock(prevBlock)
            if (!nextBlock.moved) {
                error("Consistency error: expected next block")
            }
            prevBlock = nextBlock.block
        }
        return NextBlock(true, prevBlock)
    }

    fun nextBottomBlock(block: Block): NextBlock {
        val nextStart = block.end
        val lastLine = block.parts.lastOrNull()?.line ?: 1
        val nextStartLine = if (block.parts.lastOrNull()?.containsNewLine == true) lastLine + 1 else lastLine
        val nextBorder = createBlock(nextStart, nextStartLine)

        if (nextBorder.parts.isEmpty()) {
            return NextBlock(false, block)
        }

        return NextBlock(true, nextBorder)
    }

    private fun findPrevLineStart(currentStart: Long): Long {
        var prevLeftBound = currentStart
        while (prevLeftBound > 0) {
            val leftBound = max(0, prevLeftBound - BUFFER_SIZE)
            val capacity = if (leftBound + BUFFER_SIZE >= prevLeftBound) (prevLeftBound - leftBound).toInt() else BUFFER_SIZE
            val buffer = ByteBuffer.allocate(capacity)
            fileChannel.read(buffer, leftBound)
            for (i in capacity - 1 downTo 0) {
                if (buffer[i].toChar() == '\n') {
                    return leftBound + i + 1
                }
            }
            prevLeftBound = leftBound
        }
        return 0
    }

    private fun createBlock(startPosition: Long, startLine: Int, length: Int = SMALL_BUFFER_SIZE): Block {
        val buffer = ByteBuffer.allocate(length)
        val read = fileChannel.read(buffer, startPosition)
        val processingResult = processBuffer(buffer, read, startLine)
        return Block(startPosition, processingResult.first, processingResult.second)
    }

    private fun processBuffer(buffer: ByteBuffer, read: Int, startLine: Int): Pair<Int, List<Part>> {
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
            }
            if (i - currentStart == BLOCK_LIMIT) {
                parts += Part(currentLine, String(
                        buffer.array(),
                        currentStart,
                        lastUtf8SequenceStart - currentStart,
                        StandardCharsets.UTF_8
                ), false)
                currentStart = lastUtf8SequenceStart
            }
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