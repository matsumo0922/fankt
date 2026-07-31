## MODIFIED Requirements

### Requirement: Existing Fanbox behavior remains compatible
Every existing public non-download API operation SHALL preserve its request query and JSON body semantics, request-time authentication, successful domain result, tolerant-list behavior, and typed failure semantics after generated APIs are removed and after the core is split across source sets to support Kotlin/JS. Existing public operation signatures and domain model signatures MUST NOT be broken by this migration. Because relocating declarations between source sets is not intended to change the public API, an ABI dump difference beyond the recorded target list SHALL be treated as a signal of an unintended side effect of the move.

#### Scenario: Existing fixture and MockEngine suites run
- **WHEN** the fanbox common, client, Android unit, boundary, and compatibility tests run after migration
- **THEN** all existing behavior assertions pass through the descriptor executor and pure parser path

#### Scenario: Route drift guard uses descriptors
- **WHEN** the production endpoint inventory is verified
- **THEN** the guard derives coverage from endpoint IDs, descriptor builders, and trusted policies rather than Ktorfit annotations while preserving the 29 declaration and 28 unique-route contract

#### Scenario: Existing ABI is compared
- **WHEN** legacy ABI checks compare the pre-migration public API with the migrated client
- **THEN** existing `Fanbox` operations and domain models remain compatible and internal descriptor/parser symbols do not appear in the public API dump

#### Scenario: Adding the JS target changes only the recorded targets
- **WHEN** the klib ABI dump is regenerated after the Kotlin/JS target is declared
- **THEN** the dump records the added target and no declaration available to Android or iOS before the change is removed

## ADDED Requirements

### Requirement: Publication and boundary verification cover the JavaScript target
Publication metadata verification SHALL require the Kotlin/JS publication task, and the persistence boundary check SHALL inspect the Kotlin/JS compile classpath. The portable import boundary check SHALL scan the source set shared by every target and SHALL reject JVM-only and HTML parser imports in addition to the existing forbidden roots.

#### Scenario: JS publication metadata is required
- **WHEN** the required publication metadata task inventory is verified
- **THEN** it includes the Kotlin/JS publication task on every host platform

#### Scenario: Persistence boundary covers JS
- **WHEN** the persistence boundary check resolves its inspected configurations
- **THEN** the Kotlin/JS compile classpath is among them and contains no optional persistence dependency

#### Scenario: Portable boundary scan is non-empty
- **WHEN** the portable import boundary check runs after the source set split
- **THEN** it scans a non-empty set of files from the source set shared by every target and reports no forbidden import
