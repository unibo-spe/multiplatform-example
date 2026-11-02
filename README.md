# Multi-platform programming example: a CSV library

Running example of the lecture on [multi-platform programming](https://unibo-spe.github.io/12-multiplatform/)
of the _Software Process Engineering_ course (University of Bologna).

A small library for parsing and formatting CSV files, which shows the two approaches discussed in the lecture:

1. __write once, build anywhere__: `csv-core` is written _once_ in [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html)
   and compiled for both the __JVM__ and __JavaScript__ (Node.js);
2. __write first, wrap elsewhere__: `csv-python` (the `jcsv` package) makes the JVM build of `csv-core` usable from __Python__,
   via [JPype](https://jpype.readthedocs.io/), with a _Pythonic_ API on top of it.

## Project structure

```
├── csv-core/                    # the multi-platform Kotlin library
│   └── src/
│       ├── commonMain/          # platform-agnostic code: entities, parsing, formatting, expect declarations
│       ├── commonTest/          # platform-agnostic tests, run on every platform
│       ├── jvmMain/             # JVM-specific code (file reading via java.io)
│       ├── jvmTest/
│       ├── jsMain/              # JS-specific code (file reading via Node's fs module)
│       └── jsTest/
├── csv-python/                  # the jcsv Python package, wrapping the JVM build of csv-core
│   ├── jcsv/                    # Pythonic API, Java ↔ Python conversions, JVM bootstrap
│   ├── tests/                   # pytest tests of the Pythonic API
│   └── build.gradle.kts         # Python project configuration, via the gradle-python-uv plugin
├── buildSrc/                    # build logic shared by the modules (fat JARs, Node.js versions)
├── gradle/libs.versions.toml    # versions of Kotlin, the JVM, Node.js, and Gradle plugins
└── .github/                     # CI: build, test on many OSs and runtimes, CI summary
```

## Requirements

Only a __JDK__ (17 or later) is needed, to run Gradle. Everything else is provisioned by the build:

- __Node.js__ is downloaded by the Kotlin Gradle plugin, to run JS code and tests;
- __uv__, and the Python versions it needs, are downloaded by the [gradle-python-uv](https://github.com/gciatto/gradle-python-uv) plugin;
- the JDK used by `jcsv` at runtime comes from the [jdk4py](https://pypi.org/project/jdk4py/) Python package.

## Usage

All operations go through the Gradle wrapper:

| Command | What it does |
|---|---|
| `./gradlew check` | Runs all checks: Kotlin tests on JVM and JS, Python tests and static checks (ruff) |
| `./gradlew jvmTest` / `jsTest` | Runs the Kotlin tests on one platform only |
| `./gradlew jsTest -PnodeVersion=22` | Runs the JS tests on another Node.js version (e.g. `22`, `latest-24`, `latest`) |
| `./gradlew :csv-python:test` | Runs the Python tests (pytest), with the default Python version |
| `./gradlew :csv-python:testAllPythonVersions` | Runs the Python tests on every supported Python version (3.10–3.14) |
| `./gradlew :csv-core:shadowJar` | Creates a fat JAR of `csv-core` (with its dependencies), in `csv-core/build/libs` |
| `./gradlew :csv-python:assemble` | Creates the `jcsv` wheel (with the fat JAR inside), in `csv-python/build/python/dist` |
| `./gradlew :csv-python:run --args="file.csv"` | Parses `file.csv` via `jcsv`, and prints it back as CSV |

The `jcsv` wheel can also be installed in any (virtual) environment, and used from Python:

```bash
pip install csv-python/build/python/dist/*.whl
python -m jcsv file.csv
```

```python
import jcsv

table = jcsv.parse_csv_file("file.csv")
print(table.header.columns)       # column names
for record in table.records:
    print(record.values)          # values of a record, as strings
```

## Continuous integration

On each push, GitHub Actions:

1. builds the project and runs all checks on Linux;
2. runs the tests extensively, on Windows, macOS, and Linux:
    + JVM tests, on Java 17, 21, and 25 (Oracle and Temurin distributions),
    + JS tests, on Node.js 22, 24, and 26,
    + Python tests, on Python 3.10–3.14;
3. publishes a __CI summary__ (test counts, OS × runtime matrix, failing tests), filled in as jobs finish,
   both as a "CI summary" check on the commit and on the run's summary page.

## License

[Apache License 2.0](LICENSE)
