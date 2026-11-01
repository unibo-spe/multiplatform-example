@file:Suppress("NON_EXPORTABLE_TYPE")

package io.github.gciatto.csv

import kotlin.js.JsExport
import kotlin.js.JsName

/** Represents a table, i.e. an in-memory representation of a CSV file. */
@JsExport
interface Table : Iterable<Row> {
    /** Gets the header of this table (useful to know column names). */
    @JsName("header")
    val header: Header

    /** Gets the records in this table. */
    @JsName("records")
    val records: List<Record>

    /** Gets the row at the given index (0 is the header). */
    @JsName("get")
    operator fun get(row: Int): Row

    /** Gets the number of rows in this table (header included). */
    @JsName("size")
    val size: Int
}
