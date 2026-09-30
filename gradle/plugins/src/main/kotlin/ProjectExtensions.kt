import app.testlens.gradle.provider.LocalPropertiesExtension
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.the

fun Project.dependencyFromLibs(name: String) = libsVersionCatalog.findLibrary(name).get().get().toString()

private val Project.libsVersionCatalog: VersionCatalog
    get() = the<VersionCatalogsExtension>().named("libs")

fun Project.dockerHost() =
    extensions
        .getByType<LocalPropertiesExtension>()
        .localProperty("app.testlens.podman.enabled")
        .map { it.toBoolean() }
        .filter { enabled -> enabled }
        .map {
            val os = OperatingSystem.current()
            if (os.isLinux) {
                val uid = providers.exec { commandLine("id", "-u") }.standardOutput.asText.get().trim()
                "unix:///run/user/$uid/podman/podman.sock"
            } else if (os.isMacOsX) {
                "unix:///tmp/podman.sock"
            } else {
                null
            }
        }
