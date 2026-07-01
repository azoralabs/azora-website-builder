plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    // Website scene-type ids + project-extras bridge over the SDK's universal scene model.
    compileOnly("dev.azora:azora-sdk-compiler-scene-domain:0.1.0")
    compileOnly("dev.azora:azora-sdk-core-project-domain:0.1.0")
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
}

kotlin {
    jvmToolchain(17)
}
