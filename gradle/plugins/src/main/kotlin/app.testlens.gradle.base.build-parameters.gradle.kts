import app.testlens.gradle.provider.LocalPropertiesExtension

extensions.create("localProperties", LocalPropertiesExtension::class, layout.settingsDirectory)
