import java.io.ByteArrayOutputStream
import java.io.OutputStream


tasks.create<Copy>("createCoreJar") {
    group = "Python"
    val shadowJar by project(":csv-core").tasks.getting(Jar::class)
    dependsOn(shadowJar)
    from(shadowJar.archiveFile) {
        rename(".*?\\.jar", "csv.jar")
    }
    into(projectDir.resolve("jcsv/jvm"))
}

fun findExecutablePath(
    name: String,
    vararg otherNames: String,
    test: (File) -> Boolean = { true }
): File? {
    val names = listOf(name, *otherNames).flatMap { listOf(it, "$it.exe") }
    return System.getenv("PATH")
        .split(File.pathSeparatorChar)
        .asSequence()
        .map { File(it) }
        .flatMap { path -> names.asSequence().map { path.resolve(it) } }
        .filter { it.exists() }
        .filter(test)
        .firstOrNull()
}

val globalPython = findExecutablePath("python3", "python") { path ->
   exec {
       errorOutput = OutputStream.nullOutputStream()
       standardOutput = OutputStream.nullOutputStream()
       commandLine(path, "--version")
   }.exitValue == 0
}?.absolutePath

val localPythonEnvRoot = projectDir.resolve("build").resolve("python")

val localPython
    get() = fileTree(localPythonEnvRoot) {
        include("**/python")
        include("**/python.exe")
    }.firstOrNull()?.absolutePath

val python
    get() = localPython ?: globalPython ?: error("Python executable not found")

fun pyTask(name: String, vararg args: String, conf: Exec.() -> Unit = {}) =
    tasks.create<Exec>(name) {
        workingDir(projectDir)
        group = "Python"
        commandLine(python, *args)
        conf(this)
    }

pyTask("createVirtualEnv", "-m", "venv", localPythonEnvRoot.path) {
    outputs.dir(localPythonEnvRoot)
    doLast {
        when (val path = localPython) {
            null -> error("Virtual environment creation failed")
            else -> println("Created local Python environment in $path")
        }
    }
}

pyTask("restoreDependencies", "-m", "pip", "install", "-r", "requirements.txt") {
    dependsOn("createVirtualEnv")
}

pyTask("pythonTest", "-m", "unittest", "-v") {
    dependsOn("restoreDependencies")
    dependsOn("createCoreJar")
}

tasks.create("test") {
    group = "Verification"
    dependsOn("pythonTest")
}

tasks.create("check") {
    group = "Verification"
    dependsOn("test")
}

exec {
    println("Using interpreter: $python")
    commandLine(python, "--version")
}
