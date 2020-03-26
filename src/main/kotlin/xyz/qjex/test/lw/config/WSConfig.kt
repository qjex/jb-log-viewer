package xyz.qjex.test.lw.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import xyz.qjex.test.lw.api.WSHandler

@Configuration
@EnableWebSocket
class WSConfig : WebSocketConfigurer {
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry): Unit = with(registry) {
        addHandler(WSHandler(), "/view").withSockJS()
    }
}