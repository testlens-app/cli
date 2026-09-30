pluginManagement { includeBuild("gradle/plugins") }

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("app.testlens.gradle.feature.build-cache")
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "testlens-cli"

enableFeaturePreview("STABLE_CONFIGURATION_CACHE")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
