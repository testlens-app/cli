plugins { id("base") }

// Convenience for local development: when running './gradlew' without parameters show the tasks...
defaultTasks("tasks")

if (gradle.startParameter.taskNames.isEmpty()) {
    // ...of group 'build' only
    tasks.withType<TaskReportTask>().configureEach { displayGroup = "build" }
}

tasks.register("qualityCheck") {
    description = "Run all quality checks."
    dependsOn(tasks.assemble)
}

tasks.register("qualityGate") {
    description = "Apply spotless rules and run all quality checks."
    dependsOn(tasks.assemble)
}

tasks.check { dependsOn(tasks.named("qualityCheck")) }

// Clean up the 'build' group so that it contains all the tasks you use in
// daily development and nothing else
afterEvaluate {
    val applicationGroup = setOf("bootRun", "bootTestRun", "run")
    val buildGroup = setOf("assemble", "build", "clean", "qualityGate")
    val helpGroup =
        setOf(
            "buildEnvironment",
            "dependencies",
            "dependencyInsight",
            "help",
            "javaToolchains",
            "outgoingVariants",
            "reason",
            "wrapper",
        )
    tasks.configureEach {
        group =
            when {
                name in applicationGroup -> "application"
                name in buildGroup || this is Test -> "build"
                name in helpGroup -> "help"
                else -> null
            }
    }
}
