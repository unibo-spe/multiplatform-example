@file:Suppress("NON_EXPORTABLE_TYPE")

package io.github.gciatto.csv

import kotlin.js.JsExport
import kotlin.js.JsName

/** Represents a record in a CSV file. */
@JsExport
interface Record : Row {
    /** Gets the header of this record (useful to know column names). */
    @JsName("header")
    val header: Header

    /** Gets the values of the fields in this record. */
    @JsName("values")
    val values: List<String>

    /** Checks whether the given value is present in this record. */
    @JsName("contains")
    operator fun contains(value: String): Boolean

    /** Gets the value of the field in the given column. */
    @JsName("getByName")
    operator fun get(column: String): String
}
