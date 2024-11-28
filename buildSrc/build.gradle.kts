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
        jvmTarget.set(targetJvm.map { JvmTarget.valueOf("JVM_$it") })
    }
}

java {
    sourceCompatibility = JavaVersion.valueOf("VERSION_${targetJvm.get()}")
    targetCompatibility = sourceCompatibility
}
