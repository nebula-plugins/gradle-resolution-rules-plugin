package nebula.plugin.resolutionrules


import nebula.test.IntegrationTestKitSpec
import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator
import spock.lang.Ignore
import spock.lang.Unroll

/**
 * This covers hierarchical replacement rules that allow for dependencies to be replaced in a more complex fashion
 *
 * This test classes references letters related to dependencies and replacement rules so that
 * the listing of tests run is more understandable rather than using the full similar-looking dependency name each time
 */
class ReplaceRulesHierarchySpec extends IntegrationTestKitSpec {

    private static final HashMap<String, String> dependenciesMap = [
            "a": "implementation 'test.a:oss:1.0.0'",
            "b": "implementation 'test.b:oss-lite:1.0.0'",
            "c": "implementation 'test.c:internal-lite:1.0.0'",
            "d": "implementation 'test.d:internal:1.0.0'"
    ]
    private static final HashMap<String, String> fileMap = [
            "a": "a-replace-oss-to-internal.json",
            "b": "b-replace-oss-lite-to-oss.json",
            "c": "c-replace-internal-lite-to-internal.json",
            "d": "d-replace-oss-lite-to-internal-lite.json",
            "e": "e-replace-oss-to-internal-lite.json", // this one is more problematic as you'll be missing something in both cases
            "f": "f-replace-oss-lite-to-internal.json"
    ]
    private static final String ALL_IN_ONE_FILE = "rules.json"
    private static final HashMap<String, String> ruleMap = [
            "a": replaceOssToInternalRule(),
            "b": replaceOssLiteToOssRule(),
            "c": replaceInternalLiteToInternalRule(),
            "d": replaceOssLiteToInternalLiteRule(),
            "e": replaceOssToInternalLiteRule(),
            "f": replaceOssLiteToInternalRule()
    ]
    private static final List permutationsToValidate = ["a", "b", "c", "d", "e", "f"].permutations().toList()

    def setupSpec() {
        Collections.shuffle(permutationsToValidate)

        def graph = new DependencyGraphBuilder()
                .addModule('test.a:oss:1.0.0')
                .addModule('test.b:oss-lite:1.0.0')
                .addModule('test.c:internal-lite:1.0.0')
                .addModule('test.d:internal:1.0.0')
                .build()

        def repoFolder = "build/nebulatest/${this.getClass().name}/repo" // generate this only once for the spec
        def mavenrepo = new GradleDependencyGenerator(graph, repoFolder)
        mavenrepo.generateTestMavenRepo()

        ["a", "b", "c", "d", "e", "f"].each { createRuleFile(it) }
    }

    def setup() {
        buildFile << """
            plugins { 
                id 'java'
                id 'nebula.resolution-rules'
            }
            repositories {
                maven { url '../repo/mavenrepo' }
            }
            """.stripIndent()
    }

    @Unroll
    def 'variation in file ordering #order for related dependencies'(List<String> order) {
        given:
        // requires all dependencies related to the replacement rules
        buildFile << """
            dependencies {
            ${order.collect { "resolutionRules files(\"../${fileMap[it]}\")" }.join("\n")}
            }
            dependencies {
                implementation 'test.a:oss:1.0.0'
                implementation 'test.b:oss-lite:1.0.0'
                implementation 'test.c:internal-lite:1.0.0'
                implementation 'test.d:internal:1.0.0'
            }
            """.stripIndent()

        when:
        def result = runTasks('dependencyInsight', '--dependency', 'test')

        then:
        result.output.contains('test.d:internal:1.0.0\n')
        result.output.contains('test.a:oss:1.0.0 -> test.d:internal:1.0.0')
        result.output.contains('test.b:oss-lite:1.0.0 -> test.d:internal:1.0.0')
        result.output.contains('test.c:internal-lite:1.0.0 -> test.d:internal:1.0.0')
        result.output.contains('replaced test.a:oss -> test.d:internal because \'test.a:oss should be replaced with test.d:internal\' by rule')

        where:
        order << permutationsToValidate.subList(0, 20) // only run on x-number permutations rather than all of them
    }

    @Unroll
    def 'variation in dependencies requested #deps'(List deps) {
        // requires exactly 2 out of 4 dependencies related to the replacement rules
        given:
        def order = "abcdef"

        buildFile << """
            dependencies {
            ${order.split("").collect { "resolutionRules files(\"../${fileMap[it]}\")" }.join("\n")}
            }
            dependencies {
            ${deps.collect { dependenciesMap[it] }.join("\n")}
            }
            """.stripIndent()

        when:
        def result = runTasks('dependencyInsight', '--dependency', 'test')

        then:
        def validReplacements = [
                'test.a:oss:1.0.0 -> test.d:internal:1.0.0',
                'test.a:oss:1.0.0 -> test.c:internal-lite:1.0.0',
                'test.b:oss-lite:1.0.0 -> test.a:oss:1.0.0',
                'test.b:oss-lite:1.0.0 -> test.c:internal-lite:1.0.0',
                'test.b:oss-lite:1.0.0 -> test.d:internal:1.0.0',
                'test.c:internal-lite:1.0.0 -> test.d:internal:1.0.0'
        ]

        assert validReplacements.any { result.output.contains(it) }

        if (deps.contains("d")) {
            result.output.contains('test.d:internal:1.0.0\n')
        }

        where:
        deps << [["a", "b", "c", "d"], ["a", "b", "c", "d"]].combinations() - [["a", "a"], ["b", "b"], ["c", "c"], ["d", "d"]]
    }

    @Ignore("These tests are for documentation that this setup is an option, but does work in an order-independent manner")
    @Unroll
    def 'variation in rule ordering #order in one file for related dependencies (THIS DOES NOT WORK INDEPENDENT OF ORDER)'(List<String> order) {
        given:
        buildFile << """
            dependencies {
                resolutionRules files("$ALL_IN_ONE_FILE")
            }
            
            dependencies {
                implementation 'test.a:oss:1.0.0'
                implementation 'test.b:oss-lite:1.0.0'
                implementation 'test.c:internal-lite:1.0.0'
                implementation 'test.d:internal:1.0.0'
            }
            """.stripIndent()

        File rulesJson = new File(projectDir, ALL_IN_ONE_FILE)
        rulesJson << """
            {
                "replace": [
                    ${order.collect { ruleMap[it] }.join(",")}
                ]
            }
            """.stripIndent()

        when:
        def result = runTasks('dependencyInsight', '--dependency', 'test')

        then:
        result.output.contains('test.d:internal:1.0.0\n')
        result.output.contains('test.a:oss:1.0.0 -> test.d:internal:1.0.0')
        result.output.contains('test.b:oss-lite:1.0.0 -> test.d:internal:1.0.0')
        result.output.contains('test.c:internal-lite:1.0.0 -> test.d:internal:1.0.0')
        result.output.contains('On capability')

        where:
        order << permutationsToValidate.subList(0, 20) // only run on x-number permutations rather than all of them
    }

    private void createRuleFile(String lookupCharacter) {
        // make the 1-rule-per-file files only once for the spec
        File rules = new File("build/nebulatest/${this.getClass().name}", fileMap[lookupCharacter])
        rules.delete()
        rules.createNewFile()
        rules << """
            {
                "replace": [
                    ${ruleMap[lookupCharacter]}
                ]
            }
            """.stripIndent()
    }

    private static String replaceOssToInternalRule() {
        return """{
                "module" : "test.a:oss",
                "with" : "test.d:internal",
                "reason" : "test.a:oss should be replaced with test.d:internal",
                "author" : "Example Person <person@example.org>",
                "date" : "2021-07-07T17:21:20.368Z"
            }""".stripIndent()
    }

    private static String replaceOssLiteToOssRule() {
        return """{
                "module" : "test.b:oss-lite",
                "with" : "test.a:oss",
                "reason" : "test.b:oss-lite should be replaced with test.a:oss",
                "author" : "Example Person <person@example.org>",
                "date" : "2021-07-07T17:21:20.368Z"
            }""".stripIndent()
    }

    private static String replaceInternalLiteToInternalRule() {
        return """{
                "module" : "test.c:internal-lite",
                "with" : "test.d:internal",
                "reason" : "test.c:internal-lite should be replaced with test.d:internal",
                "author" : "Example Person <person@example.org>",
                "date" : "2021-07-07T17:21:20.368Z"
            }""".stripIndent()
    }

    private static String replaceOssLiteToInternalLiteRule() {
        return """{
                "module" : "test.b:oss-lite",
                "with" : "test.c:internal-lite",
                "reason" : "test.b:oss-lite should be replaced with test.c:internal-lite",
                "author" : "Example Person <person@example.org>",
                "date" : "2021-07-07T17:21:20.368Z"
            }""".stripIndent()
    }

    private static String replaceOssToInternalLiteRule() {
        return """{
                "module" : "test.a:oss",
                "with" : "test.c:internal-lite",
                "reason" : "test.a:oss should be replaced with test.c:internal-lite",
                "author" : "Example Person <person@example.org>",
                "date" : "2021-07-07T17:21:20.368Z"
            }""".stripIndent()
    }

    private static String replaceOssLiteToInternalRule() {
        return """{
                "module" : "test.b:oss-lite",
                "with" : "test.d:internal",
                "reason" : "test.b:oss-lite should be replaced with test.d:internal",
                "author" : "Example Person <person@example.org>",
                "date" : "2021-07-07T17:21:20.368Z"
            }""".stripIndent()
    }
}
