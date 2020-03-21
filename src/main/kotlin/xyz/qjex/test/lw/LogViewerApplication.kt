package xyz.qjex.test.lw

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class LogViewerApplication

fun main(args: Array<String>) {
	runApplication<LogViewerApplication>(*args)
}
