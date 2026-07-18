## 1. Module Boundary

- [x] 1.1 Add `:fankt:fanbox-persistence-room` with Android/iOS, Room, detekt, and Maven publication configuration and a dependency on `:fankt:fanbox`.
- [x] 1.2 Move the Room database, DAO, entity, migrations, schema files, persistent storage adapter, and platform path/build logic into the optional module.
- [x] 1.3 Remove Room/sqlite/startup plugins, dependencies, sources, and initializer manifest metadata from `:fankt:fanbox`.

## 2. Explicit Storage API and Compatibility

- [x] 2.1 Implement closeable `RoomFanboxCookieStorage` and explicit Android/iOS factories without file-deletion API while preserving schema v3, database paths, and legacy `hostOnly = false` behavior.
- [x] 2.2 Move and adapt storage, migration, lifecycle, and v1/v2/v3 fixture tests to the optional module.
- [x] 2.3 Add restart restoration and production `Fanbox` injection tests for a persisted `FANBOXSESSID`.
- [x] 2.4 Add a dependency/manifest boundary regression check proving `:fankt:fanbox` contains no Room, sqlite, Startup, or optional-module dependency.

## 3. Publication and Documentation

- [x] 3.1 Add the optional artifact to Dokka aggregation and publish it before the breaking core artifact in `deploy-library.yml`.
- [x] 3.2 Update README dependency and explicit Android/iOS storage-injection examples, lifecycle guidance, and removal of the old bridge wording.
- [x] 3.3 Search README, docs, and KDoc for moved APIs and stale Room/startup behavior, then update all affected current-specification text.

## 4. Validation

- [ ] 4.1 Run OpenSpec validation and targeted persistence/core tests.
- [ ] 4.2 Run full test, lint/detekt, build, dependency-report, and publication-task validation under the validation lease with isolated Gradle and Kotlin/Native caches.
- [ ] 4.3 Record Scenario-to-test evidence, validation commands, results, and the validated HEAD SHA for PR review.
