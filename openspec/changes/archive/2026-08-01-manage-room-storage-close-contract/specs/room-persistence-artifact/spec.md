## MODIFIED Requirements

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
