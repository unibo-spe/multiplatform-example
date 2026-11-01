package io.github.gciatto.csv

/** Set of characters used to parse / represent CSV files. */
data class Configuration(val separator: Char, val delimiter: Char, val comment: Char) {
    private val commentPattern: Regex by lazy {
        """^\s*$comment\s*(.*)""".toRegex()
    }
    private val field: String
        get() = """$delimiter.*?$delimiter|[^$separator]*"""

    private val recordPattern: Regex by lazy {
        """\s*($field)\s*($separator|$)""".toRegex()
    }

    /** Checks whether the given string is a comment (i.e. starts with the comment character). */
    fun isComment(string: String): Boolean = commentPattern.matches(string)

    /** Gets the content of the comment line (i.e. removes the initial comment character). */
    fun getComment(string: String): String? = commentPattern.matchEntire(string)?.groupValues?.get(1)

    /** Checks whether the given string is a record (i.e. it contains at least one field). */
    fun isRecord(string: String): Boolean = recordPattern.find(string) != null

    private fun Sequence<Pair<String, String>>.takeUpToFirstEmptySeparator(): Sequence<String> = sequence {
        var emptySep = false
        for ((field, sep) in this@takeUpToFirstEmptySeparator) {
            if (separator !in sep) {
                if (emptySep) {
                    break
                } else {
                    emptySep = true
                }
            }
            yield(field)
        }
    }

    private fun String.unwrapFromDelimiter(): String =
        if (startsWith(delimiter) && endsWith(delimiter)) {
            slice(1 ..< lastIndex)
        } else {
            this.trim()
        }

    /** Retrieves the fields in the given record (i.e. splits the string at the separator character). */
    fun getFields(string: String): List<String> =
        recordPattern.findAll(string).map {
            val field = it.groupValues[1].unwrapFromDelimiter()
            val separator = it.groupValues[2]
            field to separator
        }.takeUpToFirstEmptySeparator().toList()

    /** Checks whether the given string is a header (i.e. a comment whose content is a record). */
    fun isHeader(string: String): Boolean =
        isComment(string) && getComment(string)?.let { isRecord(it) } == true

    /** Retrieves the column names in the given header. */
    fun getColumnNames(string: String): List<String> =
        getComment(string)?.let { getFields(it) } ?: emptyList()
}
