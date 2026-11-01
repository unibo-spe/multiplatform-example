import io.github.gciatto.python.uv.tasks.PoeTask

plugins {
    id("io.github.gciatto.python-uv")
}

python {
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
val createCoreJar by tasks.registering(Copy::class) {
    group = "Python"
    val shadowJar by project(":csv-core").tasks.getting(Jar::class)
    dependsOn(shadowJar)
    from(shadowJar.archiveFile) {
        rename(".*?\\.jar", "csv.jar")
    }
    into(projectDir.resolve("jcsv/jvm"))
}

// tests, static checks, and packaging (uv build) all need csv.jar
tasks.withType<PoeTask>().configureEach { dependsOn(createCoreJar) }
tasks.named("uvBuild") { dependsOn(createCoreJar) }
