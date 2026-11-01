package io.github.gciatto.csv

import kotlin.random.Random

private val tmpDirectory by lazy {
    tmpdir()
}

actual fun createTempFile(name: String, content: String): String {
    val (n, e) = name.splitExtension()
    val tag = Random.nextLong().toULong()
    val path = "$tmpDirectory/$n-$tag.$e"
    writeFileSync(path, content)
    return path
}
