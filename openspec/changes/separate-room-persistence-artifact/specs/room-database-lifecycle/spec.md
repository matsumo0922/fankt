## MODIFIED Requirements

### Requirement: Shared DAO ownership
Only storage explicitly created from the optional `fanbox-persistence-room` artifact SHALL construct a `FanktDatabase`. Each storage SHALL own its own database instance and Cookie DAO and SHALL NOT publish the instance through a process-global accessor. Default in-memory and host-injected storage SHALL NOT initialize that database. The database SHALL NOT expose or construct a token DAO, and the storage API SHALL NOT delete database files. This requirement traces to Issue #34’s artifact-separation task and explicit-initialization recommendation.

#### Scenario: Explicit Room storage creation
- **WHEN** one or more Room storage instances are explicitly created in the same process
- **THEN** each storage owns a separately closeable database instance and its CSRF token state is not database-backed

#### Scenario: Room storage is reopened after close
- **WHEN** one Room storage is closed and a later factory call creates another storage
- **THEN** the later storage owns a new open database instance rather than the closed instance

#### Scenario: Default dependency graph creation
- **WHEN** one or more default `Fanbox` dependency graphs are created
- **THEN** no Cookie DAO or Room database is requested

#### Scenario: Host-injected dependency graph creation
- **WHEN** a `Fanbox` dependency graph is created with host-provided non-Room Cookie and token stores
- **THEN** fankt uses those stores and does not initialize a Room database

## REMOVED Requirements

### Requirement: Process-local Room database identity
**Reason**: Issue #34 removes the implicit process-global Room accessor and replaces it with explicit, instance-owned storage from an optional artifact. Retaining the singleton requirement would contradict the new ownership contract.

**Migration**: Create one Room storage for the host lifecycle that needs persistence, inject it into `Fanbox`, close `Fanbox`, and then close the storage. A later factory call creates a fresh storage instance over the same unchanged `fankt.db`.
