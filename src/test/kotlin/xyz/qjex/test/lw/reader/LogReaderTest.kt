package xyz.qjex.test.lw.reader

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.file.Paths


class LogReaderTest {

    private val resourcesDir = Paths.get("src", "test", "resources")

    @Test
    fun `Read each line and go upwards`() {
        LogReader(resourcesDir.resolve("test.log")).use {
            val linesNumber = it.findBlockByLine(0).parts.last().line
            for (line in 1..linesNumber) {
                var block = it.findBlockByLine(line)
                while (true) {
                    assertTrue(block.parts.isNotEmpty())
                    val next = it.nextTopBlock(block)
                    if (!next.moved) {
                        break
                    }
                    assertEquals(block.start, next.block.end)
                    block = next.block
                }
            }
        }
    }

    @Test
    fun `Read empty file`() {
        LogReader(resourcesDir.resolve("empty.log")).use {
            val lineBlock = it.findBlockByLine(10)

            assertTrue(lineBlock.parts.isEmpty())
            assertEquals(0, lineBlock.start)
            assertEquals(0, lineBlock.end)

            assertFalse(it.nextBottomBlock(lineBlock).moved)
            assertFalse(it.nextTopBlock(lineBlock).moved)

            assertTrue(it.fileEndReachable(lineBlock))
        }
    }

    @Test
    fun fileEndReachable() {
        LogReader(resourcesDir.resolve("test.log")).use {
            assertFalse(it.fileEndReachable(it.findBlockByLine(10)))
            assertTrue(it.fileEndReachable(it.findBlockByLine(1000)))
        }
    }

    @Test
    fun `Go downwards and then upwards`() {
        LogReader(resourcesDir.resolve("seq.log")).use {
            var block = it.findBlockByLine(1)
            while (true) {
                val next = it.nextBottomBlock(block)
                if (!next.moved) {
                    break
                }
                assertEquals(block.end, next.block.start)
                assertEquals(block.parts.last().line + 1, next.block.parts.first().line)
                block = next.block
            }
            while (true) {
                val next = it.nextTopBlock(block)
                if (!next.moved) {
                    break
                }
                assertEquals(block.start, next.block.end)
                assertEquals(block.parts.first().line - 1, next.block.parts.last().line)
                block = next.block
            }
            assertEquals(0L, block.start)
            assertEquals(1, block.parts.first().line)
        }
    }
}