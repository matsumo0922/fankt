## Context

`Fanbox` currently leaks exactly five Ktor-backed declarations through its effective public ABI: constructor logging configuration, cookie observation, cookie mutation, raw-client access, and the download return type. Gradle also publishes the common Ktor bundle and Android/iOS engines as API dependencies. This couples every consumer's compile classpath and source code to fankt's HTTP implementation.

Issue #35 targets the intentionally breaking v0.1.0 boundary. Issues #32 and #33 already established security-sensitive download rules and a fankt-owned `FanboxCookieRecord`; this change must reuse those contracts rather than regress them or create a second cookie model.

## Goals / Non-Goals

**Goals:**

- Remove every Ktor type from compiled public/protected `fanbox` declarations.
- Preserve cookie semantics, download allowlisting, authentication scoping, streaming, progress, exception mapping, and deterministic client ownership.
- Prevent both ABI and Gradle publication metadata regressions automatically.
- Give Android and iOS consumers a common streaming primitive that does not mandate buffering large archives or media in memory.

**Non-Goals:**

- Supporting arbitrary mutually incompatible Ktor versions in one runtime graph.
- Providing a general-purpose HTTP client through `Fanbox`.
- Changing the optional Room schema or cookie persistence semantics.
- Migrating the external PixiView repository in this pull request.

## Decisions

### Reuse `FanboxCookieRecord` as the public cookie type（agent 仮決め）

`Fanbox.cookies` and `setCookies` will use the existing record already shared by in-memory and Room storage. An additional `FanboxCookie` type would duplicate identity, expiry, domain, path, secure, and host-only semantics and create conversion drift. Internal adapters remain responsible for translating records to Ktor cookies.

Alternative: rename the record or add a public type alias. Rejected because v0.1.0 has not been published, the existing name accurately describes stored values, and an alias adds another public name without new behavior.

### Add a fankt-owned logging enum with explicit internal mapping（agent 仮決め）

`FanboxLogLevel` will preserve the familiar choices `NONE`, `INFO`, `HEADERS`, `BODY`, and `ALL`. The existing security policy remains explicit: `BODY` maps to internal `INFO`, and `ALL` maps to internal `HEADERS`, so authentication payloads are not logged even when callers request verbose logging.

Alternative: expose only the three effective levels. Rejected because keeping the full source-level intent makes migration mechanical while the mapping documents the security cap.

### Replace deferred statements with direct suspend chunk streaming（agent 仮決め）

`download(url, onProgress, onChunk)` will validate the destination, execute the request, and invoke a suspend callback with bounded `ByteArray` chunks in order. Waiting for each callback provides backpressure. The callback's exception and cancellation propagate unchanged; HTTP and transport failures keep the existing `FanboxException` normalization.

This deliberately removes deferred request ownership. A whole-body convenience API is omitted because `.zip`, `.psd`, and video responses can be large; consumers that knowingly want a complete body may accumulate chunks themselves. A Ktor-free request-wrapper abstraction was considered, but it would recreate lifecycle and execution-state complexity solely to preserve deferred semantics that the issue does not require.

### Remove raw HTTP access instead of wrapping it（ユーザー確認済み）

`getHttpClient` will be deleted. Generated methods and `download` are the supported fankt operations; host applications own separate clients for unrelated network traffic. This also reduces the internal client graph by the two raw-client configurations while retaining generated and download clients.

### Verify the compiled and published boundaries（agent 仮決め）

Kotlin Gradle Plugin ABI validation will generate a checked-in public ABI dump for `fanbox`. A verification task will fail when the dump contains `io.ktor`. Publication verification will generate Gradle module metadata and inspect API dependency sets, failing when any Ktor module appears there. Source-text scanning alone is insufficient because inferred signatures and type aliases can leak dependencies without an obvious declaration.

Ktor common and engine dependencies move to `implementation`. This removes them from the consumer compile API and lets a consumer explicitly select a compatible runtime version; it does not promise that incompatible Ktor binaries can coexist.

## Risks / Trade-offs

- [Chunk callback arrays are retained by a consumer] -> Document that chunks are independent bounded values intended for immediate consumption; tests verify chunk sizing and order.
- [Callback failures are accidentally wrapped as network failures] -> Keep callback execution outside transport-normalization catches or explicitly rethrow the callback failure; add identity-preserving tests.
- [Redirect or authentication behavior changes during download rewrite] -> Retain the dedicated configured client and existing request/redirect plugins; port the issue #32 tests to the new execution shape.
- [An inferred or platform-specific Ktor signature escapes review] -> Generate ABI for supported targets and scan the checked-in ABI boundary in CI.
- [Gradle metadata uses variant-specific dependency sets] -> Inspect generated `.module` variants rather than relying only on source dependency declarations or Maven POM scopes.
- [Consumers interpret `implementation` as arbitrary version compatibility] -> State runtime compatibility precisely in README migration guidance and test only a known-compatible consumer-selected version.

## Migration Plan

1. Introduce `FanboxLogLevel`, change cookie signatures to `FanboxCookieRecord`, remove raw-client access, and replace download execution in one breaking commit series.
2. Move Ktor declarations to `implementation`, enable ABI validation, generate the intended v0.1.0 baseline, and add publication checks.
3. Update repository consumers, tests, README, and KDoc together.
4. Validate common, Android, iOS, publication metadata, and a consumer compile fixture before publishing v0.1.0.
5. External consumers migrate logging/cookies/downloads and own any general-purpose HTTP clients before adopting v0.1.0.

Rollback before publication is a source revert. After v0.1.0 publication, fixes must preserve the new Ktor-free boundary; restoring the Ktor-exposing v0.0.x signatures would require a separate compatibility release decision.

## Open Questions

None. The public API shape and migration boundary are fixed for implementation; independent falsification remains required before code changes because the download path is security- and memory-sensitive.
