import app.testlens.gradle.tasks.GenerateModelReflectionConfig
import kotlin.text.Charsets.UTF_8
import org.graalvm.buildtools.gradle.tasks.BuildNativeImageTask
import org.graalvm.buildtools.gradle.tasks.GenerateResourcesConfigFile

plugins {
    id("app.testlens.gradle.base.build-parameters")
    id("app.testlens.gradle.base.dependency-rules")
    id("app.testlens.gradle.base.lifecycle")
    id("app.testlens.gradle.check.dependencies")
    id("app.testlens.gradle.check.error-prone")
    id("app.testlens.gradle.check.format-base")
    id("app.testlens.gradle.check.format-gradle")
    id("app.testlens.gradle.check.format-java")
    id("app.testlens.gradle.check.javac-lint")
    id("app.testlens.gradle.feature.java-compile")
    id("app.testlens.gradle.feature.release")
    id("app.testlens.gradle.feature.test")
    id("application")
    id("org.openapi.generator")
    id("org.graalvm.buildtools.native")
}

val generateVersionFile = tasks.register("generateVersionFile") {
    inputs.property("version", provider { version })
    outputs.dir(layout.buildDirectory.dir("generated/version-file"))
    doFirst {
        outputs.files.singleFile.resolve("version.txt")
            .writeText(inputs.properties["version"] as String)
    }
}

sourceSets {
    main {
        resources.srcDir(generateVersionFile)
    }
}

val downloadOpenApiSpec =
    tasks.register("downloadOpenApiSpec") {
        val uri = "https://api.testlens.app/v1/openapi"
        inputs.property("uri", uri)
        val specFile = layout.buildDirectory.file("openapi-spec.yml")
        outputs.file(specFile)
        outputs.upToDateWhen { false } // depends on remote URL
        val resource = resources.text.fromUri(uri)
        doFirst {
            specFile.get().asFile.writer(UTF_8).use {
                resource.asReader().copyTo(it)
            }
        }
    }

openApiGenerate {
    generatorName = "java"
    library = "native"
    inputSpec.fileProvider(downloadOpenApiSpec.map { it.outputs.files.singleFile })
    outputDir = layout.buildDirectory.dir("generated/openapi")
    invokerPackage = "app.testlens.cli.client"
    apiPackage = "app.testlens.cli.client.api"
    modelPackage = "app.testlens.cli.client.model"
    configOptions =
        mapOf(
            "useJakartaEe" to "true",
            "openApiNullable" to "false",
            "hideGenerationTimestamp" to "true",
            "useJackson3" to "true",
            "useJspecify" to "true",
        )
    globalProperties =
        mapOf("apiDocs" to "false", "apiTests" to "false", "modelDocs" to "false", "modelTests" to "false")
    quiet = true
}

tasks.compileJava {
    // namespace for Picocli's annotation processor
    options.compilerArgs.add("-Aproject=app.testlens.cli")
}

val generateModelReflectionConfig =
    tasks.register<GenerateModelReflectionConfig>("generateModelReflectionConfig") {
        modelPackage = openApiGenerate.modelPackage
        sourceDirectory = tasks.openApiGenerate.flatMap { it.outputDir.dir("src/main/java") }
        outputDir = layout.buildDirectory.dir("generated/native")
        namespace = "app.testlens.cli"
    }

sourceSets.main {
    java.srcDir(tasks.openApiGenerate.flatMap { it.outputDir.dir("src/main/java") })
    resources.srcDir(generateModelReflectionConfig.flatMap { it.outputDir })
}

graalvmNative {
    metadataRepository { enabled = true }
    toolchainDetection = true
    binaries {
        named("main") {
            imageName = "testlens"
            javaLauncher = javaToolchains.launcherFor {
                nativeImageCapable = true
                languageVersion = java.toolchain.languageVersion
            }
            buildArgs.add("-Os")
            // TamboUI's Panama backend talks to the terminal via FFM downcalls
            buildArgs.addAll(
                "-H:+UnlockExperimentalVMOptions",
                "-H:+ForeignAPISupport",
                // TamboUI closes the terminal with Arena.ofShared()
                "-H:+SharedArenaSupport",
                "-H:-UnlockExperimentalVMOptions",
                // native-image puts the application on the class path
                "--enable-native-access=ALL-UNNAMED",
                // see https://tamboui.dev/docs/main/developer-guide.html#native-image-support
                "--enable-monitoring=jfr",
            )
        }
    }
}

tasks.withType<GenerateResourcesConfigFile>().configureEach {
    notCompatibleWithConfigurationCache("https://github.com/graalvm/native-build-tools/issues/477")
}

tasks.withType<BuildNativeImageTask>().configureEach {
    notCompatibleWithConfigurationCache("https://github.com/graalvm/native-build-tools/issues/477")
}
