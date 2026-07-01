plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(project(":domain"))

    // Universal scene model + .azn file I/O (provided by Studio at runtime).
    compileOnly("dev.azora:azora-sdk-compiler-scene-domain:0.1.0")
    compileOnly("dev.azora:azora-sdk-compiler-scene-data:0.1.0")
    compileOnly("dev.azora:azora-sdk-core-io:0.1.0")
    // CodeGenerator / CodeGeneratorImpl live here.
    compileOnly("dev.azora:azora-sdk-core-project-domain:0.1.0")
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.0")
    testImplementation("dev.azora:azora-sdk-compiler-scene-domain:0.1.0")
    testImplementation("dev.azora:azora-sdk-core-project-domain:0.1.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}
