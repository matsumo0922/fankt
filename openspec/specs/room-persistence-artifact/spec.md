# Room Persistence Artifact

## Purpose

Define the optional Room persistence artifact, its explicit platform initialization, database compatibility, and host-owned lifecycle.
## Requirements
### Requirement: Optional Room persistence artifact
The project SHALL publish `me.matsumo.fankt:fanbox-persistence-room` as an optional Kotlin Multiplatform artifact for Android and iOS. The artifact SHALL depend on `fanbox` and SHALL contain the Room, androidx.sqlite, database, DAO, migration, and storage implementation. The published `fanbox` artifact SHALL have no direct or transitive dependency on Room, androidx.sqlite, or androidx.startup and SHALL declare no AndroidX Startup provider.

#### Scenario: Core dependency graph is inspected
- **WHEN** `:fankt:fanbox` dependency metadata is resolved for its published Android and iOS variants
- **THEN** Room, androidx.sqlite, androidx.startup, and `fanbox-persistence-room` are absent

#### Scenario: Optional artifact is published
- **WHEN** the release workflow publishes the project libraries
- **THEN** every coordinate uses the same previously unpublished v0.1.0 version and it successfully publishes `:fankt:fanbox-persistence-room` before publishing the breaking `:fankt:fanbox` artifact, so a failed optional-artifact publication cannot expose the core removal without its replacement

#### Scenario: Core Android manifest is merged
- **WHEN** an Android application depends only on `fanbox`
- **THEN** the dependency contributes no `androidx.startup.InitializationProvider` or fankt initializer metadata

### Requirement: Explicit platform initialization
Room persistence SHALL be created only by an explicit host call. Android creation SHALL require a `Context` and derive the database path from `context.applicationContext.getDatabasePath("fankt.db")`; iOS creation SHALL derive the path from `NSDocumentDirectory/fankt.db`. Neither artifact SHALL retain a process-global Android `Context`, and the optional artifact SHALL NOT register AndroidX Startup.

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
The optional Room storage SHALL implement `FanboxCookieStorage`, preserve Room schema version 3 and the existing v1-to-v2 and v2-to-v3 migrations, and preserve the schema-v3 representation in which loaded and written records have `hostOnly = false`. Closing and recreating storage for the same path SHALL restore committed Cookie rows, including `FANBOXSESSID`, and injecting the recreated storage into a new `Fanbox` SHALL expose that session through the production authentication storage path.

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
Each explicitly created Room storage SHALL own one separately closeable database instance. Operations started after close SHALL fail, close SHALL be idempotent, and a later explicit creation for the same path SHALL return a fresh usable instance.

Close SHALL be linearized at one observable point, after which every operation the storage itself starts SHALL fail and no collection SHALL newly reach the database.

A collection of a `Flow` obtained from the storage SHALL terminate with `IllegalStateException` when close is the first terminal event to reach that collection, and a collection that starts after close SHALL fail the same way before it reaches the database. When another terminal event reaches the collection first — cancellation of the collecting coroutine, a failure thrown by the collector itself, or an upstream failure delivered while the close signal is incomplete — that event SHALL be the observed cause and SHALL be propagated unchanged.

Because close does not suspend and does not preempt a running collector, a terminal event raised inside the collection after close is linearized but before the collection observes the close SHALL still be the observed cause. The storage SHALL NOT intercept a collector's own exception or the collecting coroutine's cancellation in order to report close instead, because doing so would discard the host's exception and break structured cancellation. Documentation SHALL state that a host must not depend on the observed cause in that window.

Closing SHALL NOT require a collection to have unwound first, and close SHALL NOT suspend. Close SHALL NOT be called from a coroutine context whose dispatcher cannot make progress concurrently with the storage's query dispatcher, and documentation SHALL state that restriction.

Creating and closing `Fanbox` instances over one injected storage SHALL NOT close that storage or its database, and the storage SHALL remain usable until the host closes it. Absence of a database in the core `fanbox` artifact SHALL be enforced by the published dependency and manifest boundary check rather than by runtime instance counting.

The storage API SHALL NOT delete the database file or its SQLite sidecars because it cannot prove that every separately created owner for that path is closed. Documentation SHALL require hosts to create only one storage per database path, explain that independently opened instances do not propagate invalidation Flow updates and can encounter competing writes, require hosts to close both `Fanbox` and the injected Room storage, and state the post-close termination cause together with its first-terminal-event condition.

#### Scenario: Storage is reopened after close
- **WHEN** one explicit Room storage is closed and the host creates storage for the same path again
- **THEN** the later storage owns a new open database instance and can read the persisted rows

#### Scenario: Close is the first terminal event for a live collection
- **WHEN** a host is collecting a `Flow` obtained from the storage, that collection has already observed a value, and the storage is closed while the collecting coroutine stays active, its collector throws nothing, and no upstream failure reaches that collection, until the collection observes the close
- **THEN** that collection terminates with `IllegalStateException` rather than an underlying database exception or an indefinite suspension

#### Scenario: Captured Flow is first collected after close
- **WHEN** a host obtains a `Flow` from the storage before close and starts collecting it only after close
- **THEN** that collection fails with `IllegalStateException` without subscribing to the underlying database Flow

#### Scenario: Live collection through Fanbox terminates at close
- **WHEN** a host is collecting `Fanbox.cookies` for a `Fanbox` injected with the storage, that collection has already observed a value, and the storage is closed while that `Fanbox` is still open, with the collecting coroutine staying active, its collector throwing nothing, and no upstream failure reaching that collection, until the collection observes the close
- **THEN** close is the first terminal event to reach that collection and it terminates with `IllegalStateException`

#### Scenario: A collection's own terminal event wins over close
- **WHEN** a collection of a storage `Flow` is cancelled, or its collector throws, and the collection has not yet observed the close
- **THEN** that cancellation or collector failure is the observed cause and close does not replace it, whether or not close has already been linearized

#### Scenario: The unobserved-close window is documented
- **WHEN** a host reads the close guidance
- **THEN** it states that a terminal event raised inside a collection after close is linearized but before that collection observes the close is still the observed cause, so the host must not depend on the cause in that window

#### Scenario: Failure while open is not rewritten
- **WHEN** the storage's Flow composition receives an upstream failure while the close signal is incomplete
- **THEN** the collection observes that failure unchanged rather than as a close failure

#### Scenario: Storage outlives repeated client cycles
- **WHEN** a host repeatedly creates and closes `Fanbox` instances that share one injected Room storage, and then writes a new Cookie record through that storage
- **THEN** a collection established before those cycles observes the new record, and the storage still reads and writes committed rows after the last `Fanbox` closes

#### Scenario: Core artifact carries no database
- **WHEN** the published core `fanbox` dependency and manifest boundary check runs for its Android, iOS, and JS variants
- **THEN** Room, androidx.sqlite, androidx.startup, and the optional persistence artifact are absent, so a `Fanbox` cannot open a database of its own

#### Scenario: Storage cleanup is explicit
- **WHEN** a host finishes using a `Fanbox` with injected Room storage
- **THEN** documentation directs the host to close the `Fanbox` and then close the Room storage, while exposing no library API that can unlink a database still used by another owner

#### Scenario: Multiple owners are discouraged
- **WHEN** a host reads the Room persistence setup guidance
- **THEN** the guidance warns that multiple storage instances for one database path do not propagate Flow invalidations and can encounter competing writes

#### Scenario: Close context restriction is documented
- **WHEN** a host reads the Room storage close guidance
- **THEN** it states that close must not run on a context that cannot progress concurrently with the storage's query dispatcher, such as a single-parallelism dispatcher shared with that query context

