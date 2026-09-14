package nebula.plugin.resolutionrules

import nebula.test.dsl.*
import nebula.test.dsl.TestKitAssertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

internal class ResolutionRulesPluginTest {
    @TempDir
    lateinit var projectDir: File

    @Test
    fun `resolutionRules configuration can resolve a library dependency`() {
        val runner = testProject(projectDir) {
            rootProject {
                plugins {
                    id("com.netflix.nebula.resolution-rules")
                }
                dependencies {
                    add("resolutionRules", "com.netflix.nebula:gradle-resolution-rules:latest.release")
                }
            }
            subProject("sub") {
                plugins {
                    id("java")
                    id("com.netflix.nebula.resolution-rules")
                }
                repositories {
                    mavenCentral()
                }
            }
        }
        val result = runner.run("sub:dependencies", "--configuration", "resolutionRules", "--stacktrace") {
            forwardOutput()
        }
        assertThat(result)
            .hasNoProblemsReport()
            .hasNoDeprecationWarnings()
            .hasNoMutableStateWarnings()
        assertThat(result.output).contains("gradle-resolution-rules")
        runner.run(
            "sub:dependencyInsight",
            "--configuration", "resolutionRules",
            "--dependency", "gradle-resolution-rules"
        )
    }

    @Test
    fun `resolutionRules configuration can resolve a library dependency with multiple variants`() {
        val runner = testProject(projectDir) {
            rootProject {
                plugins {
                    id("com.netflix.nebula.resolution-rules")
                }
                dependencies {
                    add("resolutionRules", "com.netflix.nebula:archrules-deprecation:latest.release")
                }
            }
            subProject("sub") {
                plugins {
                    id("java")
                    id("com.netflix.nebula.resolution-rules")
                }
                repositories {
                    mavenCentral()
                }
            }
        }

        val diResult = runner.run(
            "sub:dependencyInsight",
            "--configuration", "resolutionRules",
            "--dependency", "archrules-deprecation"
        )
        assertThat(diResult.output)
            .contains("Variant runtimeElements:")
    }

    @Test
    fun `resolutionRules configuration can resolve a library dependency with newer java version`() {
        val runner = testProject(projectDir) {
            rootProject {
                plugins {
                    id("com.netflix.nebula.resolution-rules")
                }
                dependencies {
                    add("resolutionRules", "com.netflix.nebula:nebula-test:latest.release")
                }
            }
            subProject("sub") {
                plugins {
                    id("java")
                    id("com.netflix.nebula.resolution-rules")
                }
                javaToolchain(8)
                repositories {
                    mavenCentral()
                }
            }
        }

        val diResult = runner.run(
            "sub:dependencyInsight",
            "--configuration", "resolutionRules",
            "--dependency", "nebula-test"
        )
        assertThat(diResult.output)
            .contains("Variant runtimeElements:")
    }
}