package xyz.qjex.test.lw.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import xyz.qjex.test.lw.vo.FileData
import java.nio.file.FileVisitOption
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter
import java.util.stream.Collectors.toList
import javax.annotation.PostConstruct

private val TIME_FORMAT = SimpleDateFormat("dd/MM/yyyy - hh:mm:ss")

@Component
class FilesService {

    @Value("\${logDir:.}")
    private lateinit var logDir: String

    private lateinit var logDirPath: Path

    @PostConstruct
    fun setUp() {
        logDirPath = Paths.get(logDir)
        if (!Files.exists(logDirPath)) {
            error("$logDir doesn't exist")
        }
        if (!Files.isDirectory(logDirPath)) {
            error("$logDir is not a directory")
        }
    }

    fun getFiles(): List<FileData> = Files.walk(logDirPath, 1).use { fs ->
        fs.filter { Files.isRegularFile(it) }
                .map { FileData(it.fileName.toString(), TIME_FORMAT.format(Files.getLastModifiedTime(it).toMillis())) }
                .collect(toList())
    }


}