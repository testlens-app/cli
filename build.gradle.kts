plugins {
    id("app.testlens.gradle.component.application-cli")
}

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
    implementation(platform(libs.jackson.bom))
    implementation(platform(libs.tamboui.bom))
    compileOnly(libs.jspecify)
    runtimeOnly(libs.tamboui.panamaBackend)
    annotationProcessor(libs.picocli.codegen)

    testImplementation(libs.assertj)
    testImplementation(libs.junit.api)
    testImplementation(libs.junit.params)
    testImplementation(libs.wiremock.core)
    testImplementation(libs.wiremock.junit5)
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
    applicationDefaultJvmArgs = listOf("--enable-native-access=dev.tamboui.panama.backend")
}

tasks.test {
    systemProperty("org.slf4j.simpleLogger.defaultLogLevel", "ERROR")
}

tasks.build {
    dependsOn(gradle.includedBuild("plugins").task(":build"))
}
