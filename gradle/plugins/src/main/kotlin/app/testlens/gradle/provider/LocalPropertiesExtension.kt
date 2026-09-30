package app.testlens.gradle.provider

import java.util.Properties
import javax.inject.Inject
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory

abstract class LocalPropertiesExtension(rootDir: Directory) {

    @get:Inject abstract val providers: ProviderFactory

    private val properties: Properties = Properties()

    init {
        val propertiesFile = rootDir.file("local.properties")
        properties.load(providers.fileContents(propertiesFile).asText.getOrElse("").reader())
    }

    fun localProperty(name: String): Provider<String> {
        return if (properties.containsKey(name)) {
                providers.provider { properties.getProperty(name) }
            } else {
                providers.provider { null as String? }
            }
            .orElse(providers.gradleProperty(name))
    }
}
