package io.github.gciatto.csv

import kotlin.js.JsExport
import kotlin.js.JsName

/** Represents a single row in a CSV file. */
@JsExport
interface Row : Iterable<String> {
    /** Gets the value of the field at the given index. */
    @JsName("getByIndex")
    operator fun get(index: Int): String

    /** Gets the number of fields in this row. */
    @JsName("size")
    val size: Int
}
