# Gradle Resolution Rules Plugin

![Support Status](https://img.shields.io/badge/nebula-supported-brightgreen.svg)
[![Build Status](https://travis-ci.org/nebula-plugins/gradle-resolution-rules-plugin.svg?branch=master)](https://travis-ci.org/nebula-plugins/gradle-resolution-rules-plugin)
[![Coverage Status](https://coveralls.io/repos/nebula-plugins/gradle-resolution-rules-plugin/badge.svg?branch=master&service=github)](https://coveralls.io/github/nebula-plugins/gradle-resolution-rules-plugin?branch=master)
[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/nebula-plugins/gradle-resolution-rules-plugin?utm_source=badgeutm_medium=badgeutm_campaign=pr-badge)
[![Apache 2.0](https://img.shields.io/github/license/nebula-plugins/gradle-resolution-rules-plugin.svg)](http://www.apache.org/licenses/LICENSE-2.0)

Gradle resolution strategies and module metadata provide an effective way to solve the most common dependency issues, however sharing these rules between projects is cumbersome, and requires custom plugins or `apply from` calls. This plugin provides general purpose rule types, allowing rules to be published, versioned, shared between projects, and optionally [dependency locked](https://github.com/nebula-plugins/gradle-dependency-lock-plugin).

These rule types solve the most common cause of dependency issues in projects, including:

- Duplicate classes caused by changes to group or artifact ids, without renaming packages
- Duplicate classes caused by bundle dependencies, which do not conflict resolve against the normal dependencies for that library
- Lack of version alignment between libraries, where version alignment is needed for compatibility
- Ensuring a minimum version of a library

# Quick Start

Refer to the [Gradle Plugin Portal](https://plugins.gradle.org/plugin/nebula.resolution-rules) for instructions on how to apply the plugin.

## Rules

We produce a rules for dependencies found in Maven Central and other public repositories, to use those rules in your project add:

```groovy
dependencies {
    resolutionRules 'com.netflix.nebula:gradle-resolution-rules:latest.release'
}
```

See the [gradle-resolution-rules](https://github.com/nebula-plugins/gradle-resolution-rules) project for details of the rules, and instructions on how to enable optional rule sets.

# Documentation

The project wiki contains the [full documentation](https://github.com/nebula-plugins/gradle-resolution-rules-plugin/wiki) for the plugin.
