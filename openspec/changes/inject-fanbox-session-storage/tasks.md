## 1. fankt storage contracts

- [x] 1.1 Add the public Ktor-independent `FanboxCookieRecord` including host-only scope, `FanboxCookieStorage` including `snapshot()` and atomic `replaceAll()`, and `FanboxTokenStore` contracts with credential-safe KDoc and explicit host ownership semantics.
- [x] 1.2 Implement mutex-protected, instance-local in-memory Cookie and token stores with finite snapshot reads, Flow observation, atomic full replacement, and deterministic mutation visibility.
- [x] 1.3 Add contract tests for finite snapshot reads independent of Flow scheduling, current-snapshot initial Flow emission, upsert/delete/clear/replaceAll failure atomicity, concurrent mutation, independent defaults, and explicitly shared store instances.

## 2. fankt authentication adapter and constructor injection

- [x] 2.1 Extract normalization, host-only/domain/path/secure matching, snapshot-based request reads, non-fatal expiry filtering/cleanup, and Ktor conversion into one internal `CookiesStorage` adapter over `FanboxCookieStorage`.
- [x] 2.2 Refactor `PersistentCookieStorage` into a record backend without changing Room schema v3, expose and coerce legacy records as `hostOnly = false`, and keep all persistent-cookie-safety scenarios passing through the common adapter.
- [x] 2.3 Extend the public `Fanbox` constructor after the existing positional arguments with per-call in-memory Cookie/token defaults, refactor `FanboxDependencies` to use only injected stores, and record the intentional binary incompatibility for clean-rebuilt v0.1.0 consumers.
- [x] 2.4 Remove `processCsrfToken`, route request-time token reads and session-reset clears through the injected token store, and preserve existing client lifecycle behavior.
- [x] 2.5 Add mock-engine tests proving default and custom stores execute every API client path without opening Room and that two default `Fanbox` instances cannot leak Cookie or CSRF state to each other.

## 3. fankt legacy migration bridge and documentation

- [x] 3.1 Provide an explicit transitional factory whose bridge owns a fresh current-platform `fankt.db` Room instance plus close/clear/database-file cleanup APIs suitable for PixiView migration.
- [x] 3.2 Add Android and iOS tests that open v1/v2/v3 legacy fixtures without schema upgrade, prove the retained `FANBOXSESSID` reaches `api.fanbox.cc`, prove legacy writes coerce `hostOnly` to false, close the bridge, reacquire a fresh usable bridge in the same process, and delete the database and SQLite sidecars only after verified migration cleanup.
- [x] 3.3 Update README and public KDoc in current tense for in-memory defaults, persistence injection, multi-instance ownership, the v0.1.0 durability and binary compatibility breaks, clean rebuild requirement, and the temporary bridge's support-baseline removal condition.
- [x] 3.4 Grep README/docs/KDoc for `Fanbox`, `PersistentCookieStorage`, `csrfToken`, `Room`, and constructor examples; correct stale persistence or sharing claims and record `ドキュメント影響: あり（対象ファイル）` in the fankt PR description.

## 4. fankt validation and downstream handoff

- [ ] 4.1 Run fankt common/Android/iOS tests, detekt, Room migration tests, and mock-engine production-path tests under the validation lease at one final HEAD.
- [x] 4.2 Verify source compatibility with existing constructor call forms, record intentional binary incompatibility, and clean-build `../PixiView-KMP` against the local fankt change when dependency substitution is available; otherwise document the exact unverified downstream build reason.
- [x] 4.3 Confirm the approved `pixiview-secure-session-migration` spec remains a downstream contract for PixiView-KMP#109, list its independent implementation as a follow-up in the PR, and do not edit PixiView source in this stage.
