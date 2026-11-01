@file:Suppress("NON_EXPORTABLE_TYPE")

package io.github.gciatto.csv

import kotlin.js.JsExport
import kotlin.js.JsName

/** Converts rows into strings, according to some [Configuration]. */
@JsExport
interface Formatter {
    /** The source of this formatter (i.e. the rows to be formatted). */
    @JsName("source")
    val source: Iterable<Row>

    /** The configuration of this formatter (i.e. the characters to be used). */
    @JsName("configuration")
    val configuration: Configuration

    /** Formats the source into a sequence of strings (one for each row in the source). */
    @JsName("format")
    fun format(): Iterable<String>
}
