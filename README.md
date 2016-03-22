# Gradle resolution-rules Plugin

![Version](https://img.shields.io/maven-central/v/com.netflix.nebula/gradle-resolution-rules-plugin.svg)
[![Build Status](https://travis-ci.org/nebula-plugins/gradle-resolution-rules-plugin.svg?branch=master)](https://travis-ci.org/nebula-plugins/gradle-resolution-rules-plugin)
[![Coverage Status](https://coveralls.io/repos/nebula-plugins/gradle-resolution-rules-plugin/badge.svg?branch=master&service=github)](https://coveralls.io/github/nebula-plugins/gradle-resolution-rules-plugin?branch=master)
[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/nebula-plugins/gradle-resolution-rules-plugin?utm_source=badgeutm_medium=badgeutm_campaign=pr-badge)
[![Apache 2.0](https://img.shields.io/github/license/nebula-plugins/gradle-resolution-rules-plugin.svg)](http://www.apache.org/licenses/LICENSE-2.0)

Gradle plugin for providing dependency resolution rules.

Gradle resolution strategies and module metadata provide an effective way to mediate the dependency resolution process in your builds, however they don't adapt well to enterprises that need a curated and shared source for rules.

The [Gradle resolution-rules Plugin](https://github.com/nebula-plugins/gradle-resolution-rules-plugin) allows this problem to be solved with enterprise plugins for specific cases. This plugin provides general purpose rule types and supports artifact based configuration, allowing rules to be versioned and dependency locked.

# Usage

```groovy
    buildscript {
        repositories {
            jcenter()
        }

        dependencies {
            classpath 'com.netflix.nebula:gradle-resolution-rules-plugin:1.1.3'
        }
    }

    apply plugin: 'nebula.resolution-rules'
```

Or using the Gradle plugin portal:

```groovy
    plugins {
        id 'nebula.resolution-rules' version '1.1.3'
    }
```

# Dependency rules types

## Replace
 
Replace rules cause dependencies to be replaced with one at different coordinates, if both dependencies are present, avoiding class conflicts due to coordinate relocations.

## Substitute

Dependency is force replaced with new coordinates, regardless if the new dependency is visible in the graph.

## Deny

Deny rules fail the build if the specified dependency is present, forcing consumers to make a decision about the replacement of a dependency.

Versions are optional, and access to an entire dependency can be denied. This is particularly useful for 'bundle' style dependencies, which fat jar dependencies without shading classes.
 
## Reject 

Reject rules prevent a dependency version from being considered by dynamic version or `latest.*` version calculation. It does not prevent that dependency from being used if it's explicitly requested by the project.

## Align

Align rules allows the user to have a group of dependencies if present to have the same version. Example: if `jersey-core` and `jersey-server` are present make sure they use the same version. By default this will choose the largest version provided among the list.

This rule has different options depending on your use case. `includes` and `excludes` are mutually exclusive, the plugin will error out if both are present in one rule.

#### Lock only a few dependencies in a group

```json
    {
        "align": [
            {
                "group": "example.foo",
                "includes": [ "bar", "baz" ]
            }
        ]
    }
```

#### Lock most dependencies in a group, but exclude some

```json
    {
        "align": [
            {
                "group": "example.bar",
                "excludes": [ "qux", "thud" ]
            }
        ]
    }
```

#### Align along the major version, instead of an exact

```json
    {
        "align": [
            {
                "group": "example.baz",
                "along": "major"
            }
        ]
    }
```

#### Align along the minor version, instead of an exact

```json
    {
        "align": [
            {
                "group": "example.baz",
                "along": "minor"
            }
        ]
    }
```

# Consuming rules

Dependency rules are read from the `resolutionRules` configuration. Zip and jar archives are supported, as are flat JSON files. JSON files within archives can at any directory level, and more than one json file can be provided.

```groovy
    dependencies {
        resolutionRules files('local-rules.json')
        resolutionRules 'com.myorg:resolution-rules:latest.release'
    }
```
    
# Producing rules

The `nebula.resolution-rules-producer` plugin is provided to facilitate creation of rule files. This plugin can be used to validate and package rule files. To use, put your rules file in `src/resolutionRules/resolution-rules.json` and execute the packageRules task. If successful, a jar with the validated rules file will be placed in your build directory. For specifying more than one source file, see documentation below.

## Usage

```groovy
    buildscript {
        repositories {
            jcenter()
        }

        dependencies {
            classpath 'com.netflix.nebula:gradle-resolution-rules-plugin:1.1.3'
        }
    }

    apply plugin: 'nebula.resolution-rules-producer'
```    

Or using the Gradle plugin portal:

```groovy
    plugins {
        id 'nebula.resolution-rules-producer' version '1.1.3'
    }
```


Once configured, run the following:

    $ ./gradlew packageRules
    

## Customizing rule file locations

```groovy
    checkResolutionRulesSyntax {
        rules files('./alternative-rules.json', './src/rules/moreRules.json')
    }
```

# Example rules JSON

```json
    {
        "replace" : [
            {
                "module" : "asm:asm",
                "with" : "org.ow2.asm:asm",
                "reason" : "The asm group id changed for 4.0 and later",
                "author" : "Danny Thomas <dmthomas@gmail.com>",
                "date" : "2015-10-07T20:21:20.368Z"
            }
        ],
        "substitute": [
            {
                "module" : "bouncycastle:bcprov-jdk15",
                "with" : "org.bouncycastle:bcprov-jdk15:latest.release",
                "reason" : "The latest version of BC is required, using the new coordinate",
                "author" : "Danny Thomas <dmthomas@gmail.com>",
                "date" : "2015-10-07T20:21:20.368Z"
            }
        ],
        "deny": [
            {
                "module": "com.google.guava:guava:19.0-rc2",
                "reason" : "Guava 19.0-rc2 is not permitted",
                "author" : "Danny Thomas <dmthomas@gmail.com>",
                "date" : "2015-10-07T20:21:20.368Z"
            },
            {
                "module": "com.sun.jersey:jersey-bundle",
                "reason" : "jersey-bundle is a fat jar that includes non-relocated (shaded) third party classes, which can cause duplicated classes on the classpath. Please specify the jersey- libraries you need directly",
                "author" : "Danny Thomas <dmthomas@gmail.com>",
                "date" : "2015-10-07T20:21:20.368Z"
            }
        ],
        "reject": [
            {
                "module": "com.google.guava:guava:12.0",
                "reason" : "Guava 12.0 significantly regressed LocalCache performance",
                "author" : "Danny Thomas <dmthomas@gmail.com>",
                "date" : "2015-10-07T20:21:20.368Z"
            }
        ],
        "align": [
            {
                "group": "com.sun.jersey",
                "reason": "Make sure jersey-core, jersey-server, etc. are aligned e.g. 1.19.1",
                "author": "Example Person <person@example.org>",
                "date": "2015-10-08T20:15:14.321Z"
            }
        ]
    }
```
