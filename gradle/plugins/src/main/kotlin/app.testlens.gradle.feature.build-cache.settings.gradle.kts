import app.testlens.gradle.provider.LocalPropertiesExtension

plugins { id("com.atkinsondev.object-store-cache") }

@Suppress("UnstableApiUsage")
val localProperties = extensions.create("localProperties", LocalPropertiesExtension::class, layout.settingsDirectory)

buildCache {
    remote<com.atkinsondev.cache.ObjectStoreBuildCache> {
        endpoint = "fsn1.your-objectstorage.com"
        accessKey =
            providers
                .environmentVariable("BUILD_CACHE_ACCESS_KEY")
                .orElse(localProperties.localProperty("app.testlens.build-cache.access-key"))
                .orNull
        secretKey =
            providers
                .environmentVariable("BUILD_CACHE_SECRET_KEY")
                .orElse(localProperties.localProperty("app.testlens.build-cache.secret-key"))
                .orNull
        bucket = providers
                .environmentVariable("BUILD_CACHE_BUCKET")
                .orElse(localProperties.localProperty("app.testlens.build-cache.bucket"))
                .getOrElse("")
        expirationInDays = 28
        isPush = providers.environmentVariable("CI").getOrElse("false").toBoolean()
        isEnabled = bucket != "" && accessKey != null && secretKey != null
    }
}
