## Why

The FANBOX public models expose the transitional `kotlinx.datetime.Instant` compatibility type, coupling consumers to a deprecated bridge artifact. The intentionally breaking v0.1.0 boundary is the point to move timestamps and clocks to Kotlin stdlib ownership before that bridge disappears.

## What Changes

- **BREAKING** Replace every public FANBOX model timestamp with the `@ExperimentalTime` `kotlin.time.Instant` type, update internal parsing, clock, tests, and ABI baselines, and require Kotlin 2.2 consumers to opt in at each use site or compiler boundary.
- Remove the `0.7.1-0.6.x-compat` dependency and catalog alias because fankt has no remaining production need for `kotlinx-datetime` after the migration.
- Add Android and Kotlin/Native boundary checks that reject `kotlinx.datetime.Instant` or `kotlinx.datetime.Clock` in the published API and verify the artifact no longer exports `kotlinx-datetime`.
- Extend the isolated published consumer fixture with an explicit `ExperimentalTime` opt-in and stdlib timestamp usage. Document the PixiView migration: update all four `toStdlibInstant()` call sites, preserve their existing opt-ins, and retain a normal `kotlinx-datetime` dependency only where calendar/time-zone APIs remain in use.

## Capabilities

### New Capabilities

- `stdlib-time-public-api`: Defines the stdlib-owned timestamp/clock boundary, dependency removal, published ABI verification, and downstream migration contract.

### Modified Capabilities

None.

## Impact

- Public API: timestamp properties and data-class constructors in `me.matsumo.fankt.fanbox.domain.model` change binary and source type ownership.
- Implementation: FANBOX mappers, diagnostics, cookie expiry handling, tests, Android/Kotlin Native ABI baselines, and publication boundary tooling.
- Dependencies: `org.jetbrains.kotlinx:kotlinx-datetime:*compat` is removed from fankt; consumers needing calendar or time-zone operations select a normal artifact independently.
- Consumer: PixiView's formatting, payment grouping, and relative-time helpers accept `kotlin.time.Instant` directly, delete four compatibility conversion calls, retain `ExperimentalTime` opt-ins, and accept the stdlib type's experimental binary-compatibility boundary in the coordinated v0.1.0 migration.
