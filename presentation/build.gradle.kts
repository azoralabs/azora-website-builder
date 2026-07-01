plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlin.plugin.compose")
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))

    compileOnly("dev.azora:azora-sdk-plugin-core:0.1.0")
    compileOnly("dev.azora:azora-sdk-core-project-domain:0.1.0")
    compileOnly("dev.azora:azora-sdk-core-domain:0.1.0")
    compileOnly("dev.azora:azora-sdk-core-presentation:0.1.0")
    compileOnly("dev.azora:azora-sdk-core-io:0.1.0")
    // Universal scene model + .azn file I/O (provided by Studio at runtime).
    compileOnly("dev.azora:azora-sdk-compiler-scene-domain:0.1.0")
    compileOnly("dev.azora:azora-sdk-compiler-scene-data:0.1.0")
    // SDK node-graph canvas + palette used by the .azscene visual editor.
    compileOnly("dev.azora:azora-sdk-canvas-domain:0.1.0")
    compileOnly("dev.azora:azora-sdk-canvas-presentation:0.1.0")
    compileOnly("dev.azora:azora-sdk-core-theme:0.1.0")
    // SDK inspector widgets: color picker + core component library (AzoraTextField/AzoraButton).
    compileOnly("dev.azora:azora-sdk-color-presentation:0.1.0")
    compileOnly("dev.azora:azora-sdk-core-component:0.1.0")

    compileOnly("org.jetbrains.compose.runtime:runtime:1.11.1")
    compileOnly("org.jetbrains.compose.foundation:foundation:1.11.1")
    compileOnly("org.jetbrains.compose.material3:material3:1.11.0-alpha07")
    compileOnly("org.jetbrains.compose.ui:ui:1.11.1")
}

kotlin {
    jvmToolchain(17)
}
