## MODIFIED Requirements

### Requirement: Owned lifecycle
Each explicitly created Room storage SHALL own one separately closeable database instance. Operations started after close SHALL fail, close SHALL be idempotent, and a later explicit creation for the same path SHALL return a fresh usable instance. A `Flow` obtained from the storage before close SHALL terminate with `IllegalStateException` when it is still being collected at close, and collecting such a `Flow` for the first time after close SHALL fail the same way, so a host observes one deterministic termination cause rather than an underlying database exception. Creating and closing `Fanbox` instances over one injected storage SHALL NOT create or close a database instance, and the storage SHALL remain usable until the host closes it. The storage API SHALL NOT delete the database file or its SQLite sidecars because it cannot prove that every separately created owner for that path is closed. Documentation SHALL require hosts to create only one storage per database path, explain that independently opened instances do not propagate invalidation Flow updates and can encounter competing writes, require hosts to close both `Fanbox` and the injected Room storage, and state the deterministic post-close termination cause.

#### Scenario: Storage is reopened after close
- **WHEN** one explicit Room storage is closed and the host creates storage for the same path again
- **THEN** the later storage owns a new open database instance and can read the persisted rows

#### Scenario: Live collector terminates at close
- **WHEN** a host is collecting a `Flow` obtained from the storage before close, and the storage is closed
- **THEN** that collector terminates with `IllegalStateException` rather than an underlying database exception or an indefinite suspension

#### Scenario: Live collector observing through Fanbox terminates at close
- **WHEN** a host is collecting `Fanbox.cookies` for a `Fanbox` injected with the storage, and the storage is closed while that `Fanbox` is still open
- **THEN** that collector terminates with `IllegalStateException`

#### Scenario: Storage outlives repeated client cycles
- **WHEN** a host repeatedly creates and closes `Fanbox` instances that share one injected Room storage
- **THEN** no additional database instance is created, no database instance is closed by those cycles, and the storage still reads and writes committed rows after the last `Fanbox` closes

#### Scenario: Storage cleanup is explicit
- **WHEN** a host finishes using a `Fanbox` with injected Room storage
- **THEN** documentation directs the host to close the `Fanbox` and then close the Room storage, while exposing no library API that can unlink a database still used by another owner

#### Scenario: Multiple owners are discouraged
- **WHEN** a host reads the Room persistence setup guidance
- **THEN** the guidance warns that multiple storage instances for one database path do not propagate Flow invalidations and can encounter competing writes
