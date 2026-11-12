// Shows how the JS build of csv-core looks like from plain JavaScript (run via `./gradlew jsUsageTest`).
// Rules of thumb:
// - only @JsExport-ed declarations are visible: members of exported interfaces are named after their @JsName
//   (e.g. getByIndex / getByName), and the Iterable / Sequence overloads are not exported (varargs cover them);
// - varargs are arrays, Chars are numbers (UTF-16 codes), default arguments can be omitted;
// - Kotlin Lists are KtList objects: use asJsReadonlyArrayView() to get a (read-only) JS array;
// - Kotlin Iterables are not JS iterables: use toArray() (from jsMain) to iterate over rows, tables, etc.;
// - Kotlin exceptions are JS Errors, whose name is the Kotlin exception class.
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { mkdtempSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import {
  anonymousHeader,
  Configuration,
  DEFAULT_COMMENT,
  DEFAULT_DELIMITER,
  DEFAULT_SEPARATOR,
  formatAsCSV,
  formatterOf,
  headerOf,
  parseAsCSV,
  parseCsvFile,
  parserOf,
  recordOf,
  table as tableOfRecords, // Kotlin's tableOf(Header, vararg Record), renamed via @JsName("table")
  tableOf,
  toArray,
} from 'multiplatform-example-csv-core'

const char = (c) => c.charCodeAt(0)
const array = (ktList) => [...ktList.asJsReadonlyArrayView()]

const header = headerOf(['name', 'age'])
const alice = recordOf(header, ['Alice', '30'])
const bob = recordOf(header, ['Bob', '25'])
const table = tableOfRecords(header, [alice, bob])

test('factories', () => {
  // equals and toString come from Kotlin
  assert.ok(table.equals(tableOf([header, alice, bob])))
  assert.ok(headerOf(['0', '1', '2']).equals(anonymousHeader(3)))
  assert.equal(tableOf([alice]).header.toString(), 'Header("0", "1")')
  assert.throws(() => recordOf(header, ['Charlie']), { name: 'IllegalArgumentException' })
})

test('constants', () => {
  // top-level properties are exported as objects with a get() method
  assert.equal(DEFAULT_SEPARATOR.get(), char(','))
  assert.equal(DEFAULT_DELIMITER.get(), char('"'))
  assert.equal(DEFAULT_COMMENT.get(), char('#'))
})

test('row', () => {
  assert.equal(alice.size, 2)
  assert.equal(alice.getByIndex(0), 'Alice')
  assert.deepEqual(toArray(alice), ['Alice', '30'])
})

test('header', () => {
  assert.deepEqual(array(header.columns), ['name', 'age'])
  assert.ok(header.contains('age'))
  assert.ok(!header.contains('email'))
  assert.equal(header.indexOf('age'), 1)
  assert.equal(header.indexOf('email'), -1)
})

test('record', () => {
  assert.ok(alice.header.equals(header))
  assert.deepEqual(array(alice.values), ['Alice', '30'])
  assert.ok(alice.contains('Alice'))
  assert.equal(alice.getByName('age'), '30')
  assert.throws(() => alice.getByName('email'), { name: 'NoSuchElementException' })
})

test('table', () => {
  assert.ok(table.header.equals(header))
  assert.equal(array(table.records).length, 2)
  assert.equal(table.size, 3) // header included
  assert.ok(table.get(0).equals(header))
  assert.ok(table.get(2).equals(bob))
  // for...of works on the array returned by toArray
  const names = []
  for (const row of toArray(table).slice(1)) names.push(row.getByName('name'))
  assert.deepEqual(names, ['Alice', 'Bob'])
})

test('format and parse', () => {
  // default arguments can be omitted
  const csv = formatAsCSV(table)
  assert.equal(csv, '# "name", "age"\n"Alice", "30"\n"Bob", "25"')
  assert.ok(parseAsCSV(csv).equals(table))
  // extension functions take the receiver as first argument; chars are passed as numbers
  const tsv = formatAsCSV(table, char('\t'), char("'"), char('%'))
  assert.ok(parseAsCSV(tsv, char('\t'), char("'"), char('%')).equals(table))
})

test('parse file', () => {
  const path = join(mkdtempSync(join(tmpdir(), 'csv-')), 'people.csv')
  writeFileSync(path, formatAsCSV(table))
  assert.ok(parseCsvFile(path).equals(table))
})

test('configuration', () => {
  // a data class: constructor, properties, equals, copy (no componentN in JS)
  const config = new Configuration(char(';'), char('"'), char('#'))
  assert.equal(config.separator, char(';'))
  assert.ok(config.equals(new Configuration(char(';'), char('"'), char('#'))))
  assert.equal(config.copy(char(',')).separator, char(','))

  assert.ok(config.isComment('# a comment'))
  assert.equal(config.getComment('# a comment'), 'a comment')
  assert.equal(config.getComment('not a comment'), null) // String? is string | null
  assert.ok(config.isRecord('a; b'))
  assert.deepEqual(array(config.getFields('a; "b; c"')), ['a', 'b; c'])
  assert.ok(config.isHeader('# x; y'))
  assert.deepEqual(array(config.getColumnNames('# x; y')), ['x', 'y'])
})

test('parser and formatter', () => {
  const config = new Configuration(char(';'), char('"'), char('#'))
  const parser = parserOf('# name; age\nAlice; 30\nBob; 25', config)
  assert.equal(parser.source, '# name; age\nAlice; 30\nBob; 25')
  assert.ok(parser.configuration.equals(config))
  assert.ok(tableOf(toArray(parser.parse())).equals(table))

  const formatter = formatterOf(table, config)
  assert.ok(formatter.configuration.equals(config))
  assert.ok(formatter.source.equals(table))
  assert.deepEqual(toArray(formatter.format()), ['# "name"; "age"', '"Alice"; "30"', '"Bob"; "25"'])
})
