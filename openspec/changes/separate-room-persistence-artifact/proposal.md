## Why

`fankt/fanbox` still packages Room, androidx.sqlite, and androidx.startup even though Issue #33 made authentication storage injectable and the default path Room-free. Separating the optional Room backend is the remaining dependency boundary required by Issue #34 before additional non-Android targets can be considered.

## What Changes

- Add an optional `fanbox-persistence-room` KMP artifact that implements `FanboxCookieStorage` with the existing Room schema and database locations on Android and iOS.
- **BREAKING**: move the legacy Room factory and all Room database implementation types out of `fanbox`; callers that need persistence add the new artifact and explicitly create/inject its storage.
- Remove Room, androidx.sqlite, androidx.startup, and the AndroidX Startup provider from the `fanbox` artifact.
- Publish `fanbox-persistence-room` to Maven Central and document explicit persistent-storage setup.
- Preserve the existing `fankt.db` path, schema v3, v1/v2 migrations, Cookie semantics, and close/reopen lifecycle; remove library-owned database-file deletion from the long-lived persistence backend.

## Capabilities

### New Capabilities

- `room-persistence-artifact`: Defines the optional published Room storage artifact, its explicit platform initialization, dependency boundary, and restart durability. This traces to every acceptance criterion and implementation note in Issue #34.

### Modified Capabilities

- `injectable-auth-storage`: Moves explicit Room access from the core artifact to the optional persistence artifact while retaining the injected `FanboxCookieStorage` contract. This traces to Issue #34 acceptance criterion “persistence-room を注入した場合の動作が現行と同等”.
- `room-database-lifecycle`: Replaces the former core-owned legacy bridge wording and singleton accessor with explicitly created, instance-owned storage in the optional artifact. This traces to Issue #34’s explicit-initialization recommendation and unchanged-database constraint.

## Impact

- Gradle modules and publication: `settings.gradle.kts`, `fankt/fanbox`, new `fankt/fanbox-persistence-room`, `.github/workflows/deploy-library.yml`, and Dokka aggregation.
- Public API: the Room storage factory moves to the new artifact and requires explicit platform initialization; `Fanbox`, `FanboxCookieStorage`, and default in-memory behavior remain in `fanbox`.
- Data compatibility: Android continues to use `Context.getDatabasePath("fankt.db")`; iOS continues to use `NSDocumentDirectory/fankt.db`; Room schema remains version 3.
- Documentation and tests: README dependency/injection example, dependency-boundary verification, schema migration tests, restart restoration tests, and production `Fanbox` injection coverage.
