# Room Database Lifecycle

## Purpose

Define the process-local lifetime and ownership guarantees for the FANBOX Room database and its DAOs.

## Requirements

### Requirement: Process-local Room database identity
After successful initialization, the FANBOX persistence layer SHALL return the same `FanktDatabase` reference from every successful `getFanktDatabase()` call within one platform process. The first database construction SHALL be lazy and serialized across concurrent callers. An initializer failure SHALL propagate to that caller without publishing a partial instance, and a later call SHALL retry initialization. This requirement traces to Issue #23 acceptance criterion "`getFanktDatabase()` を複数回呼んでも同一インスタンスが返ること".

#### Scenario: Repeated accessor calls
- **WHEN** `getFanktDatabase()` completes successfully more than once in the same process
- **THEN** every successful call returns the identical `FanktDatabase` reference

#### Scenario: Concurrent first access
- **WHEN** multiple callers invoke `getFanktDatabase()` concurrently and initialization succeeds
- **THEN** one database instance is constructed and that identical reference is published to every successful caller

#### Scenario: Initializer failure
- **WHEN** database construction throws before an instance is published
- **THEN** the exception propagates to that caller and a later call retries construction without observing a partial instance

### Requirement: Shared DAO ownership
Only an explicitly requested legacy Room-backed `FanboxCookieStorage` SHALL construct a `FanktDatabase`. Each legacy bridge SHALL own its own database instance and Cookie DAO, SHALL close that instance before file deletion, and SHALL NOT publish the instance through the process-global default accessor. Default in-memory and host-injected storage SHALL NOT initialize that database. The database SHALL NOT expose or construct a token DAO.

#### Scenario: Explicit legacy storage creation
- **WHEN** one or more legacy Room storage bridges are explicitly created in the same process
- **THEN** each bridge owns a separately closeable database instance and its CSRF token state is not database-backed

#### Scenario: Legacy bridge is reopened after close
- **WHEN** one legacy bridge is closed and a later factory call creates another bridge
- **THEN** the later bridge owns a new open database instance rather than the closed instance

#### Scenario: Default dependency graph creation
- **WHEN** one or more default `Fanbox` dependency graphs are created
- **THEN** no Cookie DAO or Room database is requested

#### Scenario: Host-injected dependency graph creation
- **WHEN** a `Fanbox` dependency graph is created with host-provided Cookie and token stores
- **THEN** fankt uses those stores and does not initialize its Room database
