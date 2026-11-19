package nebula.plugin.resolutionrules

import com.netflix.nebula.interop.VersionWithSelector
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

internal class SubstituteRuleTest {
    private fun rule(version: String): SubstituteRule {
        val module = if (version.isEmpty()) "test.nebula:apricot" else "test.nebula:apricot:$version"
        return SubstituteRule(module, "test.nebula:apricot:1.6", "test", "test", "test", "2026-11-19").apply {
            substitutedVersionId = module.toModuleVersionId()
            versionSelector = VersionWithSelector(version).asSelector()
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["(,1.4)", "(,1.4]", "[1.0,1.4)", "1.0"])
    fun `version qualified rules do not match versionless requests`(sourceVersion: String) {
        assertFalse(rule(sourceVersion).acceptsVersion(""))
    }

    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = [
            "(,1.4) | 1.0 | true",
            "(,1.4) | 1.4 | false",
            "(,1.4) | 1.6 | false",
            "(,1.4] | 1.4 | true",
            "1.0 | 1.0 | true",
            "1.0 | 1.2 | false",
            "(,1.4) | latest.release | false",
            "(,1.4) | 1.+ | false",
            "(,1.4) | [1.0,1.2] | false"
        ]
    )
    fun `version qualified rules only match exact requests accepted by the selector`(
        sourceVersion: String,
        requestedVersion: String,
        expected: Boolean
    ) {
        assertEquals(expected, rule(sourceVersion).acceptsVersion(requestedVersion))
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "1.0", "1.6", "latest.release", "1.+", "[1.0,1.2]"])
    fun `unversioned rules match all requested versions`(requestedVersion: String) {
        assertTrue(rule("").acceptsVersion(requestedVersion))
    }
}
