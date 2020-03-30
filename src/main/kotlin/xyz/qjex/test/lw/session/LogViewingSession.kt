package xyz.qjex.test.lw.session

import org.slf4j.LoggerFactory
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import xyz.qjex.test.lw.l10n.ERROR_READING_FILE
import xyz.qjex.test.lw.l10n.FILE_DOES_NOT_EXISTS
import xyz.qjex.test.lw.l10n.INTERNAL_ERROR
import xyz.qjex.test.lw.reader.LogReader
import xyz.qjex.test.lw.service.FilesService
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private const val SET_LINE_COMMAND = "1"
private const val EXTEND_TOP_COMMAND = "2"
private const val EXTEND_BOTTOM_COMMAND = "3"
private const val ERROR_COMMAND = "4"

private const val FOLLOWER_UPDATE_DELAY_MS = 1000L
private const val MAX_LOADED_PARTS = 500
private val LOGGER = LoggerFactory.getLogger(LogViewingSession::class.java)

class LogViewingSession(
        filesService: FilesService,
        private val wsSession: WebSocketSession,
        fileName: String?
) {
    private lateinit var file: Path
    private lateinit var logReader: LogReader
    private lateinit var pushDataTask: ScheduledFuture<*>
    private val sessionLock = ReentrantLock()
    private val scheduler = Executors.newScheduledThreadPool(1)
    private val loadedBlocks: Deque<Block> = LinkedList()
    private var loadedParts = 0

    init {
        if (fileName == null || !filesService.validFileName(fileName)) {
            sendError(FILE_DOES_NOT_EXISTS)
        } else {
            file = filesService.resolve(fileName)
            logReader = LogReader(file)
            pushDataTask = scheduler.scheduleWithFixedDelay({ pushData() }, FOLLOWER_UPDATE_DELAY_MS, FOLLOWER_UPDATE_DELAY_MS, TimeUnit.MILLISECONDS)
        }
    }

    fun handle(request: String) = sessionLock.withLock {
        val scanner = Scanner(request).also {
            it.useDelimiter("\\|")
        }
        try {
            when (scanner.next()) {
                EXTEND_BOTTOM_COMMAND -> extendBottom()
                EXTEND_TOP_COMMAND -> extendTop()
                SET_LINE_COMMAND -> {
                    val line = scanner.nextInt()
                    setLine(line)
                }
            }
        } catch (e: IOException) {
            LOGGER.warn("Error reading file on request $request:", e)
            sendError(ERROR_READING_FILE)
        } catch (e: Exception) {
            LOGGER.warn("Internal error on request $request:", e)
            sendError(INTERNAL_ERROR)
        }
    }

    fun close() {
        pushDataTask.cancel(false)
        logReader.close()
        scheduler.shutdown()
    }

    private fun pushData() = sessionLock.withLock {
        if (!Files.exists(file)) {
            sendError("File deleted")
            return@withLock
        }
        if (logReader.fileEndReachable(loadedBlocks.last)) {
            extendBottom()
        }
    }

    private fun setLine(requestedLine: Int) {
        val block = logReader.findBlockByLine(requestedLine)
        loadedBlocks.clear()
        loadedBlocks.addFirst(block)
        loadedParts = block.parts.size
        sendExtendResponse(EXTEND_BOTTOM_COMMAND, 0, block)
    }

    private fun extendTop() {
        with(logReader.nextTopBlock(loadedBlocks.first)) {
            if (!moved) {
                sendExtendResponse(EXTEND_TOP_COMMAND)
            } else {
                loadedBlocks.addFirst(block)
                loadedParts += block.parts.size
                val toDelete = cleanUp { loadedBlocks.pollLast() }
                sendExtendResponse(EXTEND_TOP_COMMAND, toDelete, block)
            }
        }
    }

    private fun extendBottom() {
        with(logReader.nextBottomBlock(loadedBlocks.last)) {
            if (!moved) {
                sendExtendResponse(EXTEND_BOTTOM_COMMAND)
            } else {
                loadedBlocks.addLast(block)
                loadedParts += block.parts.size
                val toDelete = cleanUp { loadedBlocks.pollFirst() }
                sendExtendResponse(EXTEND_BOTTOM_COMMAND, toDelete, block)
            }
        }
    }

    private fun cleanUp(cleanUpFunction: () -> Block): Int {
        var toDelete = 0
        while (loadedBlocks.size > 2 && loadedParts > MAX_LOADED_PARTS) {
            val cleaned = cleanUpFunction().parts.size
            loadedParts -= cleaned
            toDelete += cleaned
        }
        return toDelete
    }

    private fun sendError(errorMessage: String) {
        wsSession.sendMessage(TextMessage("$ERROR_COMMAND|$errorMessage"))
    }

    private fun sendExtendResponse(command: String, toDelete: Int = 0, block: Block = Block(0, 0, emptyList())) {
        val commandBuilder = StringBuilder()
        commandBuilder.append("$command|$toDelete|${block.parts.size}")
        for (part in block.parts) {
            val encodedData = URLEncoder.encode(part.data, StandardCharsets.UTF_8)
            commandBuilder.append("|${part.line}|${encodedData}")
        }
        wsSession.sendMessage(TextMessage(commandBuilder.toString()))
    }
}