# Gradle Resolution Rules Plugin

[![Build Status](https://travis-ci.org/nebula-plugins/gradle-resolution-rules-plugin.svg?branch=master)](https://travis-ci.org/nebula-plugins/gradle-resolution-rules-plugin)
[![Coverage Status](https://coveralls.io/repos/nebula-plugins/gradle-resolution-rules-plugin/badge.svg?branch=master&service=github)](https://coveralls.io/github/nebula-plugins/gradle-resolution-rules-plugin?branch=master)
[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/nebula-plugins/gradle-resolution-rules-plugin?utm_source=badgeutm_medium=badgeutm_campaign=pr-badge)
[![Apache 2.0](https://img.shields.io/github/license/nebula-plugins/gradle-resolution-rules-plugin.svg)](http://www.apache.org/licenses/LICENSE-2.0)

Gradle plugin for providing dependency resolution rules.

Similar to the [Blacklist Plugin](https://github.com/nebula-plugins/gradle-blacklist-plugin), but provides general purpose rule types, and supports remote configuration rather than extension based configuration via an enterprise plugin.

# Usage

    buildscript {
        repositories {
            jcenter()
        }

        dependencies {
            classpath 'com.netflix.nebula:gradle-resolution-rules-plugin:1.0.0'
        }
    }

    apply plugin: 'nebula.resolution-rules'

Or using the Gradle plugin portal:

    plugins {
        id 'nebula.resolution-rules' version '1.0.0'
    }

# Dependency rules

Dependency rules are read from the `resolutionRules` configuration. Zip and jar archives are supported, as are flat JSON files. JSON files within archives can at any directory level, and more than one json file can be provided.

    dependencies {
        resolutionRules files('local-rules.json')
        resolutionRules 'com.myorg:resolution-rules:latest.release'
    }

# Example JSON

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
                "reason" : "jersey-bundle is a far jar that includes non-relocated (shaded) third party classes, which can cause duplicated classes on the classpath. Please specify the jersey- libraries you need directly",
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
        ]
    }