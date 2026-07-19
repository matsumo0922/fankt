## ADDED Requirements

### Requirement: Public Fanbox API is independent from Ktor
Every public or protected declaration in the `me.matsumo.fankt.fanbox` production API SHALL use Kotlin standard-library, kotlinx, or fankt-owned types and SHALL contain no `io.ktor` type in parameters, returns, properties, supertypes, or type aliases. Cookie-facing operations SHALL use `FanboxCookieRecord`, and logging configuration SHALL use `FanboxLogLevel`.

#### Scenario: Compiled API is inspected
- **WHEN** the compiled Kotlin public ABI of `fanbox` is generated and inspected
- **THEN** it contains no `io.ktor` reference

#### Scenario: Cookies are observed and replaced
- **WHEN** a consumer observes `Fanbox.cookies` or calls `Fanbox.setCookies`
- **THEN** the values use `FanboxCookieRecord` without requiring Ktor Cookie types

#### Scenario: Logging is configured
- **WHEN** a consumer selects a `FanboxLogLevel`
- **THEN** fankt maps it to internal HTTP logging without exposing the internal logging type

### Requirement: Ktor is declared as an implementation dependency
Every `fanbox` common, hierarchical native, target-specific iOS, and Android source set SHALL declare Ktor libraries and platform engines with Gradle `implementation` rather than `api`. The Android published API variant SHALL contain no Ktor dependency, its runtime variant SHALL retain every required Ktor implementation dependency, and automated verification SHALL inspect both declared source-set dependency scopes and Android Gradle module metadata. KMP/Native compiler-facing publication metadata MAY include implementation KLib dependencies required for linking, but the compiled public ABI SHALL remain Ktor-free.

#### Scenario: Declared dependency scopes are verified
- **WHEN** every common, hierarchy, platform, and target-specific source-set API dependency declaration is inspected
- **THEN** none declares a Ktor module

#### Scenario: Android publication metadata is verified
- **WHEN** the `fanbox` Android Gradle module metadata is generated
- **THEN** its API variant contains no Ktor module while its runtime variant retains the required implementation dependencies

#### Scenario: Native publication retains link dependencies without ABI exposure
- **WHEN** Kotlin/Native publication metadata includes implementation KLib dependencies in a compiler-facing variant
- **THEN** the checked public ABI still contains no Ktor type

#### Scenario: Consumer chooses a compatible Ktor version
- **WHEN** a consumer explicitly selects a runtime-compatible Ktor version
- **THEN** dependency resolution can select that version without any fankt public signature requiring Ktor source usage
