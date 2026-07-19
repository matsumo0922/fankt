## ADDED Requirements

### Requirement: Public Fanbox API is independent from Ktor
Every public or protected declaration in the `me.matsumo.fankt.fanbox` production API SHALL use Kotlin standard-library, kotlinx, or fankt-owned types and SHALL contain no `io.ktor` type in parameters, returns, properties, supertypes, or type aliases. Cookie-facing operations SHALL use `FanboxCookieRecord`, and logging configuration SHALL use `FanboxLogLevel`.

#### Scenario: Compiled API is inspected
- **WHEN** the compiled Kotlin public ABI of `fanbox` is generated and inspected
- **THEN** it contains no `io.ktor` reference

#### Scenario: Android generic signatures are inspected
- **WHEN** Android public and protected class, field, and method signatures are inspected from compiled class files
- **THEN** their generic Signature attributes contain no `io.ktor` type even when the erased descriptor contains only a collection or another generic container

#### Scenario: Cookies are observed and replaced
- **WHEN** a consumer observes `Fanbox.cookies` or calls `Fanbox.setCookies`
- **THEN** the values use `FanboxCookieRecord` without requiring Ktor Cookie types

#### Scenario: Logging is configured
- **WHEN** a consumer selects a `FanboxLogLevel`
- **THEN** fankt maps it to internal HTTP logging without exposing the internal logging type

### Requirement: Ktor is declared as an implementation dependency
Every `fanbox` common, hierarchical native, target-specific iOS, and Android source set SHALL declare Ktor libraries and platform engines with Gradle `implementation` rather than `api`. Non-Ktor libraries used by public Android signatures, including coroutines, datetime, and serialization core, SHALL be declared individually as API dependencies without promoting the complete infrastructure bundle. The Android published API variant SHALL contain no Ktor dependency, its runtime variant SHALL retain a fixed expected inventory of required Ktor implementation dependencies including the Android engine, and automated verification SHALL inspect both declared source-set dependency scopes and Android Gradle module metadata. KMP/Native compiler-facing publication metadata MAY include implementation KLib dependencies required for linking, but the compiled public ABI SHALL remain Ktor-free.

#### Scenario: Declared dependency scopes are verified
- **WHEN** every common, hierarchy, platform, and target-specific source-set API dependency declaration is inspected
- **THEN** none declares a Ktor module

#### Scenario: Android publication metadata is verified
- **WHEN** the `fanbox` Android Gradle module metadata is generated
- **THEN** its API variant contains the non-Ktor libraries required by public signatures and no Ktor module while its runtime variant retains every module in a fixed required Ktor inventory, including `ktor-client-okhttp`

#### Scenario: Native publication retains link dependencies without ABI exposure
- **WHEN** Kotlin/Native publication metadata includes implementation KLib dependencies in a compiler-facing variant
- **THEN** the checked public ABI still contains no Ktor type

#### Scenario: Consumer chooses a compatible Ktor version
- **WHEN** a consumer explicitly selects a runtime-compatible Ktor version
- **THEN** dependency resolution can select that version without any fankt public signature requiring Ktor source usage

#### Scenario: Published Android consumer compiles without Ktor
- **WHEN** a consumer resolves the isolated Android publication and compiles usages of Flow, datetime, and serialization-backed fankt signatures without declaring Ktor
- **THEN** compilation succeeds with no Ktor component on its compile classpath while the runtime graph retains the consumer-selected compatible Ktor version

#### Scenario: Consumer fixture resolves only the isolated publication
- **WHEN** the compatibility fixture resolves `me.matsumo.fankt:fanbox` using its property-supplied version and repository
- **THEN** exclusive repository content rules prevent fallback for the `me.matsumo.fankt` group and verification proves the resolved artifact path is inside that local repository
