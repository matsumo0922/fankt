## MODIFIED Requirements

### Requirement: FANBOX timestamps and clocks use Kotlin stdlib types
Every public or protected FANBOX declaration SHALL use `kotlin.time.Instant` for moment-in-time values and SHALL contain no `kotlinx.datetime.Instant` or `kotlinx.datetime.Clock` in source, Android ABI, or Kotlin/Native ABI. fankt production and tests SHALL use Kotlin 2.3's stable `kotlin.time.Instant` and `kotlin.time.Clock` without retaining `ExperimentalTime` opt-ins solely for those declarations. Internal timestamp parsing and current-time reads SHALL use `kotlin.time.Instant` and `kotlin.time.Clock` directly, and serialized public model timestamps SHALL preserve their ISO-8601 JSON representation. Trace: issue #36 public API and Clock migration acceptance criteria, updated by issue #88 Kotlin 2.3.21 toolchain acceptance criteria.

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
- **THEN** production code uses stable `kotlin.time.Clock.System` without a deprecated compatibility conversion or obsolete opt-in

#### Scenario: Stable time boundary is current
- **WHEN** fankt compiles its stdlib timestamp declarations on Kotlin 2.3.21
- **THEN** `Instant` and `Clock` compile as stable APIs without `ExperimentalTime` opt-ins retained solely for those declarations

### Requirement: PixiView migration is explicit and compilable
The current consumer guidance SHALL state that PixiView and equivalent Kotlin 2.3.21-or-newer consumers accept fankt model timestamps as stable `kotlin.time.Instant`, remove `toStdlibInstant()` compatibility bridges, and retain or select a normal non-compat `kotlinx-datetime` artifact only for their own remaining calendar or time-zone APIs. It SHALL NOT require `ExperimentalTime` opt-in solely for `Instant` or `Clock`. All four known PixiView bridge calls SHALL remain absent while the isolated fankt candidate compiles.

#### Scenario: PixiView relative-time helpers use stable stdlib time
- **WHEN** PixiView compiles its fankt timestamp extension receivers as `kotlin.time.Instant` without the four `toStdlibInstant()` calls
- **THEN** payment grouping, relative-time, and formatting code compile without requiring `ExperimentalTime` solely for `Instant` or `Clock`

#### Scenario: PixiView retains calendar APIs independently
- **WHEN** PixiView still uses kotlinx-datetime calendar or time-zone operations after the fankt migration
- **THEN** it selects a normal non-compat kotlinx-datetime version independently rather than receiving a compat artifact through fankt

#### Scenario: Consumer guidance is current
- **WHEN** a consumer reads the compatibility guidance
- **THEN** it identifies stable stdlib timestamp ownership, Kotlin 2.3.21 as the minimum consumer compiler, four bridge removals, and the independent normal datetime dependency rule without a stale experimental binary-shape warning
