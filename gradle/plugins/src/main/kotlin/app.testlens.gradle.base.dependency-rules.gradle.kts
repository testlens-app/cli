plugins {
    id("java")
    id("org.gradlex.jvm-dependency-conflict-resolution")
}

configurations.configureEach {
    resolutionStrategy.cacheChangingModulesFor(0, "seconds")
    resolutionStrategy.cacheDynamicVersionsFor(0, "seconds")
}

dependencies {
    constraints {
        implementation("com.github.jknack:handlebars") {
            version { require("4.5.2") }
            because("workaround for CVE-2026-55760")
        }
    }
}

jvmDependencyConflicts {
    logging { enforceSlf4JSimple() }
    patch {
        align("com.github.jknack:handlebars", "com.github.jknack:handlebars-helpers")
    }
}
