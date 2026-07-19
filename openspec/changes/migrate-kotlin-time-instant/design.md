## Context

FANBOX serializable public models currently import `kotlinx.datetime.Instant`, while internal expiry and diagnostic code imports `kotlinx.datetime.Clock`. The catalog selects `kotlinx-datetime:0.7.1-0.6.x-compat`, so Android and Kotlin/Native ABI baselines expose the transitional namespace and the published API dependency forces the bridge onto consumers.

The official kotlinx-datetime release history confirms that 0.7.0 moved `Instant` and `Clock` to `kotlin.time`, 0.7.1 restored aliases for migration, and the current 0.8.0 normal artifact is published without those compatibility declarations. Kotlin 2.2.10 and kotlinx-serialization 1.9.0 in this repository support serializable stdlib `Instant` directly.

PixiView currently selects `kotlinx-datetime:0.8.0-0.6.x-compat`. Its fankt timestamp call sites use four `toStdlibInstant()` calls across payment grouping, the common formatting extension, and two relative-time extensions; other calendar and time-zone helpers still justify a normal `kotlinx-datetime` dependency in that application. Those files already carry `ExperimentalTime` opt-ins. Its main checkout has unrelated local edits, so validation must use a separate disposable worktree.

In Kotlin 2.2.10, `kotlin.time.Instant` and `kotlin.time.Clock` are annotated with error-level `ExperimentalTime`. Exposing them is therefore not merely a namespace replacement: fankt production/tests and every consumer usage must opt in, and the stdlib explicitly warns library authors that future binary compatibility is not yet guaranteed.

## Goals / Non-Goals

**Goals:**

- Make `kotlin.time.Instant` and `kotlin.time.Clock` the only Instant/Clock types in fankt production and public ABI, with explicit `ExperimentalTime` opt-ins.
- Remove fankt's complete `kotlinx-datetime` dependency because no production calendar or time-zone API remains after migration.
- Prove Android and Kotlin/Native ABI, publication metadata, isolated consumer compilation, serialization, and mapper parsing use the stdlib type.
- Give PixiView an exact source migration and verify it against the locally published candidate without modifying its existing dirty checkout.

**Non-Goals:**

- Migrate PixiView calendar/time-zone APIs away from normal `kotlinx-datetime`.
- Publish fankt or commit a PixiView change from this PR.
- Preserve binary compatibility with the unpublished v0.1.0 candidate or the v0.0.x public timestamp descriptors.

## Decisions

### 1. Replace type ownership directly instead of adding a fankt alias

（ユーザー確認済み）Issue #36 explicitly selects the Kotlin 2.1+ stdlib types. All public model fields, internal mappers, tests, expiry reads, and diagnostic clocks use `kotlin.time.Instant` or `kotlin.time.Clock` directly. The ABI baselines therefore record `kotlin.time.Instant`; no deprecated alias remains in source or metadata. Because Kotlin 2.2.10 marks both types with error-level `ExperimentalTime`, fankt opts in explicitly and migration guidance requires consumers to do the same. This release accepts the documented risk that a future Kotlin version may change the experimental binary surface; silently retaining the deprecated bridge would violate the issue's selected boundary.

Alternative: add a fankt-owned timestamp alias. Rejected because the stdlib type is already the stable ownership boundary and another public name would add migration work without decoupling value.

### 2. Remove kotlinx-datetime from fankt instead of changing it to a normal version

（agent 仮決め）After the direct migration, repository search shows no fankt production use of `LocalDate`, `LocalDateTime`, `TimeZone`, or other kotlinx-datetime APIs. Remove the catalog version/alias, the direct source-set dependency, and the `infra-api` bundle member. Publication verification rejects every `org.jetbrains.kotlinx:kotlinx-datetime` component on both compile and runtime graphs.

Alternative: keep `0.8.0` as `implementation`. Rejected because an unused dependency still couples resolution, increases the runtime graph, and weakens the acceptance criterion that it is used only where necessary.

### 3. Make the existing isolated consumer gate enforce the new boundary

（agent 仮決め）Change `FanboxBoundaryConsumer.publishedAt` to an explicitly opted-in `kotlin.time.Instant`, retain `FanboxPost.serializer()` compilation, and extend its verification task to fail if any kotlinx-datetime module appears in compile or runtime resolution. Extend the root boundary task to reject `kotlinx/datetime/Instant`, `kotlinx/datetime/Clock`, and the module dependency in Android/Kotlin Native ABI and publication metadata. Keep Ktor checks intact.

Alternative: rely only on source grep. Rejected because the regression can survive in generated ABI or publication metadata even when direct imports disappear.

### 4. Validate the real PixiView migration in an isolated worktree

（agent 仮決め）Create a disposable PixiView worktree from its current tracked `master`, leave the user's dirty checkout untouched, point it at the isolated local fankt publication, select normal `kotlinx-datetime:0.8.0`, convert all four bridge calls and their receivers to direct `kotlin.time.Instant`, preserve the existing `ExperimentalTime` opt-ins, and compile the affected common modules. Discard the validation worktree after recording the result; the fankt PR documents but does not commit consumer changes.

Alternative: declare the compatibility fixture sufficient. Rejected because the issue explicitly requires PixiView compilation, and its extension overload resolution is the concrete consumer risk.

### 5. Put the migration note in the existing v0.1.0 README section

（agent 仮決め）Extend the existing breaking-migration section rather than creating a separate release-note file. State that model timestamps are `kotlin.time.Instant`, Kotlin 2.2 callers require `ExperimentalTime` opt-in, all four known PixiView `toStdlibInstant()` calls are removed, and consumers retain/select normal kotlinx-datetime only for calendar/time-zone operations.

## Risks / Trade-offs

- [An ABI baseline is updated mechanically while a deprecated type remains elsewhere] → Scan production source, Android ABI, KLib ABI, and publication graphs independently; add negative boundary fixtures where practical.
- [Serialization shape changes when type ownership changes] → Round-trip representative public models and preserve ISO-8601 JSON values in focused mapper/serialization tests.
- [Removing the dependency breaks an unobserved internal extension import] → Compile all common, Android, and iOS targets and require the isolated publication consumer to resolve without datetime.
- [PixiView validation accidentally overwrites user work] → Use a separate worktree at an explicit path and never edit or clean its existing checkout.
- [Consumers treat the stdlib namespace as stable in Kotlin 2.2] → Document the error-level opt-in and experimental binary-compatibility warning; compile the fixture and PixiView with explicit opt-ins.
- [A later fankt feature needs calendar APIs] → Reintroduce a current normal artifact as `implementation` at that point; public timestamp ownership remains stdlib.

## Migration Plan

1. Migrate fankt production/test imports and regenerate Android/Kotlin Native ABI baselines.
2. Remove the catalog alias and source-set API dependency; add negative source, ABI, metadata, and consumer graph gates.
3. Publish all fankt targets to an isolated local repository and compile the boundary consumer.
4. In a disposable PixiView worktree, select the local candidate, normal `kotlinx-datetime:0.8.0`, direct stdlib receivers at all four bridge call sites, explicit opt-ins, and compile affected modules.
5. Document the consumer edits in README and the PR description, then run full repository validation and required CI.

Rollback before v0.1.0 publication is a source revert. After publication, restoring `kotlinx.datetime.Instant` would be another breaking API change and is not a compatible rollback; fixes preserve stdlib timestamp ownership.

## Open Questions

None. The dependency can be removed based on the complete current production import inventory, and the consumer validation path avoids the dirty PixiView checkout.
