package io.github.gciatto.csv;

import kotlin.collections.CollectionsKt;
import kotlin.sequences.SequencesKt;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Shows how the JVM build of csv-core looks like from Java.
 * Rules of thumb:
 * - top-level functions/constants in Csv.kt become static members of class {@code Csv} (because of {@code @file:JvmName}),
 *   while the actual {@code parseCsvFile} (in Csv.jvm.kt) is a static member of class {@code CsvJvm};
 * - Kotlin properties become getters ({@code size} → {@code getSize()}), {@code operator fun get} is just {@code get};
 * - default arguments are NOT visible from Java (no {@code @JvmOverloads}): all arguments must be passed;
 * - Kotlin's {@code List}/{@code Iterable} are plain {@code java.util.List}/{@code java.lang.Iterable};
 *   Kotlin's {@code Sequence} is not a Java type, but can be created via {@code SequencesKt}.
 */
public class JavaUsageTest {

    private final Header header = Csv.headerOf("name", "age");
    private final Record alice = Csv.recordOf(header, "Alice", "30");
    private final Record bob = Csv.recordOf(header, "Bob", "25");
    private final Table table = Csv.tableOf(header, alice, bob);

    @Test
    public void constants() {
        assertEquals(',', Csv.DEFAULT_SEPARATOR);
        assertEquals('"', Csv.DEFAULT_DELIMITER);
        assertEquals('#', Csv.DEFAULT_COMMENT);
    }

    @Test
    public void headerFactories() {
        // vararg, Iterable, and Sequence overloads
        assertEquals(header, Csv.headerOf(List.of("name", "age")));
        assertEquals(header, Csv.headerOf(SequencesKt.sequenceOf("name", "age")));
        assertEquals(Csv.headerOf("0", "1", "2"), Csv.anonymousHeader(3));
    }

    @Test
    public void recordFactories() {
        assertEquals(alice, Csv.recordOf(header, List.of("Alice", "30")));
        assertEquals(alice, Csv.recordOf(header, SequencesKt.sequenceOf("Alice", "30")));
        // Kotlin's require(...) failures are IllegalArgumentExceptions in Java
        assertThrows(IllegalArgumentException.class, () -> Csv.recordOf(header, "Charlie"));
    }

    @Test
    public void tableFactories() {
        assertEquals(table, Csv.tableOf(header, List.of(alice, bob)));
        assertEquals(table, Csv.tableOf(header, SequencesKt.sequenceOf(alice, bob)));
        // from rows (the header is the first Header among them)
        assertEquals(table, Csv.tableOf(header, alice, bob));
        assertEquals(table, Csv.tableOf(List.<Row>of(header, alice, bob)));
        assertEquals(table, Csv.tableOf(SequencesKt.<Row>sequenceOf(header, alice, bob)));
        // with no header, an anonymous one is created
        assertEquals(Csv.anonymousHeader(2), Csv.tableOf(new Row[]{alice}).getHeader());
    }

    @Test
    public void row() {
        assertEquals(2, alice.getSize());
        assertEquals("Alice", alice.get(0));
        // Row is a java.lang.Iterable<String>, hence it works with for-each and String.join
        List<String> values = new ArrayList<>();
        for (String value : alice) {
            values.add(value);
        }
        assertEquals(List.of("Alice", "30"), values);
        assertEquals("Alice;30", String.join(";", alice));
    }

    @Test
    public void header() {
        assertEquals(List.of("name", "age"), header.getColumns());
        assertTrue(header.contains("age"));
        assertFalse(header.contains("email"));
        assertEquals(1, header.indexOf("age"));
        assertEquals(-1, header.indexOf("email"));
    }

    @Test
    public void record() {
        assertEquals(header, alice.getHeader());
        assertEquals(List.of("Alice", "30"), alice.getValues());
        assertTrue(alice.contains("Alice"));
        assertEquals("30", alice.get("age")); // get(String) by column name, get(int) by index
        assertThrows(NoSuchElementException.class, () -> alice.get("email"));
    }

    @Test
    public void table() {
        assertEquals(header, table.getHeader());
        assertEquals(List.of(alice, bob), table.getRecords());
        assertEquals(3, table.getSize()); // header included
        assertEquals(header, table.get(0));
        assertEquals(bob, table.get(2));
        // Table is a java.lang.Iterable<Row>
        List<Row> rows = new ArrayList<>();
        table.forEach(rows::add);
        assertEquals(List.of(header, alice, bob), rows);
    }

    @Test
    public void formatAndParse() {
        // no default arguments from Java: separator, delimiter, and comment must be given
        String csv = Csv.formatAsCSV(table, Csv.DEFAULT_SEPARATOR, Csv.DEFAULT_DELIMITER, Csv.DEFAULT_COMMENT);
        assertEquals("# \"name\", \"age\"\n\"Alice\", \"30\"\n\"Bob\", \"25\"", csv);
        assertEquals(table, Csv.parseAsCSV(csv, ',', '"', '#'));
        // extension functions take the receiver as first argument
        String tsv = Csv.formatAsCSV(table, '\t', '\'', '%');
        assertEquals(table, Csv.parseAsCSV(tsv, '\t', '\'', '%'));
    }

    @Test
    public void parseFile() throws IOException {
        File file = File.createTempFile("people", ".csv");
        file.deleteOnExit();
        Files.writeString(file.toPath(), Csv.formatAsCSV(table, ',', '"', '#'));
        // the actual of an expect function lives in the platform-specific file facade
        assertEquals(table, CsvJvm.parseCsvFile(file.getPath(), ',', '"', '#'));
    }

    @Test
    public void configuration() {
        // a data class: constructor, getters, equals, copy, componentN
        Configuration config = new Configuration(';', '"', '#');
        assertEquals(';', config.getSeparator());
        assertEquals(config, new Configuration(';', '"', '#'));
        assertEquals(',', config.copy(',', '"', '#').component1());

        assertTrue(config.isComment("# a comment"));
        assertEquals("a comment", config.getComment("# a comment"));
        assertNull(config.getComment("not a comment")); // String? is a nullable String in Java
        assertTrue(config.isRecord("a; b"));
        assertEquals(List.of("a", "b; c"), config.getFields("a; \"b; c\""));
        assertTrue(config.isHeader("# x; y"));
        assertEquals(List.of("x", "y"), config.getColumnNames("# x; y"));
    }

    @Test
    public void defaultParserAndFormatter() {
        Configuration config = new Configuration(';', '"', '#');
        Parser parser = Csv.parserOf("# name; age\nAlice; 30\nBob; 25", config);
        assertEquals(config, parser.getConfiguration());
        assertEquals(table, Csv.tableOf(parser.parse()));

        Formatter formatter = Csv.formatterOf(table, config);
        assertEquals(table, formatter.getSource());
        // format() returns a lazy Iterable (not a List): CollectionsKt.toList converts it
        assertEquals(
            List.of("# \"name\"; \"age\"", "\"Alice\"; \"30\"", "\"Bob\"; \"25\""),
            CollectionsKt.toList(formatter.format())
        );
    }

    /** Java classes can implement the Kotlin interfaces too: a parser reading a header line, then one record per line. */
    private record LinesParser(List<String> lines, Configuration configuration) implements Parser {
        @Override
        public Object getSource() {
            return lines;
        }

        @Override
        public Configuration getConfiguration() {
            return configuration;
        }

        @Override
        public Iterable<Row> parse() {
            Header header = Csv.headerOf(configuration.getFields(lines.get(0)));
            List<Row> rows = new ArrayList<>(List.of(header));
            for (String line : lines.subList(1, lines.size())) {
                rows.add(Csv.recordOf(header, configuration.getFields(line)));
            }
            return rows;
        }
    }

    /** ...and a formatter producing Markdown tables. */
    private record MarkdownFormatter(Iterable<Row> source) implements Formatter {
        @Override
        public Iterable<Row> getSource() {
            return source;
        }

        @Override
        public Configuration getConfiguration() {
            return new Configuration('|', '`', '#');
        }

        @Override
        public Iterable<String> format() {
            List<String> lines = new ArrayList<>();
            for (Row row : source) {
                lines.add("| " + String.join(" | ", row) + " |");
                if (row instanceof Header) {
                    lines.add("|---".repeat(row.getSize()) + "|");
                }
            }
            return lines;
        }
    }

    @Test
    public void customParserAndFormatter() {
        Parser parser = new LinesParser(List.of("name, age", "Alice, 30", "Bob, 25"), new Configuration(',', '"', '#'));
        assertEquals(table, Csv.tableOf(parser.parse()));

        Formatter formatter = new MarkdownFormatter(table);
        assertEquals(
            List.of("| name | age |", "|---|---|", "| Alice | 30 |", "| Bob | 25 |"),
            formatter.format()
        );
    }
}
