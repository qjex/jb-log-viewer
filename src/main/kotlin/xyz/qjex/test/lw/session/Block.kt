package xyz.qjex.test.lw.session

data class Block(
        val start: Long,
        val length: Int,
        val parts: List<Part>
) {
    val end
        get() = start + length
}

/**
 * [Part] represents a full line ending with new line or part of the line (the length of it defined by [LogReader]
 *
 * @property line the actual line if file.
 * @property data full line or its part.
 * @property containsNewLine true if the [line] ends with [data].
 */
data class Part(
        val line: Int,
        val data: String,
        val containsNewLine: Boolean
)