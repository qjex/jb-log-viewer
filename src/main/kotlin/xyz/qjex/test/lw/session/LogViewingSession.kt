package xyz.qjex.test.lw.session

import org.slf4j.LoggerFactory
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import xyz.qjex.test.lw.service.FilesService
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.Executors

private const val SET_LINE_COMMAND = "1"
private const val EXTEND_TOP_COMMAND = "2"
private const val EXTEND_BOTTOM_COMMAND = "3"
private const val REMOVE_TOP_COMMAND = "4"
private const val REMOVE_BOTTOM_COMMAND = "5"
private const val SERVER_APPEND_COMMAND = "6"
private const val ERROR_COMMAND = "7"

private const val FOLLOWER_UPDATE_DELAY_MS = 1000

private val LOGGER = LoggerFactory.getLogger(LogViewingSession::class.java)

class LogViewingSession(
        private val filesService: FilesService,
        private val wsSession: WebSocketSession,
        fileName: String?
) {
    private lateinit var logReader: LogReader

    init {
        if (validateFileName(fileName)) {
            logReader = LogReader(filesService.resolve(fileName!!))
        } else {
            wsSession.close()
        }
    }

    private fun validateFileName(fileName: String?): Boolean {
        if (fileName == null) {
            sendError("fileName not specified")
            return false
        }
        if (!filesService.validFileName(fileName)) {
            sendError("File not found")
            return false
        }
        return true
    }

    private val path = Paths.get(fileName)
    private lateinit var topBorder: Border
    private lateinit var bottomBorder: Border
    private val scheduler = Executors.newScheduledThreadPool(1)

    private fun setLine(requestedLine: Int) {
        topBorder = logReader.findBorderByLine(requestedLine)
        bottomBorder = topBorder
        wsSession.sendMessage(TextMessage("$SET_LINE_COMMAND|${topBorder.lineNumber}|${topBorder.partOrLine}"))
    }

    private fun extendTop() {
        val next = logReader.nextTop(topBorder)
        if (!next.moved) {
            wsSession.sendMessage(TextMessage("$EXTEND_TOP_COMMAND|0"))
        } else {
            topBorder = next.border
            wsSession.sendMessage(TextMessage("$EXTEND_TOP_COMMAND|${topBorder.lineNumber}|${topBorder.partOrLine}"))
        }
    }

    private fun extendBottom() {
        val next = logReader.nextBottom(bottomBorder)
        if (!next.moved) {
            wsSession.sendMessage(TextMessage("$EXTEND_BOTTOM_COMMAND|0"))
        } else {
            bottomBorder = next.border
            wsSession.sendMessage(TextMessage("$EXTEND_BOTTOM_COMMAND|${bottomBorder.lineNumber}|${bottomBorder.partOrLine}"))
        }
    }

    private fun removeTop() {
        val next = logReader.nextBottom(topBorder)
        if (next.moved) {
            topBorder = next.border
        }
        wsSession.sendMessage(TextMessage("$REMOVE_TOP_COMMAND|${next.moved}"))
    }

    private fun removeBottom() {
        val next = logReader.nextTop(bottomBorder)
        if (next.moved) {
            bottomBorder = next.border
        }
        wsSession.sendMessage(TextMessage("$REMOVE_BOTTOM_COMMAND|${next.moved}"))
    }

    fun handle(request: String) {
        val scanner = Scanner(request).also {
            it.useDelimiter("\\|")
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

    fun close() {
        logReader.close()
    }

    private fun sendError(errorMessage: String) {
        wsSession.sendMessage(TextMessage("$ERROR_COMMAND|$errorMessage"))
    }
}