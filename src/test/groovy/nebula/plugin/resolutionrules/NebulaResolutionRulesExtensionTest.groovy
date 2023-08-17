package nebula.plugin.resolutionrules

import org.gradle.api.Project
import spock.lang.Specification

class NebulaResolutionRulesExtensionTest extends Specification {

    Project project = Mock(Project)

    def 'can assign values'() {
        given:
        def extension = new NebulaResolutionRulesExtension(project)

        when:
        extension.include = ['something']
        extension.optional = ['some-rule']
        extension.exclude = ['foo']

        then:
        extension.include.contains('something')
        extension.optional.contains('some-rule')
        extension.exclude.contains('foo')
    }

    def 'can assign and append to exclude value'() {
        given:
        def extension = new NebulaResolutionRulesExtension(project)

        when:
        extension.include = ['something']
        extension.include.add('else')

        extension.exclude = ['foo']
        extension.exclude.add('bar')

        extension.optional = ['some-rule']
        extension.optional.add('another-rule')


        then:
        extension.include.contains('something')
        extension.include.contains('else')
        extension.exclude.contains('foo')
        extension.exclude.contains('bar')
        extension.optional.contains('some-rule')
        extension.optional.contains('another-rule')
    }

    def 'can assign and setter does not override existing values'() {
        given:
        def extension = new NebulaResolutionRulesExtension(project)

        when:

        extension.include = ['something']
        extension.include = ['else']

        extension.exclude = ['foo']
        extension.exclude = ['bar']

        extension.optional = ['some-rule']
        extension.optional = ['another-rule']

        then:
        extension.include.contains('something')
        extension.include.contains('else')
        extension.exclude.contains('foo')
        extension.exclude.contains('bar')
        extension.optional.contains('some-rule')
        extension.optional.contains('another-rule')
    }
}
