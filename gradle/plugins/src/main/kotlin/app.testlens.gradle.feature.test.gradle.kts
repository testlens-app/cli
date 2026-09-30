import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent.*

plugins { id("java") }

@Suppress("UnstableApiUsage")
testing.suites {
    withType<JvmTestSuite>().configureEach {
        // remove automatically added compile time dependencies, as we define them explicitly
        configurations.getByName(sources.implementationConfigurationName) {
            withDependencies { removeIf { it.group == "org.junit.jupiter" && it.name == "junit-jupiter" } }
        }
        dependencies {
            runtimeOnly("org.junit.jupiter:junit-jupiter-engine")
            runtimeOnly("org.junit.platform:junit-platform-reporting")
        }

        useJUnitJupiter()
        targets.configureEach {
            testTask {
                testLogging {
                    if (System.getenv("CI") != null) {
                        events = setOf(STANDARD_OUT, STANDARD_ERROR, FAILED)
                    }
                    exceptionFormat = TestExceptionFormat.FULL
                }
                jvmArgumentProviders += CommandLineArgumentProvider {
                    listOf(
                        "-Djunit.platform.reporting.open.xml.enabled=true",
                        "-Djunit.platform.reporting.output.dir=${reports.junitXml.outputLocation.get()}/fork-{uniqueNumber}",
                        "-Djunit.platform.reporting.open.xml.git.enabled=true",
                    )
                }
                systemProperty(
                    "junit.jupiter.displayname.generator.default",
                    $$"org.junit.jupiter.api.DisplayNameGenerator$ReplaceUnderscores",
                )
                systemProperty("junit.platform.discovery.issue.severity.critical", "info")
            }
        }
    }
}
