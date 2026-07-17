## ADDED Requirements

### Requirement: Process-local Room database identity
The FANBOX persistence layer SHALL return the same `FanktDatabase` reference from every `getFanktDatabase()` call within one platform process. The first database construction SHALL be lazy and serialized across concurrent callers. This requirement traces to Issue #23 acceptance criterion "`getFanktDatabase()` を複数回呼んでも同一インスタンスが返ること".

#### Scenario: Repeated accessor calls
- **WHEN** `getFanktDatabase()` is called more than once in the same process
- **THEN** every call returns the identical `FanktDatabase` reference

#### Scenario: Concurrent first access
- **WHEN** multiple callers first invoke `getFanktDatabase()` concurrently in the same process
- **THEN** one database instance is constructed and that identical reference is published to every caller

### Requirement: Shared DAO ownership
Each `Fanbox` dependency graph SHALL obtain its Cookie DAO and token DAO from the process-local `FanktDatabase` instance. This requirement traces to Issue #23 task "`Fanbox` 側は同一インスタンスから両 DAO を取得する".

#### Scenario: Dependency graph creation
- **WHEN** one or more `Fanbox` dependency graphs are created in the same process
- **THEN** their Cookie DAO and token DAO are obtained from the same process-local database instance
