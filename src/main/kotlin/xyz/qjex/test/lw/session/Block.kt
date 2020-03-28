package xyz.qjex.test.lw.session

data class Block(
        val start: Long,
        val length: Int,
        val parts: List<Part>
) {
    val end
        get() = start + length
}

data class Part(
        val line: Int,
        val data: String,
        val containsNewLine: Boolean
)