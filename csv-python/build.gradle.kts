import io.github.gciatto.python.uv.tasks.PoeTask

plugins {
    alias(libs.plugins.python.uv)
}

python {
    // each tested via testPython<X_Y>, in its own virtual environment
    testedPythonVersions("3.10", "3.11", "3.12", "3.13", "3.14")
    pyproject {
        name = "jcsv"
        description = "Pythonic wrapper of a Kotlin/JVM CSV library, via JPype"
        dependency("JPype1>=1.7.1")
        dependency("jdk4py>=25.0.2.1")
        // no mypy: JPype customisers call Java methods that only exist at runtime
        poeSequence("static-checks", "compile", "format-check", "ruff-check")
    }
}

// Copies the fat JAR of :csv-core into the Python package, as jcsv/jvm/csv.jar
val createCoreJar = tasks.register<Copy>("createCoreJar") {
    group = "Python"
    val shadowJar = project(":csv-core").tasks.named<Jar>("shadowJar")
    dependsOn(shadowJar)
    from(shadowJar.flatMap { it.archiveFile }) {
        rename(".*?\\.jar", "csv.jar")
    }
    into(projectDir.resolve("jcsv/jvm"))
}

// tests, static checks, and packaging (uv build) all need csv.jar
tasks.withType<PoeTask>().configureEach { dependsOn(createCoreJar) }
tasks.named("uvBuild") { dependsOn(createCoreJar) }
