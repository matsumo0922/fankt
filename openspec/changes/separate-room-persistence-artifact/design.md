## Context

Issue #33 already makes `FanboxCookieStorage` and `FanboxTokenStore` host-injectable and makes `Fanbox()` use per-instance in-memory storage. Room remains in `fankt/fanbox` only because the explicit legacy bridge, schema, DAO, migrations, platform builders, AndroidX Startup initializer, and their tests still live there. Issue #34 requires a binary dependency boundary without changing the on-device `fankt.db` location or schema.

This is a cross-layer and migration-sensitive change. The contract source is Issue #34; delta-spec Scenarios are the validation units.

## Goals / Non-Goals

**Goals:**

- Make the published `fanbox` dependency graph free of Room, androidx.sqlite, and androidx.startup.
- Preserve Android and iOS access to existing schema-v1/v2/v3 `fankt.db` files through an optional artifact.
- Require hosts to create persistent storage explicitly and inject it into `Fanbox`.
- Publish and document the optional artifact in the same PR.

**Non-Goals:**

- Add JS, Wasm, or Desktop targets in this change; Issue #34 only removes the Room blocker.
- Change Cookie matching, schema v3, database path, or CSRF persistence.
- Reintroduce implicit persistence or process-global database/context access.
- Add a new encrypted persistence format or migrate data out of Room.

## Decisions

### 1. Create `:fankt:fanbox-persistence-room` / `fanbox-persistence-room`（agent 仮決め）

The Gradle module depends on `api(project(":fankt:fanbox"))`, applies the existing Android, iOS, Room, detekt, and Maven publication conventions, and owns all Room/sqlite dependencies. The Maven artifact follows the existing project-name coordinate convention.

Alternative: name the artifact `fankt-fanbox-persistence-room`. Rejected because existing coordinates already use the group `me.matsumo.fankt` and artifact `fanbox`; the shorter sibling name is consistent and avoids repeating the project name.

### 2. Replace implicit initialization with platform-explicit factories（agent 仮決め）

The optional artifact exposes a closeable `RoomFanboxCookieStorage` implementing `FanboxCookieStorage`. Android exposes `createRoomFanboxCookieStorage(context, ioDispatcher)` and derives the path from `context.applicationContext.getDatabasePath("fankt.db")`. iOS exposes `createRoomFanboxCookieStorage(ioDispatcher)` and derives the existing Documents path. The new module has no Startup provider and never stores a global `Context`.

Alternative: retain `FanktInitializer` in the optional artifact. Rejected because it preserves the surprising implicit global state that Issue #34 explicitly recommends removing.

Alternative: expose only a caller-supplied database path. Rejected as the primary API because it makes accidental path drift easier. Platform factories keep the existing location authoritative; internal path injection remains test-only.

### 3. Move the Room implementation as one boundary and preserve schema identity（ユーザー確認済み: Issue #34）

DAO, entities, migrations, database class, builders, schema JSON, persistent storage adapter, and lifecycle tests move together. Table names, columns, indices, migration SQL, version 3, Android path, and iOS path remain unchanged. Generated schema output is checked against the moved schema files, including the current v3 identity hash `f2777107128e294b9b88e002cb5712fb`.

Package/class names may change because Room compatibility is defined by the database schema and identity hash, not the Kotlin type name. Tests open v1/v2/v3 fixtures at the production path and verify retained rows.

### 4. Keep one database per explicit storage instance（agent 仮決め）

No process-global singleton replaces the initializer. Each factory call builds a database and returns an owning storage wrapper. `close()` is idempotent; reads and writes after close fail; reopening creates a fresh instance. The public storage exposes no database-file deletion method: a single instance cannot prove that another explicit owner for the same path is closed, so unlinking would be unsafe. Hosts that intentionally delete app data must coordinate every owner outside this library. This retains safe close/reopen behavior from Issue #33 without promoting its one-shot migration cleanup API into a long-lived backend.

### 5. Publish the replacement before the breaking core（agent 仮決め）

The release workflow publishes `fanbox-persistence-room` first and `fanbox` afterward. If the optional artifact fails to publish, the job stops before the core factory removal is released. If the later core publication fails, the additive optional artifact may already exist but no existing caller is broken. Maven Central cannot atomically publish separate coordinates, so ordering is the fail-safe boundary.

Alternative: keep the current core-first order. Rejected because a failure in the new artifact step would irreversibly publish the breaking removal without a replacement.

### 6. Prove both dependency separation and production injection（agent 仮決め）

Validation combines:

- Gradle dependency output/metadata assertions for the core artifact and a manifest-source assertion that Startup metadata is absent.
- Migration fixtures for v1/v2/v3 and restart tests that close and recreate storage at the same production-derived path.
- A production-call-path test that injects recreated Room storage into a public `Fanbox`, observes the restored session through `Fanbox.cookies`, and verifies the domain-scoped session is selected by the core authentication path where practical.
- Publication task existence and workflow wiring.

## Risks / Trade-offs

- [Room-generated schema identity changes during the move] → retain schema JSON, compare generated v3 identity, and open real v1/v2/v3 fixtures without destructive migration.
- [Android callers lose implicit `Context` availability] → make `Context` a required factory argument and document the one-line dependency/factory/injection path.
- [The core still appears Room-free only in source while published metadata leaks it transitively] → inspect Gradle dependency metadata for core variants, not only source imports.
- [Moving internal canonicalization helpers across the module boundary duplicates behavior] → use public `FanboxCookieRecord` values and keep storage normalization inside the optional backend; the core adapter remains the sole request-matching authority.
- [Two explicit instances open the same SQLite file concurrently] → do not expose file deletion; each instance closes only its own database. Document one host-owned storage per lifecycle and explicit close order.
- [The new persistence artifact fails to publish] → publish it before the breaking core coordinate and rely on workflow fail-fast behavior.
- [Breaking removal of the old factory surprises callers] → the v0.1.0 issue is explicitly breaking; README shows the replacement artifact and API.

## Migration Plan

1. Publish `fanbox-persistence-room` at the release version before publishing `fanbox` at that version.
2. Callers that do not need restart persistence keep `Fanbox()` and remove no data.
3. Callers that need Room persistence add `me.matsumo.fankt:fanbox-persistence-room:<version>`, explicitly create platform Room storage, and pass it as `Fanbox(cookieStorage = storage)`.
4. Existing `fankt.db` is opened in place. No file copy or schema migration beyond the existing v1/v2-to-v3 migrations occurs.
5. If optional-artifact publication fails, stop before publishing core. If core publication then fails, the already published optional artifact is additive and the old core remains available.
6. Rollback uses the prior library version against the unchanged schema-v3 file. The new change creates no schema-v4 state.

## Open Questions

None blocking. Artifact and public factory names are low-risk agent choices and are called out for human review in the PR description.
