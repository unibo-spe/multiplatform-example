@file:Suppress("NON_EXPORTABLE_TYPE")

package io.github.gciatto.csv

@JsExport
actual fun parseCsvFile(
    path: String,
    separator: Char,
    delimiter: Char,
    comment: Char
): Table = readFileSync(path, js("{encoding: 'utf8'}")).parseAsCSV(separator, delimiter, comment)

/**
 * Converts any Kotlin [Iterable] (e.g. a [Table], a [Row], or the output of [Parser.parse] / [Formatter.format])
 * into a JS array, as Kotlin iterables are not JS iterables.
 */
@JsExport
fun <T> toArray(iterable: Iterable<T>): Array<T> = iterable.toList().toTypedArray()
