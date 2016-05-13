1.5.1 / 2016/05/12
==================
- Protect against spring-boot plugin getting us into a stackoverflow situation

1.5.0 / 2016/05/12
==================
- Align rules no longer replace changes made by other rule type (uses useVersion instead of useTarget).

1.4.0 / 2016/05/11
==================
- Make it so we are not eagerly resolving the different configurations. Will only resolve when gradle resolves the configuration.

1.3.0 / 2016/04/28
==================
- BUGFIX: Align rules attempt to align project dependencies, causing them to be resolved as remote artifacts
- Rules files may now be optional, so optionated rules aren't applied without users opting in
- Align rules support regular expressions in the group, includes and excludes
- Empty rules types can be excluded from rules files (required for backwards compatibility with old rules files, but also makes working with them nicer)

1.2.2 / 2016/04/25
==================
- BUGFIX: Handle circularish dependencies B depends on A for compile, A depends on B for testCompile

1.2.1 / 2016/04/19
==================
- BUGFIX: Make sure resolutionRules configuration can be locked by nebula.dependency-lock
- BUGFIX: Allow other changes to configurations.all and associated resolutionStrategy

1.2.0 / 2016/04/11
==================
- Allow opt out of rules for shared company wide rules that apply to your project, e.g. there is a common align rule for a:foo and a:bar and you produce them
- Performance improvement if there are multiple align rules
- BUGFIX for unresolvable dependencies fixed by a resolution rule

1.1.5 / 2016/03/31
==================
- Fix interaction bug with nebula.dependency-recommender (omitted versions causing issues)
- Fix interaction bug with spring-boot plugin (omitted versions causing issues)
- Fix handling of dependency graphs with cycles in them

1.1.4 / 2016/03/22
==================
- Remove dependency on jackson libraries

1.1.3 / 2016/03/21
==================
- Publish nebula.resolution-rules-producer to gradle plugin portal

1.1.2 / 2016/03/21
==================
- Attempt to add nebula.resolution-rules-producer to gradle plugin portal

1.1.1 / 2016/03/21
==================
- Fix publishing to bintray

1.1.0 / 2016/03/21
==================
- Add in align rule

1.0.1 / 2015/10/28
==================
- Re-publish due to initial JCenter sync failure

1.0.0 / 2015/10/28
==================
- Initial Release
