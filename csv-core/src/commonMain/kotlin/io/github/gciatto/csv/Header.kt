@file:Suppress("NON_EXPORTABLE_TYPE")

package io.github.gciatto.csv

import kotlin.js.JsExport
import kotlin.js.JsName

/** Represents the header of a CSV file, i.e. a special row containing the names of the columns. */
@JsExport
interface Header : Row {
    /** Gets the names of the columns. */
    @JsName("columns")
    val columns: List<String>

    /** Checks whether the given column name is present in this header. */
    @JsName("contains")
    operator fun contains(column: String): Boolean

    /** Gets the index of the given column name (-1 if missing). */
    @JsName("indexOf")
    fun indexOf(column: String): Int
}
