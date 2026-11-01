package io.github.gciatto.csv.impl

import io.github.gciatto.csv.Configuration
import io.github.gciatto.csv.Header
import io.github.gciatto.csv.Parser
import io.github.gciatto.csv.Row
import io.github.gciatto.csv.anonymousHeader
import io.github.gciatto.csv.headerOf
import io.github.gciatto.csv.recordOf

internal abstract class AbstractParser(
    override val source: Any,
    override val configuration: Configuration
) : Parser {

    // Hooks to be overridden by sub-classes to initialise / finalise parsing (e.g. open / close files)
    protected open fun beforeParsing() = Unit
    protected open fun afterParsing() = Unit

    // Template method that splits the source into a sequence of strings (one per line)
    protected abstract fun sourceAsLines(): Sequence<String>

    // Parses the source into a sequence of rows (skipping comments, looking for at most 1 header)
    override fun parse(): Iterable<Row> = sequence {
        beforeParsing()
        try {
            var header: Header? = null
            for ((i, line) in sourceAsLines().withIndex()) {
                if (line.isBlank()) {
                    continue
                } else if (configuration.isHeader(line)) {
                    if (header == null) {
                        header = headerOf(configuration.getColumnNames(line))
                        yield(header)
                    }
                } else if (configuration.isComment(line)) {
                    continue
                } else if (configuration.isRecord(line)) {
                    val fields = configuration.getFields(line)
                    if (header == null) {
                        header = anonymousHeader(fields.size)
                        yield(header)
                    }
                    try {
                        yield(recordOf(header, fields))
                    } catch (e: IllegalArgumentException) {
                        throw IllegalStateException("Invalid CSV at line ${i + 1}: $line", e)
                    }
                } else {
                    error("Invalid CSV at line ${i + 1}: $line")
                }
            }
        } finally {
            afterParsing() // executed even in case of errors
        }
    }.asIterable()
}
