@file:Suppress("NON_EXPORTABLE_TYPE")

package io.github.gciatto.csv

import kotlin.js.JsExport
import kotlin.js.JsName

/** Converts some source into rows, according to some [Configuration]. */
@JsExport
interface Parser {
    /** The source to be parsed by this parser (must be interpretable as string). */
    @JsName("source")
    val source: Any

    /** The configuration of this parser (i.e. the characters to be used). */
    @JsName("configuration")
    val configuration: Configuration

    /** Parses the source into a sequence of rows (one for each row in the source). */
    @JsName("parse")
    fun parse(): Iterable<Row>
}
