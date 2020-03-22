package xyz.qjex.test.lw.api

import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import xyz.qjex.test.lw.session.LogViewingSession
import java.util.concurrent.ConcurrentHashMap

private const val SESSION = "session"

class WSHandler : TextWebSocketHandler() {

    private val requests = ConcurrentHashMap<String, String>()

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val remoteAddress = session.remoteAddress.toString()
        if (message.isLast) {
            val request = (requests.remove(remoteAddress) ?: "") + message.payload
            (session.attributes[SESSION] as LogViewingSession).handle(request)
        } else {
            requests.merge(remoteAddress, message.payload) { a, b: String -> a + b }
        }
    }

    override fun afterConnectionEstablished(session: WebSocketSession) {
        session.attributes[SESSION] = LogViewingSession(session, session.uri!!.query)
    }
}