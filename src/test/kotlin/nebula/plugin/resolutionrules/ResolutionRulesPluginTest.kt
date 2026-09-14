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
                    add("resolutionRules", project(":rules"))
                }
            }
            subProject("rules") {
                plugins {
                    id("java-library")
                }
            }
            subProject("sub") {
                plugins {
                    id("java")
                    id("com.netflix.nebula.resolution-rules")
                }
            }
        }
        val result = runner.run("sub:dependencies", "--configuration", "resolutionRules", "--stacktrace")
        assertThat(result)
            .hasNoProblemsReport()
            .hasNoDeprecationWarnings()
            .hasNoMutableStateWarnings()
        assertThat(result.output).contains("project ':rules'")
        runner.run("sub:dependencyInsight", "--configuration", "resolutionRules", "--dependency", "rules"){
            forwardOutput()
        }
    }

    @Test
    fun `resolutionRules configuration can resolve a library dependency with multiple variants`() {
        val runner = testProject(projectDir) {
            rootProject {
                plugins {
                    id("com.netflix.nebula.resolution-rules")
                }
                dependencies {
                    add("resolutionRules", project(":rules"))
                }
            }
            subProject("rules") {
                plugins {
                    id("java-library")
                }
                //language=kotlin
                rawBuildScript("""
java {
    val featureSourceSet = sourceSets.create("feature")
    val feature = org.gradle.api.plugins.jvm.internal.DefaultJvmFeature(
        "feature", 
        featureSourceSet, 
        setOf(), 
        project as org.gradle.api.internal.project.ProjectInternal, 
        false)
    org.gradle.api.plugins.internal.JavaPluginHelper.getJavaComponent(project).getFeatures().add(feature)
    val attr = Attribute.of("feature", String::class.java)
    project.configurations.named(featureSourceSet.runtimeElementsConfigurationName) {
        attributes { 
            attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage::class.java, "feature-runtime"))
       }
    }
    project.configurations.named(featureSourceSet.apiElementsConfigurationName) {
        attributes { 
            attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage::class.java, "feature-api"))
       }
    }
}
""")
            }
            subProject("sub") {
                plugins {
                    id("java")
                    id("com.netflix.nebula.resolution-rules")
                }
            }
        }

        val diResult = runner.run("sub:dependencyInsight", "--configuration", "resolutionRules", "--dependency", "rules"){
            forwardOutput()
        }
        assertThat(diResult.output)
            .contains("Variant runtimeElements:")
    }

    @Test
    fun `resolutionRules configuration can resolve a library dependency with different java version`() {
        val runner = testProject(projectDir) {
            rootProject {
                plugins {
                    id("com.netflix.nebula.resolution-rules")
                }
                dependencies {
                    add("resolutionRules", project(":rules"))
                }
            }
            subProject("rules") {
                plugins {
                    id("java-library")
                }
                javaToolchain(8)
            }
            subProject("sub") {
                plugins {
                    id("java")
                    id("com.netflix.nebula.resolution-rules")
                }
                javaToolchain(17)
            }
        }

        val diResult = runner.run("sub:dependencyInsight", "--configuration", "resolutionRules", "--dependency", "rules"){
            forwardOutput()
        }
        assertThat(diResult.output)
            .contains("Variant runtimeElements:")
    }
}