## 1. PR 1 — Source set split and dependency cleanup

Targets remain Android and iOS. The existing CI acts as the regression gate for this stage.

- [ ] 1.1 Declare a `clientMain` intermediate source set between `commonMain` and both `androidMain` and `iosMain`, and a matching `clientTest`, and verify that the existing Android and iOS compilations still resolve
- [ ] 1.2 Remove `kotlin-stdlib-jdk8` and `kotlin-reflect` from the `:fankt:fanbox` common dependencies, declare `kotlinx-serialization-json` and `kotlinx-collections-immutable` individually, and move `napier` to `clientMain`, leaving `bundles.infra.api` intact for other modules
- [ ] 1.3 Move `Fanbox.kt`, `ClientBuilder.kt`, `FanboxDependencies.kt`, `FanboxCookiesStorageAdapter.kt`, and `FanboxDownloadDestination.kt` to `clientMain` without changing their contents
- [ ] 1.4 Move `transport/ktor/**` and the four `repository/**` files to `clientMain` without changing their contents
- [ ] 1.5 Split `FanboxExceptionFactory` so that `fromDownloadHttpResponse` moves to `clientMain` against the download implementation while the remaining pure delegations stay in `commonMain`, and confirm the download failure semantics are unchanged
- [ ] 1.6 Split metadata parsing so that `commonMain` holds the metadata JSON decoder and the extraction contract, and `clientMain` holds the Ksoup implementation that locates the `meta[name=metadata]` content, preserving the existing missing-element and malformed-JSON schema mismatch behavior
- [ ] 1.7 Move the thirteen MockEngine-dependent tests from `commonTest` to `clientTest`, keeping the `fixture/**` helpers in `commonTest`
- [ ] 1.8 Run Android and iOS builds, `:fankt:fanbox:testDebugUnitTest`, detekt, ABI checks, and the three boundary verification tasks, and confirm the ABI dumps are unchanged

## 2. PR 2 — Kotlin/JS target

- [ ] 2.1 Add a `matsumo.primitive.kmp.js` primitive plugin that declares `js(IR)` with a library binary and a Node.js test environment, and apply it to `:fankt:fanbox`
- [ ] 2.2 Resolve every remaining unresolved reference reported by the Kotlin/JS compilation of `commonMain`, moving any newly found non-portable code to `clientMain`
- [ ] 2.3 Rewrite `response/FanboxUrlPartsTest.kt` to assert against Ktor-free expected values so it can run on every target
- [ ] 2.4 Confirm that `:fankt:fanbox:jsTest` runs the three mapper golden tests, the metadata decoding test, the endpoint builder test, the two response tests, and the three serialization tests, and that all pass
- [ ] 2.5 Regenerate the klib ABI dump, and verify that the Kotlin/JS target is recorded and no declaration previously available to Android or iOS is removed
- [ ] 2.6 Add a test or verification step proving that a Ktor import added to `commonMain` fails the Kotlin/JS compilation
- [ ] 2.7 Run the full check task on Android, iOS, and Kotlin/JS and confirm no regression

## 3. PR 3 — Verification tasks, CI, and publication

- [ ] 3.1 Restrict `verifyPortableImportBoundary` to scan `commonMain`, add JVM-only and Ksoup import roots to its forbidden list, and confirm the non-empty scan guard still fires when the paths are wrong
- [ ] 3.2 Add the Kotlin/JS compile classpath to the `verifyPersistenceBoundary` inspected configurations
- [ ] 3.3 Add the Kotlin/JS publication task to `requiredPublicationMetadataTasks`, outside the macOS-only condition
- [ ] 3.4 Add the Kotlin/JS compilation and `:fankt:fanbox:jsTest` to the pull request workflow
- [ ] 3.5 Confirm the release workflow publishes the Kotlin/JS artifact alongside the existing targets
- [ ] 3.6 Update the `README.md` platform table and the introduction to record Kotlin/JS support and the boundary between the portable core and HTTP execution, and grep the documentation for statements invalidated by the source set split
