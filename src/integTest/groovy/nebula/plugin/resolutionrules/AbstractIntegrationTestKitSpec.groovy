package nebula.plugin.resolutionrules

import nebula.test.IntegrationTestKitSpec

abstract class AbstractIntegrationTestKitSpec extends IntegrationTestKitSpec {
    def setup() {
        // Enable configuration cache :)
        new File(projectDir, 'gradle.properties') << '''org.gradle.configuration-cache=true'''.stripIndent()
    }


    void disableConfigurationCache() {
        def propertiesFile = new File(projectDir, 'gradle.properties')
        if(propertiesFile.exists()) {
            propertiesFile.delete()
        }
        propertiesFile.createNewFile()
        propertiesFile << '''org.gradle.configuration-cache=false'''.stripIndent()
    }
}
