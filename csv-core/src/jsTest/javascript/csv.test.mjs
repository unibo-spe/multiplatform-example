// Shows how the JS build of csv-core looks like from plain JavaScript (run via `./gradlew jsUsageTest`).
// Rules of thumb:
// - only @JsExport-ed declarations are visible: the vararg factories, formatAsCSV, parseAsCSV, parseCsvFile,
//   and the members of the exported interfaces (named after their @JsName, e.g. getByIndex / getByName);
// - Configuration, the Iterable/Sequence overloads, and the default implementations of Parser / Formatter are not;
// - varargs are arrays, Chars are numbers (UTF-16 codes), default arguments can be omitted;
// - Kotlin Lists are KtList objects: use asJsReadonlyArrayView() to get a (read-only) JS array;
// - Kotlin Iterables are not JS iterables: no for...of on rows or tables;
// - Kotlin exceptions are JS Errors, whose name is the Kotlin exception class.
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { mkdtempSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import {
  anonymousHeader,
  formatAsCSV,
  headerOf,
  parseAsCSV,
  parseCsvFile,
  recordOf,
  table as tableOfRecords, // Kotlin's tableOf(Header, vararg Record), renamed via @JsName("table")
  tableOf,
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

test('row', () => {
  assert.equal(alice.size, 2)
  assert.equal(alice.getByIndex(0), 'Alice')
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
  // no for...of: iterate by index
  const names = []
  for (let i = 1; i < table.size; i++) names.push(table.get(i).getByName('name'))
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
