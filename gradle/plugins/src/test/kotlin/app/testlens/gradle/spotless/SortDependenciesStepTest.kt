package app.testlens.gradle.spotless

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SortDependenciesStepTest {
    private val formatter = SortDependenciesStep().toFormatter()

    @Test
    fun `inserts blank line directly before each source set group`() {
        val input =
            """
            dependencies {
                testFixturesApi(testFixtures(projects.statisticsStore))
                testFixturesApi(libs.spring.test)
                testImplementation(testFixtures(project(path)))
                testImplementation(libs.assertj)
                api(libs.jooq)
                implementation(libs.slf4j.api)
            }
            """
                .trimIndent()
        val expected =
            """
            dependencies {
                api(libs.jooq)
                implementation(libs.slf4j.api)

                testImplementation(testFixtures(project(path)))
                testImplementation(libs.assertj)

                testFixturesApi(testFixtures(projects.statisticsStore))
                testFixturesApi(libs.spring.test)
            }
            """
                .trimIndent()
        assertEquals(expected, formatter.apply(input))
    }

    @Test
    fun `no blank line before first group and none before closing brace`() {
        val input =
            """
            dependencies {
                testFixturesApi(libs.jooq)
                testFixturesRuntimeOnly(libs.r2dbc.postgresql)
            }
            """
                .trimIndent()
        val expected =
            """
            dependencies {
                testFixturesApi(libs.jooq)
                testFixturesRuntimeOnly(libs.r2dbc.postgresql)
            }
            """
                .trimIndent()
        assertEquals(expected, formatter.apply(input))
    }

    @Test
    fun `scopes are ordered within a source set`() {
        val input =
            """
            dependencies {
                runtimeOnly(libs.reactor.core)
                compileOnly(libs.jspecify)
                api(libs.jooq)
                annotationProcessor(libs.errorprone.core)
                implementation(libs.slf4j.api)
                compileOnlyApi(libs.jsr305)
            }
            """
                .trimIndent()
        val expected =
            """
            dependencies {
                api(libs.jooq)
                implementation(libs.slf4j.api)
                compileOnlyApi(libs.jsr305)
                compileOnly(libs.jspecify)
                runtimeOnly(libs.reactor.core)
                annotationProcessor(libs.errorprone.core)
            }
            """
                .trimIndent()
        assertEquals(expected, formatter.apply(input))
    }

    @Test
    fun `project dependencies sort before library dependencies`() {
        val input =
            """
            dependencies {
                testImplementation(libs.assertj)
                testImplementation(testFixtures(projects.repositoryStore))
                testFixturesApi(libs.jooq)
                testFixturesApi(projects.persistence)
            }
            """
                .trimIndent()
        val expected =
            """
            dependencies {
                testImplementation(testFixtures(projects.repositoryStore))
                testImplementation(libs.assertj)

                testFixturesApi(projects.persistence)
                testFixturesApi(libs.jooq)
            }
            """
                .trimIndent()
        assertEquals(expected, formatter.apply(input))
    }

    @Test
    fun `no dependencies block is left unchanged`() {
        val input = "plugins { id(\"foo\") }"
        assertEquals(input, formatter.apply(input))
    }

    @Test
    fun `single line dependencies block is left unchanged`() {
        val input = "dependencies { implementation(libs.jooq) }"
        assertEquals(input, formatter.apply(input))
    }

    @Test
    fun `plain string notation is rejected`() {
        val input = "dependencies {\n    implementation(\"g:a:1\")\n}"
        assertThrows<RuntimeException> { formatter.apply(input) }
    }
}
