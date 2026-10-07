import org.gradle.internal.os.OperatingSystem

plugins {
    signing
    id("org.graalvm.buildtools.native")
}

val assembleReleaseArchive = tasks.register<Zip>("assembleReleaseArchive") {
    from(tasks.named("nativeCompile"))
    into(provider { "testlens-${version}" })
    destinationDirectory = project.layout.buildDirectory.dir("release")
    val os = OperatingSystem.current()
    val osName = when {
        os.isLinux -> "linux"
        os.isMacOsX -> "macos"
        os.isWindows -> "windows"
        else -> error("Unsupported OS: $os")
    }
    val archName = System.getProperty("os.arch")
    archiveBaseName = "testlens-$osName-$archName"
    filePermissions {
        user {
            read = true
            execute = true
        }
        group.execute = true
        other.execute = true
    }
}

signing {
    useInMemoryPgpKeys(
        providers.environmentVariable("SIGNING_KEY").orNull,
        providers.environmentVariable("SIGNING_PASSPHRASE").orNull,
    )
}

tasks.register<Sign>("signReleaseArchive") {
    sign(assembleReleaseArchive.get())
}
