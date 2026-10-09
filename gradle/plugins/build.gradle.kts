plugins {
    `kotlin-dsl`
}

tasks.test {
    useJUnitPlatform()
}

dependencies {
    implementation("com.autonomousapps:dependency-analysis-gradle-plugin:3.19.2")
    implementation("com.diffplug.spotless:spotless-plugin-gradle:8.10.4")
    implementation("io.fuchs.gradle.classpath-collision-detector:classpath-collision-detector:1.0.0")
    implementation("net.ltgt.errorprone:net.ltgt.errorprone.gradle.plugin:5.1.1")
    implementation("org.graalvm.buildtools:native-gradle-plugin:1.1.14")
    implementation("net.ltgt.nullaway:net.ltgt.nullaway.gradle.plugin:3.3.0")
    implementation("org.gradlex:jvm-dependency-conflict-resolution:2.5")
    implementation("org.gradlex:reproducible-builds:1.1")
    implementation("org.openapitools:openapi-generator-gradle-plugin:7.26.0")
    implementation("com.atkinsondev.gradle:object-store-cache-plugin:3.1.0")
    implementation("tools.jackson.core:jackson-databind:3.2.3")
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    constraints {
        implementation("org.apache.commons:commons-lang3") {
            version {
                require("3.18.0")
            }
            because("Workaround for CVE-2025-48924")
        }
        implementation("io.minio:minio") {
            version {
                require("8.6.0")
            }
            because("Workaround for CVE-2025-59952")
        }
        implementation("org.bouncycastle:bcprov-jdk18on") {
            version {
                require("1.85")
            }
            because("Workaround for CVE-2026-8763")
        }
        implementation("com.fasterxml.jackson.core:jackson-databind") {
            version {
                require("2.22.3")
            }
            because("Workaround for CVE-2026-68497")
        }
        implementation("com.github.jknack:handlebars") {
            version {
                require("4.5.2")
            }
            because("workaround for CVE-2026-55760")
        }
    }
}
