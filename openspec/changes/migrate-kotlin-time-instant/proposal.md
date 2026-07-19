## Why

The FANBOX public models expose the transitional `kotlinx.datetime.Instant` compatibility type, coupling consumers to a deprecated bridge artifact. The intentionally breaking v0.1.0 boundary is the point to move timestamps and clocks to Kotlin stdlib ownership before that bridge disappears.

## What Changes

- **BREAKING** Replace every public FANBOX model timestamp with `kotlin.time.Instant` and update internal parsing, clock, tests, and ABI baselines to the stdlib types.
- Remove the `0.7.1-0.6.x-compat` dependency and catalog alias because fankt has no remaining production need for `kotlinx-datetime` after the migration.
- Add Android and Kotlin/Native boundary checks that reject `kotlinx.datetime.Instant` or `kotlinx.datetime.Clock` in the published API and verify the artifact no longer exports `kotlinx-datetime`.
- Extend the isolated published consumer fixture with stdlib timestamp usage and document the PixiView migration: remove `toStdlibInstant()` bridges while retaining a normal `kotlinx-datetime` dependency only where calendar/time-zone APIs remain in use.

## Capabilities

### New Capabilities

- `stdlib-time-public-api`: Defines the stdlib-owned timestamp/clock boundary, dependency removal, published ABI verification, and downstream migration contract.

### Modified Capabilities

None.

## Impact

- Public API: timestamp properties and data-class constructors in `me.matsumo.fankt.fanbox.domain.model` change binary and source type ownership.
- Implementation: FANBOX mappers, diagnostics, cookie expiry handling, tests, Android/Kotlin Native ABI baselines, and publication boundary tooling.
- Dependencies: `org.jetbrains.kotlinx:kotlinx-datetime:*compat` is removed from fankt; consumers needing calendar or time-zone operations select a normal artifact independently.
- Consumer: PixiView's formatting and relative-time helpers accept `kotlin.time.Instant` directly and delete compatibility conversion calls in the coordinated v0.1.0 migration.
