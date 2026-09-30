plugins { id("com.diffplug.spotless") }

spotless {
    java {
        targetExclude("build/**/*.java")
        importOrder()
        removeUnusedImports()
        eclipse().configFile(layout.settingsDirectory.file("gradle/config/eclipse-formatter.xml"))
    }
}
