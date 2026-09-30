plugins {
    id("java")
    id("org.gradlex.reproducible-builds")
}

val javaVersion = providers.fileContents(layout.settingsDirectory.file("gradle/java-version.txt")).asText.get().trim()

java { toolchain.languageVersion = JavaLanguageVersion.of(javaVersion) }

tasks.withType<JavaCompile>().configureEach { options.compilerArgs.add("-parameters") }
