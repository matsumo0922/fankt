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

### Requirement: Published API metadata hides Ktor dependencies
Every published `fanbox` variant SHALL declare Ktor libraries and platform engines as runtime implementation dependencies rather than API dependencies. Automated verification SHALL inspect Gradle module metadata so a Ktor dependency cannot be added to an API dependency set unnoticed.

#### Scenario: Publication metadata is verified
- **WHEN** the `fanbox` Gradle module metadata for common, Android, and iOS variants is generated
- **THEN** no API dependency set contains a Ktor module

#### Scenario: Consumer chooses a compatible Ktor version
- **WHEN** a consumer explicitly selects a runtime-compatible Ktor version
- **THEN** fankt's publication metadata does not force Ktor onto the consumer compile API

