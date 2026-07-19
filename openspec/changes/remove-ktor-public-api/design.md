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

`Fanbox.cookies` and `setCookies` will use the existing record already shared by in-memory and Room storage. `FanboxCookieRecord.domain` is required and `hostOnly` is explicit, so `setCookies` will remove its `url` parameter rather than retain two competing scope authorities. `setFanboxSessionId` remains the convenience API for the conventional `.fanbox.cc` session scope. The public mutation adapter will preserve existing expiry behavior: expired additive records delete their identity and expired replacement records are omitted atomically. An additional `FanboxCookie` type would duplicate identity, expiry, domain, path, secure, and host-only semantics and create conversion drift. Internal adapters remain responsible for translating records to Ktor cookies.

Alternative: rename the record or add a public type alias. Rejected because v0.1.0 has not been published, the existing name accurately describes stored values, and an alias adds another public name without new behavior.

### Add a fankt-owned logging enum with explicit internal mapping（agent 仮決め）

`FanboxLogLevel` will preserve the familiar choices `NONE`, `INFO`, `HEADERS`, `BODY`, and `ALL`. The existing security policy remains explicit: `BODY` maps to internal `INFO`, and `ALL` maps to internal `HEADERS`, so authentication payloads are not logged even when callers request verbose logging.

Alternative: expose only the three effective levels. Rejected because keeping the full source-level intent makes migration mechanical while the mapping documents the security cap.

### Replace deferred statements with direct suspend chunk streaming（agent 仮決め）

`download(url, onProgress, onChunk)` will validate the destination, execute the request in the HTTP client's scoped response API, and invoke a suspend callback with independent `ByteArray` chunks of at most 64 KiB in order. The response scope guarantees release on every exit. Waiting for each callback before requesting the next read provides an observable backpressure guarantee. A zero-byte read yields before retrying instead of spinning. `onProgress` receives an initial `0f` before chunks, then known-length progress is computed from bytes whose callback has completed, clamped to `0f..1f`, rather than from an ahead-of-consumer transport hook. Callback exceptions and `CancellationException` propagate unchanged. HTTP failures and transport exceptions from header or body reads are normalized to `FanboxException`, with callback execution kept outside that normalization boundary so caller failures retain identity. A deterministic MockEngine response test sends one buffered chunk and then fails its channel, proving this mapping through the public `download` entrypoint rather than only through an internal read helper.

Each invocation creates a download-specific supervisor Job with the caller Job as an optional parent. This accepts coroutine contexts with no Job, propagates caller cancellation downward, and gives `Fanbox.close()` a narrow cancellation target. Close cancels only that download Job rather than the caller's Job or its surrounding scope; callback failure and cancellation identity remain unchanged across the added context boundary.

This deliberately removes deferred request ownership. A whole-body convenience API is omitted because `.zip`, `.psd`, and video responses can be large; consumers that knowingly want a complete body may accumulate chunks themselves. A Ktor-free request-wrapper abstraction was considered, but it would recreate lifecycle and execution-state complexity solely to preserve deferred semantics that the issue does not require.

### Remove raw HTTP access instead of wrapping it（ユーザー確認済み）

`getHttpClient` will be deleted. Generated methods and `download` are the supported fankt operations; host applications own separate clients for unrelated network traffic. This also reduces the internal client graph by the two raw-client configurations while retaining generated and download clients.

The download client will use a download-specific diagnostic source rather than the removed raw-client `custom-request` label, so public `FanboxException.endpoint` values do not refer to a deleted API.

### Verify the compiled and declared boundaries（agent 仮決め）

Kotlin Gradle Plugin 2.2.10's experimental ABI validation is the first candidate for checked-in public ABI dumps for the supported `fanbox` targets. Task 3.2 must prove Android and iOS Gradle integration before the change relies on it; if a target is unsupported, the fallback is a deterministic target compiler/KLib ABI dump committed and checked by the same boundary task. A verification task will fail when any dump contains `io.ktor`. A provider-backed dependency-scope check will fail if common, Android, or iOS source-set API declarations contain Ktor, and generated Android Gradle module metadata will be checked to keep Ktor out of its API variant. Source-text scanning alone is insufficient because inferred signatures and type aliases can leak dependencies without an obvious declaration.

Generated metadata establishes an important platform distinction: Android separates API and runtime variants, but KMP/Native compiler-facing metadata publishes implementation KLib dependencies because downstream linking needs them. Therefore KMP/Native publication metadata is not used as proof of source API exposure; every hierarchy and target-specific source set's declared Gradle scope plus compiled ABI are the enforceable boundary. Android metadata verification checks both directions: Ktor is absent from its API variant and all required implementation modules remain in its runtime variant. Verification task inputs remain provider-backed so the repository's configuration-cache policy is preserved.

Android's text ABI dump erases generic arguments and renders JVM internal names differently from the Native dump. The boundary task therefore also parses compiled release class files and inspects generic `Signature` attributes only for declarations present in the checked public/protected Android ABI. It recognizes both dotted and JVM-internal Ktor package names. This catches `List<KtorType>`-style leaks without treating Kotlin-internal implementation members as public API.

Type aliases require a separate compiled boundary because Android JVM descriptors and generic `Signature` attributes erase them completely. The same task reads `@kotlin.Metadata`, selects public or protected aliases, and recursively checks both underlying and expanded `KmType` graphs for Ktor classifiers. An Android-only unit-test source contains a deliberate public Ktor alias; a fixture task uses the production metadata inspector and succeeds only when that alias is reported as a violation, proving the empty-violation assertion in the release gate would fail.

Only non-Ktor modules required by public signatures are promoted individually: coroutines core for `Flow` and `CoroutineDispatcher`, datetime for public model timestamps, and serialization core for generated serializers. The broader infrastructure bundle remains an implementation dependency so reflection, immutable collections, and logging do not become accidental API promises. Android runtime metadata is compared with a fixed inventory of Ktor modules, including the OkHttp engine, rather than an expectation derived from the declarations being verified.

PR CI will run the declared-scope, Android ABI, and Android metadata gate, while the existing macOS job will run the iOS ABI gate. The release workflow will rerun the complete boundary gate before any publication step. Both workflows call one repository script so the publication and consumer commands cannot drift. A consumer fixture imports the root version catalog and resolves its `versionName` module metadata from an isolated repository rather than use a project dependency or duplicate the release version, reserves `me.matsumo.fankt` exclusively for that repository, verifies the resolved artifact path, and compiles public Flow, datetime, and serialization usages with no Ktor compile dependency. Its runtime graph still explicitly selects a compatible Ktor version. Kotlin metadata and ASM dependencies used by the boundary task are both explicit buildscript dependencies whose versions come from the same catalog.

The public Cookie storage contract defines identity using a trimmed, leading-dot-free, lowercase domain, a path canonicalized to `/` when it does not start with `/`, and the Cookie name. The fankt adapter canonicalizes and deduplicates records before calling a host-owned implementation, so custom storage cannot accidentally persist equivalent identities separately even if a caller supplies raw domain or path spellings.

Ktor common and engine dependencies move to `implementation`. On Android this removes them from the published compile API; on Kotlin/Native they remain compiler/link dependencies in Gradle metadata even though no fankt public signature uses them. Consumers can explicitly select a compatible resolved version, but incompatible Ktor binaries are not promised to coexist.

## Risks / Trade-offs

- [Chunk callback arrays are retained by a consumer] -> Document that chunks are independent bounded values intended for immediate consumption; tests verify chunk sizing and order.
- [Callback failures are accidentally wrapped as network failures] -> Keep callback execution outside transport-normalization catches or explicitly rethrow the callback failure; add identity-preserving tests.
- [Redirect or authentication behavior changes during download rewrite] -> Retain the dedicated configured client and existing request/redirect plugins; port the issue #32 tests to the new execution shape.
- [An inferred or platform-specific Ktor signature or erased type alias escapes review] -> Generate ABI for supported targets, inspect Android generic signatures and Kotlin metadata, and keep a deliberate Android-only Ktor alias regression fixture in the gate.
- [Kotlin/Native metadata represents implementation dependencies as compiler API dependencies] -> Verify declared source-set scope and compiled ABI for Native; reserve publication API-metadata exclusion for Android where API/runtime variants are distinct.
- [Consumers interpret `implementation` as arbitrary version compatibility] -> State runtime compatibility precisely in README migration guidance and test only a known-compatible consumer-selected version.
- [One PR spans API, streaming, and build gates] -> Keep the v0.1.0 breaking boundary atomic, organize implementation as separately reviewable commits, and require focused streaming plus boundary validation before the aggregate suite.
- [Close or disk failure leaves a partial consumer file] -> Propagate cancellation/callback failure and document that callers should write to a temporary destination and promote it only after `download` returns.

## Migration Plan

1. Introduce `FanboxLogLevel`, change cookie signatures to `FanboxCookieRecord`, remove raw-client access, and replace download execution in one breaking commit series.
2. Move Ktor declarations to `implementation`, enable ABI validation, generate the intended v0.1.0 baseline, and add declared-scope plus Android publication checks to PR and release CI.
3. Update repository consumers, tests, README, and KDoc together.
4. Validate common, Android, iOS, Android publication metadata, declared scopes, and a locally published consumer compile fixture before publishing v0.1.0; the release workflow blocks publication until the same boundary checks pass.
5. External consumers migrate logging/cookies/downloads and own any general-purpose HTTP clients before adopting v0.1.0.

Rollback before publication is a source revert. After v0.1.0 publication, fixes must preserve the new Ktor-free boundary; restoring the Ktor-exposing v0.0.x signatures would require a separate compatibility release decision.

## Open Questions

None. The public API shape and migration boundary are fixed for implementation; independent falsification remains required before code changes because the download path is security- and memory-sensitive.
