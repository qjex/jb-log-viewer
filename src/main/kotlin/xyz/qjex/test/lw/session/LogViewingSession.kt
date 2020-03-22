package xyz.qjex.test.lw.session

import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Paths
import java.util.*
import kotlin.math.max

private const val LINE_LIMIT = 256
private const val BUFFER_SIZE = 1024

private const val EXTEND_TOP_COMMAND = "2"
private const val EXTEND_BOTTOM_COMMAND = "3"
private const val REMOVE_TOP_COMMAND = "4"
private const val REMOVE_BOTTOM_COMMAND = "5"
private const val SET_LINE_COMMAND = "1"

class LogViewingSession(
        private val wsSession: WebSocketSession,
        fileName: String
) {

    private val fileChannel: FileChannel = FileChannel.open(Paths.get(fileName))

    private lateinit var topBorder: Border
    private lateinit var bottomBorder: Border
    private val buffer = ByteBuffer.allocate(BUFFER_SIZE)
    private val partBuffer = ByteBuffer.allocate(LINE_LIMIT)

    /**
     * Starts reading from [requestedLine] line
     * if [requestedLine] is bigger than the amount of lines in file,
     * the last line is set as starting line for current session
     * [requestedLine] is 1-indexed
     */
    private fun setLine(requestedLine: Int) {
        var currentLine = 0
        var bytesRead = 0L
        var lastFullLineStart = 0L
        var currentLineStart = 0L
        readingLoop@ while (true) {
            val currentRead = fileChannel.read(buffer)
            if (currentRead == 0) {
                break
            }
            for (i in 0..currentRead) {
                bytesRead++
                if (buffer[i].toChar() == '\n') {
                    currentLine++
                    lastFullLineStart = currentLineStart
                    if (currentLine == requestedLine) {
                        break@readingLoop
                    }
                    currentLineStart = bytesRead + 1
                }
            }
        }

        val part = readLineOrPart(lastFullLineStart)
        topBorder = Border(currentLine, lastFullLineStart, part)
        bottomBorder = Border(currentLine, lastFullLineStart, part)
        wsSession.sendMessage(TextMessage("$SET_LINE_COMMAND|$currentLine|$part"))
    }

    private fun extendTop() {
        val next = nextUp(topBorder)
        if (next.second) {
            wsSession.sendMessage(TextMessage("$EXTEND_TOP_COMMAND|0"))
        } else {
            topBorder = next.first
            wsSession.sendMessage(TextMessage("$EXTEND_TOP_COMMAND|${topBorder.lineNumber}|${topBorder.partOrLine}"))
        }
    }

    private fun extendBottom() {
        val next = nextDown(bottomBorder)
        if (next.second) {
            wsSession.sendMessage(TextMessage("$EXTEND_BOTTOM_COMMAND|0"))
        } else {
            bottomBorder = next.first
            wsSession.sendMessage(TextMessage("$EXTEND_BOTTOM_COMMAND|${bottomBorder.lineNumber}|${bottomBorder.partOrLine}"))
        }
    }

    private fun removeTop() {
        val next = nextDown(topBorder)
        if (next.second) {
            topBorder = next.first
        }
        wsSession.sendMessage(TextMessage("$REMOVE_TOP_COMMAND|${next.second}"))
    }

    private fun removeBottom() {
        val next = nextUp(bottomBorder)
        if (next.second) {
            bottomBorder = next.first
        }
        wsSession.sendMessage(TextMessage("$REMOVE_BOTTOM_COMMAND|${next.second}"))
    }

    private fun nextDown(border: Border): Pair<Border, Boolean> {
        val nextStart = border.end
        val nextPartOrLine = readLineOrPart(nextStart)

        if (nextPartOrLine.isEmpty()) {
            return border to false
        }

        var newLineNumber = bottomBorder.lineNumber
        if (border.containsNewLine) {
            newLineNumber++
        }
        return Border(newLineNumber, nextStart, nextPartOrLine) to true
    }

    private fun nextUp(border: Border): Pair<Border, Boolean> {
        val prevFullPartStart = max(0, border.start - LINE_LIMIT)
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
            return border to false
        }

        val prevPartOrLine = readLineOrPart(prevStart)

        var newLineNumber = bottomBorder.lineNumber
        if (prevPartOrLine.contains('\n')) {
            newLineNumber--
        }

        return Border(newLineNumber, prevStart, prevPartOrLine) to true
    }

    private fun readLineOrPart(start: Long): String {
        val bytesRead = fileChannel.read(partBuffer, start)

        val resultBuilder = StringBuilder()
        for (i in 0..bytesRead) {
            resultBuilder.append(partBuffer[i].toChar())
            if (partBuffer[i].toChar() == '\n') {
                break
            }
        }
        return resultBuilder.toString()
    }

    fun handle(request: String) {
        val scanner = Scanner(request).also {
            it.useDelimiter("\|")
        }
        when (scanner.next()) {
            REMOVE_BOTTOM_COMMAND -> removeBottom()
            REMOVE_TOP_COMMAND -> removeTop()
            EXTEND_BOTTOM_COMMAND -> extendBottom()
            EXTEND_TOP_COMMAND -> extendTop()
            SET_LINE_COMMAND -> {
                val line = scanner.nextInt()
                setLine(line)
            }
        }
    }
}