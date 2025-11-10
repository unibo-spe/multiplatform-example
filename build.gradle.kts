@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    alias(libs.plugins.gitSemVer)
}

group = "io.github.gciatto"

gitSemVer {
    excludeLightweightTags()
    gitSensitiveSemanticVersion.toString()
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

afterEvaluate {
    subprojects {
        version = rootProject.version
        group = rootProject.group
    }
}
