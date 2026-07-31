## ADDED Requirements

### Requirement: The FANBOX core compiles for Kotlin/JS
`:fankt:fanbox` SHALL declare a Kotlin/JS IR target that produces a library output. The target MUST NOT declare a browser binary. The portable core — request descriptors, endpoint builders, response parsing, entity and domain models, authentication storage contracts, the exception hierarchy, and tolerant list decoding — SHALL compile for that target.

#### Scenario: JS compilation succeeds
- **WHEN** the Kotlin/JS compilation of the FANBOX core is executed
- **THEN** it completes without unresolved references

#### Scenario: Library output is produced
- **WHEN** the Kotlin/JS target configuration is inspected
- **THEN** it declares a library binary and does not declare a browser environment

### Requirement: Fixture tests run on Kotlin/JS
The golden fixture tests for response parsing, entity mapping, endpoint building, and model serialization SHALL execute on the Kotlin/JS target and produce the same results as on the existing targets.

#### Scenario: JS test task passes
- **WHEN** `:fankt:fanbox:jsTest` is executed
- **THEN** it runs the portable fixture tests and reports no failures

#### Scenario: JS test coverage includes each portable area
- **WHEN** the set of tests executed on Kotlin/JS is inspected
- **THEN** it includes creator, post, and user mapper golden tests, metadata JSON decoding tests, endpoint builder tests, URL parsing tests, failure interpretation tests, and identifier, timestamp, and post body serialization tests

### Requirement: HTTP execution is excluded from the JavaScript target
Code that constructs or executes HTTP requests, downloads media, logs through Napier, parses HTML, or references a dispatcher that is unavailable on Kotlin/JS SHALL reside in a source set that the Kotlin/JS target does not compile. The Android and iOS targets SHALL share one such source set rather than duplicating that code.

#### Scenario: Ktor is absent from the JS compilation
- **WHEN** the source sets compiled for the Kotlin/JS target are inspected
- **THEN** none of them contains an import of `io.ktor`, `io.github.aakira.napier`, `kotlinx.io`, or a Ksoup type, and none references `Dispatchers.IO`

#### Scenario: Client code is shared by Android and iOS
- **WHEN** the source set hierarchy is inspected
- **THEN** a single intermediate source set holds the HTTP client, request executor, repositories, download support, and HTML metadata extraction, and both the Android and iOS source sets depend on it

#### Scenario: Existing targets keep their behaviour
- **WHEN** the Android and iOS targets are built and their tests are executed after the source set split
- **THEN** the public API, the request wire contract, and the test results are unchanged

### Requirement: The core declares no JVM-only dependency
The dependencies of the source set shared by every target SHALL exclude artifacts that are unavailable on Kotlin/JS, specifically the JDK-targeted Kotlin standard library and Kotlin reflection.

#### Scenario: Common dependencies resolve for JS
- **WHEN** the dependencies of the source set shared by every target are resolved for the Kotlin/JS target
- **THEN** resolution succeeds and includes neither `kotlin-stdlib-jdk8` nor `kotlin-reflect`

### Requirement: Continuous integration verifies the JavaScript target
The pull request workflow SHALL build and test the Kotlin/JS target of `:fankt:fanbox`.

#### Scenario: JS build and test run in CI
- **WHEN** the pull request verification workflow runs
- **THEN** it executes the Kotlin/JS compilation and the Kotlin/JS test task for the FANBOX core
