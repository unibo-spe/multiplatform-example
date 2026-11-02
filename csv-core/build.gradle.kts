@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin

plugins {
    kotlin("multiplatform")
}

val nodeVersion = project.findProperty("nodeVersion")?.toString()?.takeUnless { it.isBlank() }
    ?: libs.versions.node.get()

val jvmVersion = libs.versions.jvm

java {
    sourceCompatibility = JavaVersion.valueOf("VERSION_${jvmVersion.get()}")
    targetCompatibility = sourceCompatibility
}

// the Node.js version to download and use for running / testing JS code
plugins.withType<NodeJsPlugin> {
    the<NodeJsEnvSpec>().version = NodeVersions.latest(nodeVersion).also {
        println("Using Node.js version $it")
    }
}

kotlin {
    compilerOptions {
        allWarningsAsErrors = true
        freeCompilerArgs.add("-opt-in=kotlin.js.ExperimentalJsExport")
    }
    js {
        compilerOptions {
            // Row and Table extend Iterable, which is not exported to JS (KT-83009: to become an error)
            freeCompilerArgs.add("-Xwarning-level=EXPOSED_NOT_EXPORTED_SUPER_INTERFACE_WARNING:disabled")
        }
        useEsModules()
        binaries.executable()
        nodejs()
    }
    jvm {
        compilerOptions {
            jvmTarget.set(jvmVersion.map { JvmTarget.valueOf("JVM_$it") })
        }
    }
    sourceSets {
        // the Kotlin stdlib is added automatically to all source sets
        commonTest.dependencies {
            // kotlin.test, with the right platform-specific variant for each target
            implementation(kotlin("test"))
        }
    }
}

tasks.register("test") {
    dependsOn("jvmTest")
    dependsOn("jsTest")
    group = "verification"
}

tasks.register("classes") {
    dependsOn("jvmMainClasses")
    dependsOn("jsMainClasses")
    dependsOn("jvmTestClasses")
    dependsOn("jsTestClasses")
    group = "build"
}

shadowJar()
