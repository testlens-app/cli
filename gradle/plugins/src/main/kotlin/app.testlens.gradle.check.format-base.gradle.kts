import com.diffplug.spotless.LineEnding

plugins {
    id("app.testlens.gradle.base.lifecycle")
    id("com.diffplug.spotless")
}

spotless { lineEndings = LineEnding.UNIX }

tasks.named("qualityCheck") { dependsOn(tasks.spotlessCheck) }

tasks.named("qualityGate") { dependsOn(tasks.spotlessApply) }
