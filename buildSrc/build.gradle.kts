import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
    alias(libs.plugins.kotlin.jvm)
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(libs.shadowJar)
    implementation(libs.kotlin.gradlePlugin)
}

val targetJvm = libs.versions.jvm

kotlin {
    compilerOptions {
        allWarningsAsErrors = true
        // kotlin-dsl still passes -Xuse-fir-lt, deprecated since Kotlin 2.4.20
        freeCompilerArgs.add("-Xwarning-level=DEPRECATED_CLI_ARG:disabled")
        jvmTarget.set(targetJvm.map { JvmTarget.valueOf("JVM_$it") })
    }
}

java {
    sourceCompatibility = JavaVersion.valueOf("VERSION_${targetJvm.get()}")
    targetCompatibility = sourceCompatibility
}
