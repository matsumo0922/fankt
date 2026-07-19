## MODIFIED Requirements

### Requirement: Explicit legacy Room access
The optional `fanbox-persistence-room` artifact SHALL provide an explicit Room-backed `FanboxCookieStorage` factory that reads Android `getDatabasePath("fankt.db")` or iOS `NSDocumentDirectory/fankt.db` and implements the same storage contract. The backend SHALL keep schema v3 unchanged, SHALL expose every legacy row with `hostOnly = false`, and SHALL coerce writes to `hostOnly = false` because the schema cannot preserve that distinction. Each storage SHALL own its database instance and SHALL permit a later factory call to create a fresh instance after close. It SHALL NOT expose database-file deletion because one instance cannot prove that other owners for the same path are closed. The `fanbox` artifact and default `Fanbox` construction SHALL NOT contain or select this backend implicitly. This requirement traces to Issue #34 acceptance criterion “persistence-room を注入した場合の動作が現行と同等” and its unchanged-database implementation note.

#### Scenario: Existing database is opened explicitly
- **WHEN** a host adds the optional artifact and explicitly requests Room storage on a device with existing Cookie rows
- **THEN** the storage exposes those rows through `FanboxCookieStorage.cookies` with their original fields unchanged and `hostOnly = false`

#### Scenario: Room write preserves current domain behavior
- **WHEN** the Room storage stores a Cookie record supplied with `hostOnly = true`
- **THEN** it reloads that record with `hostOnly = false` without changing the Room schema version

#### Scenario: Legacy FANBOX session still reaches API host
- **WHEN** a v1, v2, or v3 fixture contains the existing domain-scoped `FANBOXSESSID`, the optional storage opens it, and that storage is injected into `Fanbox`
- **THEN** the common authentication adapter makes the session available for `https://api.fanbox.cc/`

#### Scenario: Core default avoids legacy database
- **WHEN** a host depends only on `fanbox` and constructs `Fanbox()`
- **THEN** fankt does not include Room or open `fankt.db`

#### Scenario: Closed storage can be reacquired
- **WHEN** one Room storage closes and a later caller explicitly creates storage again in the same process
- **THEN** the factory returns a fresh usable database instance rather than the closed instance
