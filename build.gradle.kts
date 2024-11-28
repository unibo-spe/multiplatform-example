@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    alias(libs.plugins.gitSemVer)
}

group = "io.github.gciatto"

gitSemVer {
    excludeLightweightTags()
    assignGitSemanticVersion()
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
