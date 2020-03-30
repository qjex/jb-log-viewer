package xyz.qjex.test.lw.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*


@ExtendWith(SpringExtension::class)
@ContextConfiguration(classes = [FilesServiceTest.TestConfig::class])
internal class FilesServiceTest {

    @Autowired
    private lateinit var service: FilesService

    @Test
    fun validFileName() {
        assertTrue(service.validFileName("seq.log"))
        assertFalse(service.validFileName("se1.log"))
        assertFalse(service.validFileName("non_log_dir"))

        val logDir = Paths.get("src", "test", "resources")
        val file = logDir.resolve(Paths.get("non_log_dir", "file"))
        assertFalse(service.validFileName(logDir.relativize(file).toString()))
    }

    @Test
    fun resolve() {
        assertTrue(Files.isSameFile(
                Paths.get("src", "test", "resources", "seq.log"),
                service.resolve("seq.log")
        ))
    }

    @Test
    fun getFiles() {
        val result = service.getFiles()
        assertEquals(4, result.size)
    }


    @Configuration
    class TestConfig {

        @Bean
        fun filesService() = FilesService()

        @Bean
        fun properties(): PropertySourcesPlaceholderConfigurer = PropertySourcesPlaceholderConfigurer().apply {
            setProperties(Properties().also {
                it.setProperty("logDir", Paths.get("src", "test", "resources").toAbsolutePath().toString())
            })
        }
    }
}