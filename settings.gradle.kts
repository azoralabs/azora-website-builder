rootProject.name = "azora-website-builder"

pluginManagement {
    repositories { gradlePluginPortal(); mavenCentral(); google() }
}

dependencyResolutionManagement {
    repositories { mavenCentral(); google() }
}

include(":domain", ":data", ":presentation")

// Compile against the Azora SDK via a composite build; the substitution maps our requested
// coordinates onto its modules. (The .azscene editor host lives in azora-studio's studioApp.)
includeBuild("../azora-studio") {
    dependencySubstitution {
        substitute(module("dev.azora:azora-sdk-plugin-core")).using(project(":azora-sdk-plugin:core"))
        substitute(module("dev.azora:azora-sdk-core-project-domain")).using(project(":azora-sdk-core:project:domain"))
        substitute(module("dev.azora:azora-sdk-core-io")).using(project(":azora-sdk-core:io"))
        substitute(module("dev.azora:azora-sdk-core-domain")).using(project(":azora-sdk-core:domain"))
        substitute(module("dev.azora:azora-sdk-core-theme")).using(project(":azora-sdk-core:theme"))
        substitute(module("dev.azora:azora-sdk-canvas-domain")).using(project(":azora-sdk:canvas:domain"))
        substitute(module("dev.azora:azora-sdk-canvas-presentation")).using(project(":azora-sdk:canvas:presentation"))
        // SDK widgets for the inspector: the color picker and the core component library.
        substitute(module("dev.azora:azora-sdk-color-presentation")).using(project(":azora-sdk:color:presentation"))
        substitute(module("dev.azora:azora-sdk-core-component")).using(project(":azora-sdk-core:component"))
    }
}
