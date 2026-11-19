package nebula.plugin.resolutionrules

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.File

internal class SubstitutionRecommendationsTest {
    @TempDir
    lateinit var projectDir: File

    private fun pom(group: String, name: String, version: String, content: String = "") {
        val directory = File(projectDir, "repo/${group.replace('.', '/')}/$name/$version").apply { mkdirs() }
        File(directory, "$name-$version.pom").writeText("""
            <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>$group</groupId>
                <artifactId>$name</artifactId>
                <version>$version</version>
                $content
            </project>
        """.trimIndent())
    }

    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = [
            "recommender | (,1.4) | 1.8",
            "platform | (,1.4) | 1.8",
            "recommender | '' | 1.6",
            "platform | '' | 1.6"
        ]
    )
    fun `versionless dependencies receive recommendations unless substituted by an unversioned rule`(
        source: String,
        sourceVersion: String,
        expectedVersion: String
    ) {
        File(projectDir, "settings.gradle").writeText("rootProject.name = 'substitution-recommendations'")
        listOf("1.6", "1.8").forEach { pom("test.nebula", "apricot", it) }
        pom("test.nebula", "bom", "1.0", """
            <packaging>pom</packaging>
            <dependencyManagement>
                <dependencies>
                    <dependency>
                        <groupId>test.nebula</groupId>
                        <artifactId>apricot</artifactId>
                        <version>1.8</version>
                    </dependency>
                </dependencies>
            </dependencyManagement>
        """.trimIndent())
        val module = "test.nebula:apricot" + if (sourceVersion.isEmpty()) "" else ":$sourceVersion"
        File(projectDir, "rules.json").writeText("""
            {
                "substitute": [{
                    "module": "$module",
                    "with": "test.nebula:apricot:1.6",
                    "reason": "Use a supported version",
                    "author": "test",
                    "date": "2026-11-19"
                }]
            }
        """.trimIndent())
        val recommendations = if (source == "recommender") """
            apply plugin: 'com.netflix.nebula.dependency-recommender'
            dependencyRecommendations {
                mavenBom module: 'test.nebula:bom:1.0'
            }
        """.trimIndent() else """
            dependencies {
                implementation platform('test.nebula:bom:1.0')
            }
        """.trimIndent()
        File(projectDir, "build.gradle").writeText("""
            plugins {
                id 'java'
                id 'com.netflix.nebula.resolution-rules'
            }
            repositories { maven { url = uri('repo') } }
            $recommendations
            dependencies {
                resolutionRules files('rules.json')
                implementation 'test.nebula:apricot'
            }
        """.trimIndent())

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("dependencyInsight", "--configuration", "compileClasspath", "--dependency", "apricot", "--stacktrace")
            .build()

        assertTrue(result.output.contains("test.nebula:apricot:$expectedVersion\n"), result.output)
        if (sourceVersion.isNotEmpty()) {
            assertFalse(result.output.contains("substituted test.nebula:apricot"), result.output)
        } else {
            assertTrue(result.output.contains("substituted test.nebula:apricot"), result.output)
        }
    }
}
