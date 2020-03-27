package xyz.qjex.test.lw.config

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import xyz.qjex.test.lw.api.WSHandler
import xyz.qjex.test.lw.service.FilesService

@Configuration
@EnableWebSocket
class WSConfig
@Autowired constructor(private val filesService: FilesService) : WebSocketConfigurer {
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry): Unit = with(registry) {
        addHandler(WSHandler(filesService), "/view").withSockJS()
    }
}