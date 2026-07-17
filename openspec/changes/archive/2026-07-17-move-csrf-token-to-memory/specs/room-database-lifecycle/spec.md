## MODIFIED Requirements

### Requirement: Shared DAO ownership
Each `Fanbox` dependency graph SHALL obtain its Cookie DAO from the process-local `FanktDatabase` instance. The database SHALL NOT expose or construct a token DAO after CSRF token persistence is removed. This requirement traces to Issue #23 task "`Fanbox` 側は同一インスタンスから両 DAO を取得する" as revised by Issue #24.

#### Scenario: Dependency graph creation
- **WHEN** one or more `Fanbox` dependency graphs are created in the same process
- **THEN** their Cookie DAOs are obtained from the same process-local database instance and their CSRF token state is not database-backed
