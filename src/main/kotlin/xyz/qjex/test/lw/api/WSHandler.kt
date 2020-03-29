package xyz.qjex.test.lw.api

import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator
import org.springframework.web.socket.handler.TextWebSocketHandler
import xyz.qjex.test.lw.service.FilesService
import xyz.qjex.test.lw.session.LogViewingSession

private const val SESSION = "session"
private const val TIME_TO_SEND_MS = 5000
private const val BUFFER_SIZE_LIMIT_BYTES = 1024

class WSHandler(
        private val filesService: FilesService
) : TextWebSocketHandler() {

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        (session.attributes[SESSION] as LogViewingSession).handle(message.payload)
    }

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val fileName = session.uri?.query?.substringAfter("=")
        session.attributes[SESSION] = LogViewingSession(
                filesService,
                ConcurrentWebSocketSessionDecorator(session, TIME_TO_SEND_MS, BUFFER_SIZE_LIMIT_BYTES),
                fileName
        )
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        (session.attributes[SESSION] as LogViewingSession?)?.close()
    }
}