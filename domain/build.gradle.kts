plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    // Website domain models build on the SDK's WebComponent + project model (provided at runtime).
    compileOnly("dev.azora:azora-sdk-core-project-domain:0.1.0")
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
}

kotlin {
    jvmToolchain(17)
}
