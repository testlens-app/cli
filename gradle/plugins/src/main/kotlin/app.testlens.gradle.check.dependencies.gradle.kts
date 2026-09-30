import com.autonomousapps.DependencyAnalysisExtension
import com.autonomousapps.tasks.ProjectHealthTask

plugins {
    id("java")
    id("app.testlens.gradle.base.lifecycle")
    id("com.autonomousapps.dependency-analysis")
    id("io.fuchs.gradle.classpath-collision-detector")
}

tasks.detectCollisions { collisionFilter.include("**/*.class") }

configure<DependencyAnalysisExtension> {
    issues {
        all {
            onAny {
                // Configure the dependency analysis plugin to fail if issues are found
                severity("fail")
            }
            onCompileOnly {
                // @PostConstruct/@PreDestroy must be present at runtime for Spring to see them via reflection
                exclude(dependencyFromLibs("jakarta-annotation-api"))
            }
        }
    }
}

tasks.named("qualityCheck") {
    dependsOn(tasks.detectCollisions)
    dependsOn(tasks.withType<ProjectHealthTask>())
}

tasks.named("qualityGate") {
    dependsOn(tasks.detectCollisions)
    dependsOn(tasks.withType<ProjectHealthTask>())
}
