## ADDED Requirements

### Requirement: FANBOX timestamps and clocks use Kotlin stdlib types
Every public or protected FANBOX declaration SHALL use `kotlin.time.Instant` for moment-in-time values and SHALL contain no `kotlinx.datetime.Instant` or `kotlinx.datetime.Clock` in source, Android ABI, or Kotlin/Native ABI. Internal timestamp parsing and current-time reads SHALL use `kotlin.time.Instant` and `kotlin.time.Clock` directly, and serialized public model timestamps SHALL preserve their ISO-8601 JSON representation. Trace: issue #36 public API and Clock migration acceptance criteria.

#### Scenario: Public timestamp ABI is inspected
- **WHEN** Android and Kotlin/Native public ABI baselines are generated and inspected
- **THEN** timestamp properties and data-class operations reference `kotlin.time.Instant` and contain no `kotlinx.datetime.Instant` or `kotlinx.datetime.Clock`

#### Scenario: FANBOX timestamps are parsed
- **WHEN** a FANBOX response contains an ISO-8601 timestamp with an explicit offset
- **THEN** its mapper produces the equivalent `kotlin.time.Instant` value

#### Scenario: Public model timestamp is serialized
- **WHEN** a serializable public FANBOX model containing an Instant is encoded and decoded
- **THEN** the JSON timestamp remains ISO-8601 compatible and the decoded `kotlin.time.Instant` represents the same moment

#### Scenario: Runtime clocks use stdlib ownership
- **WHEN** Cookie expiry or diagnostic timestamps read the system clock
- **THEN** production code uses `kotlin.time.Clock.System` without a deprecated compatibility conversion

### Requirement: fankt does not publish kotlinx-datetime
The fankt version catalog and `fanbox` source sets SHALL declare no `kotlinx-datetime` dependency when production has no remaining calendar or time-zone use. Android and Kotlin Multiplatform publication metadata and the isolated consumer compile/runtime graphs SHALL contain no `org.jetbrains.kotlinx:kotlinx-datetime` module.

#### Scenario: Declared datetime dependency is absent
- **WHEN** the version catalog and every `fanbox` source-set dependency declaration are inspected
- **THEN** no compatibility or normal `kotlinx-datetime` dependency is declared

#### Scenario: Published metadata is independent from datetime
- **WHEN** Android and Kotlin Multiplatform publications are generated in an isolated repository
- **THEN** neither compile nor runtime metadata exports `org.jetbrains.kotlinx:kotlinx-datetime`

#### Scenario: Isolated consumer uses stdlib Instant
- **WHEN** a consumer compiles a `FanboxPost.publishedDatetime` usage and `FanboxPost.serializer()` against the isolated publication without declaring kotlinx-datetime
- **THEN** compilation succeeds with `kotlin.time.Instant` and no kotlinx-datetime component resolves on compile or runtime classpaths

### Requirement: PixiView migration is explicit and compilable
The v0.1.0 migration documentation SHALL state that PixiView and equivalent consumers accept fankt model timestamps as `kotlin.time.Instant`, remove `toStdlibInstant()` compatibility bridges, and retain or select a normal non-compat `kotlinx-datetime` artifact only for their own remaining calendar or time-zone APIs. The known PixiView timestamp call sites SHALL compile against the isolated fankt candidate after those mechanical edits.

#### Scenario: PixiView relative-time helpers migrate
- **WHEN** PixiView changes its fankt timestamp extension receivers to `kotlin.time.Instant` and removes `toStdlibInstant()` calls
- **THEN** relative-time and formatting code compiles without a compatibility Instant type

#### Scenario: PixiView retains calendar APIs independently
- **WHEN** PixiView still uses kotlinx-datetime calendar or time-zone operations after the fankt migration
- **THEN** it selects a normal non-compat kotlinx-datetime version independently rather than receiving a compat artifact through fankt

#### Scenario: Release migration is documented
- **WHEN** a consumer reads the v0.1.0 breaking-migration guidance
- **THEN** it identifies the public type change, direct stdlib replacement, bridge removals, and independent normal datetime dependency rule
