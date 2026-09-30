package app.testlens.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.api.tasks.PathSensitivity.NAME_ONLY
import tools.jackson.databind.json.JsonMapper

/**
 * Generates GraalVM reachability metadata for every generated model class, so Jackson can (de)serialize them in the
 * native image.
 */
@CacheableTask
abstract class GenerateModelReflectionConfig : DefaultTask() {

    @get:Input abstract val modelPackage: Property<String>

    @get:Input abstract val namespace: Property<String>

    @get:PathSensitive(NAME_ONLY) @get:InputDirectory abstract val sourceDirectory: DirectoryProperty

    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val pkg = modelPackage.get()
        val modelDir = sourceDirectory.get().dir(pkg.replace('.', '/')).asFile
        val reflection =
            modelDir
                .listFiles { it.extension == "java" }
                .orEmpty()
                .map { "$pkg.${it.nameWithoutExtension}" }
                .sorted()
                .map {
                    linkedMapOf(
                        "type" to it,
                        "allDeclaredConstructors" to true,
                        "allDeclaredFields" to true,
                        "allDeclaredMethods" to true,
                    )
                }
        val root = mapOf("reflection" to reflection)

        val file = outputDir.get().file("META-INF/native-image/${namespace.get()}/reachability-metadata.json").asFile
        file.parentFile.mkdirs()
        JsonMapper().writerWithDefaultPrettyPrinter().writeValue(file, root)
    }
}
