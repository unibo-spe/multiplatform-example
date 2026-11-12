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

// Copies the plain-JS usage examples next to the npm workspace of the compiled library, so that they can import it by name
val prepareJsUsageTest = tasks.register<Sync>("prepareJsUsageTest") {
    dependsOn("jsProductionExecutableCompileSync", ":kotlinNpmInstall")
    from("src/jsTest/javascript")
    into(rootProject.layout.buildDirectory.dir("js/usage"))
}

// Runs the plain-JS usage examples with Node's built-in test runner (also as part of jsTest)
val jsUsageTest = tasks.register<Exec>("jsUsageTest") {
    group = "verification"
    description = "Runs the plain-JavaScript usage examples in src/jsTest/javascript."
    dependsOn(prepareJsUsageTest, "kotlinNodeJsSetup")
    val report = layout.buildDirectory.file("test-results/jsUsageTest/TEST-jsUsageTest.xml")
    workingDir(prepareJsUsageTest.map { it.destinationDir })
    executable(project.the<NodeJsEnvSpec>().executable.get())
    args("--test", "--test-reporter=spec", "--test-reporter-destination=stdout")
    args("--test-reporter=junit", "--test-reporter-destination=${report.get().asFile}")
    doFirst { report.get().asFile.parentFile.mkdirs() }
}

tasks.named("jsTest") { dependsOn(jsUsageTest) }
