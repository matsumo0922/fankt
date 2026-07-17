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
Each `Fanbox` dependency graph SHALL obtain its Cookie DAO and token DAO from the process-local `FanktDatabase` instance. This requirement traces to Issue #23 task "`Fanbox` 側は同一インスタンスから両 DAO を取得する".

#### Scenario: Dependency graph creation
- **WHEN** one or more `Fanbox` dependency graphs are created in the same process
- **THEN** their Cookie DAO and token DAO are obtained from the same process-local database instance
