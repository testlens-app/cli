import net.ltgt.gradle.errorprone.errorprone
import net.ltgt.gradle.nullaway.nullaway

plugins {
    id("net.ltgt.errorprone")
    id("net.ltgt.nullaway")
}

dependencies {
    errorprone(dependencyFromLibs("error-prone-core"))
    errorprone(dependencyFromLibs("nullaway"))
}

tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        disableAllChecks = true
        disableWarningsInGeneratedCode = true
        nullaway {
            enable()
            onlyNullMarked = true
            jspecifyMode = true
            handleTestAssertionLibraries = true
            excludedFieldAnnotations.addAll(
                "app.testlens.server.Autoweird",
                "org.springframework.beans.factory.annotation.Autowired",
                "org.springframework.beans.factory.annotation.Value",
                "org.junit.jupiter.api.io.TempDir",
                "org.junit.jupiter.params.Parameter",
                "org.springframework.test.context.bean.override.convention.TestBean",
            )
        }
    }
}
