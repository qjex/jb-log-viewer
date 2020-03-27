package xyz.qjex.test.lw.session

data class Border(
        val lineNumber: Int,
        val start: Long,
        val partOrLine: String
) {
    val end
        get() = start + partOrLine.length
    val containsNewLine: Boolean by lazy {
        partOrLine.contains("\n")
    }
}