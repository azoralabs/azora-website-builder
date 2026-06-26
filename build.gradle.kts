import org.gradle.api.tasks.SourceSetContainer

plugins {
    kotlin("jvm") version "2.4.0"
    kotlin("plugin.serialization") version "2.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0"
}

repositories {
    mavenCentral()
    google()
}

// The plugin entry point lives here; the data/domain/presentation modules carry the implementation.
dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":presentation"))

    compileOnly("dev.azora:azora-sdk-plugin-core:0.1.0")
    compileOnly("dev.azora:azora-sdk-core-project-domain:0.1.0")
    compileOnly("dev.azora:azora-sdk-core-io:0.1.0")

    compileOnly("org.jetbrains.compose.runtime:runtime:1.11.1")
    compileOnly("org.jetbrains.compose.foundation:foundation:1.11.1")
    compileOnly("org.jetbrains.compose.material3:material3:1.11.0-alpha07")
    compileOnly("org.jetbrains.compose.ui:ui:1.11.1")
}

kotlin {
    jvmToolchain(17)
}

// The plugin ships as a single JAR: bundle the module classes (but not the compileOnly SDK/compose,
// which Studio provides at runtime).
val pluginModules = listOf(":domain", ":data", ":presentation")
pluginModules.forEach { evaluationDependsOn(it) }

tasks.jar {
    archiveBaseName.set("dev.azora.website_builder")
    archiveVersion.set("0.0.1")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    pluginModules.forEach { path ->
        dependsOn("$path:classes")
        from(project(path).extensions.getByType(SourceSetContainer::class.java).getByName("main").output)
    }
}
