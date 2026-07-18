## ADDED Requirements

### Requirement: Optional Room persistence artifact
The project SHALL publish `me.matsumo.fankt:fanbox-persistence-room` as an optional Kotlin Multiplatform artifact for Android and iOS. The artifact SHALL depend on `fanbox` and SHALL contain the Room, androidx.sqlite, database, DAO, migration, and storage implementation. The published `fanbox` artifact SHALL have no direct or transitive dependency on Room, androidx.sqlite, or androidx.startup and SHALL declare no AndroidX Startup provider. This requirement traces to Issue #34 acceptance criterion “`fankt/fanbox` 本体が Room に依存しない” and its publication task.

#### Scenario: Core dependency graph is inspected
- **WHEN** `:fankt:fanbox` dependency metadata is resolved for its published Android and iOS variants
- **THEN** Room, androidx.sqlite, androidx.startup, and `fanbox-persistence-room` are absent

#### Scenario: Optional artifact is published
- **WHEN** the release workflow publishes the project libraries
- **THEN** it successfully publishes `:fankt:fanbox-persistence-room` before publishing the breaking `:fankt:fanbox` artifact, so a failed optional-artifact publication cannot expose the core removal without its replacement

#### Scenario: Core Android manifest is merged
- **WHEN** an Android application depends only on `fanbox`
- **THEN** the dependency contributes no `androidx.startup.InitializationProvider` or fankt initializer metadata

### Requirement: Explicit platform initialization
Room persistence SHALL be created only by an explicit host call. Android creation SHALL require a `Context` and derive the database path from `context.applicationContext.getDatabasePath("fankt.db")`; iOS creation SHALL derive the path from `NSDocumentDirectory/fankt.db`. Neither artifact SHALL retain a process-global Android `Context`, and the optional artifact SHALL NOT register AndroidX Startup. This requirement traces to Issue #34’s unchanged-path implementation note and explicit-initialization recommendation.

#### Scenario: Android host creates storage
- **WHEN** an Android host explicitly creates Room storage with a `Context`
- **THEN** the storage opens that application’s existing `getDatabasePath("fankt.db")` without registering or requiring AndroidX Startup

#### Scenario: iOS host creates storage
- **WHEN** an iOS host explicitly creates Room storage
- **THEN** the storage opens the existing `NSDocumentDirectory/fankt.db` path

#### Scenario: Core client is created without persistence
- **WHEN** a host constructs `Fanbox()` without adding or calling the optional artifact
- **THEN** no Room database is opened and no global platform context is captured

### Requirement: Persistent session compatibility
The optional Room storage SHALL implement `FanboxCookieStorage`, preserve Room schema version 3 and the existing v1-to-v2 and v2-to-v3 migrations, and preserve the schema-v3 representation in which loaded and written records have `hostOnly = false`. Closing and recreating storage for the same path SHALL restore committed Cookie rows, including `FANBOXSESSID`, and injecting the recreated storage into a new `Fanbox` SHALL expose that session through the production authentication storage path. This requirement traces to Issue #34 acceptance criterion “cookie 永続化・再起動後のセッション復元” and the no-migration implementation note.

#### Scenario: Existing database versions are opened
- **WHEN** explicit Room storage opens an existing v1, v2, or v3 `fankt.db`
- **THEN** it exposes the retained Cookie rows under schema v3 without destructive migration or a path change

#### Scenario: Session survives storage recreation
- **WHEN** a session Cookie is committed, the storage is closed, and storage for the same database path is explicitly created again
- **THEN** a new `Fanbox` injected with the recreated storage observes the committed session Cookie

#### Scenario: Legacy host-only representation is retained
- **WHEN** the optional backend writes a `FanboxCookieRecord` whose `hostOnly` value is true
- **THEN** reopening the record returns `hostOnly = false` without changing schema version 3

### Requirement: Owned lifecycle
Each explicitly created Room storage SHALL own one separately closeable database instance. Operations after close SHALL fail, close SHALL be idempotent, and a later explicit creation for the same path SHALL return a fresh usable instance. The storage API SHALL NOT delete the database file or its SQLite sidecars because it cannot prove that every separately created owner for that path is closed. Documentation SHALL require hosts to close both `Fanbox` and the injected Room storage. This requirement traces to Issue #34’s explicit-initialization choice and the existing non-regression lifecycle invariant.

#### Scenario: Storage is reopened after close
- **WHEN** one explicit Room storage is closed and the host creates storage for the same path again
- **THEN** the later storage owns a new open database instance and can read the persisted rows

#### Scenario: Storage cleanup is explicit
- **WHEN** a host finishes using a `Fanbox` with injected Room storage
- **THEN** documentation directs the host to close the `Fanbox` and then close the Room storage, while exposing no library API that can unlink a database still used by another owner
