package xyz.qjex.test.lw.session

import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.math.max

private const val LINE_LIMIT = 256
private const val BUFFER_SIZE = 1024

class LogReader(file: Path) : Closeable {

    private val fileChannel: FileChannel = FileChannel.open(file, StandardOpenOption.READ)
    private val partBufferArray = ByteArray(LINE_LIMIT)
    private val bufferArray = ByteArray(BUFFER_SIZE)

    /**
     * Starts reading from [requestedLine] line
     * if [requestedLine] is bigger than the amount of lines in file,
     * the last line is set as starting line for current session
     * [requestedLine] is 1-indexed
     */
    fun findBorderByLine(requestedLine: Int): Border {
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
        val part = readLineOrPart(lastFullLineStart)
        return Border(currentLine, lastFullLineStart, part)
    }

    fun nextTop(border: Border): NextBorder {
        val prevFullPartStart = max(0, border.start - LINE_LIMIT)
        val partBuffer = ByteBuffer.wrap(partBufferArray)
        fileChannel.read(partBuffer, prevFullPartStart)

        val prevPartBytes = partBuffer.array().copyOfRange(0, (border.start - prevFullPartStart).toInt())
        var prevStart = prevFullPartStart
        for (i in prevPartBytes.size - 2 downTo 0) {
            if (prevPartBytes[i].toChar() == '\n') {
                prevStart += i + 1
                break
            }
        }

        if (prevStart == border.start) {
            return NextBorder(false, border)
        }

        val prevPartOrLine = readLineOrPart(prevStart)

        var newLineNumber = border.lineNumber
        if (prevPartOrLine.contains('\n')) {
            newLineNumber--
        }

        return NextBorder(true, Border(newLineNumber, prevStart, prevPartOrLine))
    }

    fun nextBottom(border: Border): NextBorder {
        val nextStart = border.end
        val nextPartOrLine = readLineOrPart(nextStart)

        if (nextPartOrLine.isEmpty()) {
            return NextBorder(false, border)
        }

        var newLineNumber = border.lineNumber
        if (border.containsNewLine) {
            newLineNumber++
        }
        return NextBorder(true, Border(newLineNumber, nextStart, nextPartOrLine))

    }

    private fun readLineOrPart(start: Long): String {
        val partBuffer = ByteBuffer.wrap(partBufferArray)
        val bytesRead = fileChannel.read(partBuffer, start)

        val resultBuilder = StringBuilder()
        for (i in 0 until bytesRead) {
            resultBuilder.append(partBuffer[i].toChar())
            if (partBuffer[i].toChar() == '\n') {
                break
            }
        }
        return resultBuilder.toString()
    }

    override fun close() {
        fileChannel.close()
    }
}

data class NextBorder(
        val moved: Boolean,
        val border: Border
)