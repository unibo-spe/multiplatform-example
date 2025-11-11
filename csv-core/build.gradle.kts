@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("com.github.johnrengelman.shadow")
}

val nodeVersion = project.findProperty("nodeVersion")?.toString()?.takeUnless { it.isBlank() }
    ?: libs.versions.node.get()

val jvmVersion = libs.versions.jvm

java {
    sourceCompatibility = JavaVersion.valueOf("VERSION_${jvmVersion.get()}")
    targetCompatibility = sourceCompatibility
}

kotlin {
    compilerOptions {
        allWarningsAsErrors = true
        freeCompilerArgs.add("-opt-in=kotlin.js.ExperimentalJsExport")
    }
    js {
        useEsModules()
        binaries.executable()
        nodejs {
            version = NodeVersions.latest(nodeVersion).also {
                println("Using Node.js version $it")
            }
        }
    }
    jvm {
        withJava()
        compilerOptions {
            jvmTarget.set(jvmVersion.map { JvmTarget.valueOf("JVM_$it") })
        }
    }
    sourceSets {
        commonMain {
            dependencies {
                api(libs.kotlin.stdlib)
            }
        }
        commonTest {
            dependencies {
               api(libs.kotlin.test)
            }
        }

        getByName("jvmMain") {
            dependencies {
                api(libs.kotlin.stdlib.jvm)
            }
        }

        getByName("jvmTest") {
            dependencies {
                api(libs.kotlin.test.junit)
            }
        }

        getByName("jsMain") {
            dependencies {
                api(libs.kotlin.stdlib.js)
            }
        }

        getByName("jsTest") {
            dependencies {
                api(libs.kotlin.test.js)
            }
        }
    }
}

tasks.register("test") {
    dependsOn("jvmTest")
    dependsOn("jsTest")
}

tasks.getByName("classes") {
    dependsOn("jvmMainClasses")
    dependsOn("jsMainClasses")
    dependsOn("jvmTestClasses")
    dependsOn("jsTestClasses")
}

shadowJar()
