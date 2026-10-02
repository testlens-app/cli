plugins {
    id("app.testlens.gradle.component.application-cli")
}

/**
 * Releasing:
 * 1. Change the version to the version that you want to release
 * 2. Commit the change, but don't push yet
 *     - commit message: "chore: release 1.x.x"
 * 3. Tag the commit as `v<version>` where `<version>` is the version you want to release
 *     - run: git tag -s v1.x.x -m v1.x.x
 * 3. Change the version to the next development SNAPSHOT version
 * 4. Commit the change:
 *     - commit message: "chore: use next snapshot version"
 * 5. Push everything by running `git push --follow-tags`
 */
version = "1.0.0-rc-1"

dependencies {
    implementation(libs.diffUtils)
    implementation(libs.jackson.annotations)
    implementation(libs.jackson.core)
    implementation(libs.jackson.databind)
    implementation(libs.jakarta.annotation.api)
    implementation(libs.picocli)
    implementation(libs.tamboui.core)
    implementation(libs.tamboui.toolkit)
    implementation(libs.tamboui.tui)
    implementation(libs.tamboui.widgets)
    implementation(platform(libs.jackson3.bom))
    implementation(platform(libs.tamboui.bom))
    compileOnly(libs.jspecify)
    runtimeOnly(libs.tamboui.panamaBackend)
    annotationProcessor(libs.picocli.codegen)

    testImplementation(libs.assertj)
    testImplementation(libs.junit.api)
    testImplementation(libs.junit.params)
    testImplementation(libs.wiremock.core)
    testImplementation(libs.wiremock.junit5)
    testImplementation(platform(libs.jackson2.bom))
    testImplementation(platform(libs.junit.bom))
    testImplementation(platform(libs.slf4j.bom))
    testImplementation(testFixtures(libs.tamboui.tui))
    testRuntimeOnly(libs.slf4j.simple)
    testRuntimeOnly(libs.wiremock.httpclient.apache5)
    testRuntimeOnly(libs.wiremock.jetty)
    testAnnotationProcessor(libs.picocli.codegen)
}

tasks.withType<JavaCompile>().configureEach {
    // java-diff-utils only ships an Automatic-Module-Name
    options.compilerArgs.add("-Xlint:-requires-automatic")
}

application {
    mainModule = "app.testlens.cli"
    mainClass = "app.testlens.cli.Testlens"
    applicationName = "testlens"
    // tamboui 0.5.0 DialogElement uses dev.tamboui.widgets.Clear, which the widgets module does not export to toolkit
    applicationDefaultJvmArgs =
        listOf(
            "--enable-native-access=dev.tamboui.panama.backend",
            "--add-exports=dev.tamboui.widgets/dev.tamboui.widgets=dev.tamboui.toolkit",
        )
}

tasks.test {
    systemProperty("org.slf4j.simpleLogger.defaultLogLevel", "ERROR")
}

tasks.build {
    dependsOn(gradle.includedBuild("plugins").task(":build"))
}
