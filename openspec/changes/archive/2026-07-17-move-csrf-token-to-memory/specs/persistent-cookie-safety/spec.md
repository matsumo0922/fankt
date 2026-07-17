## MODIFIED Requirements

### Requirement: Safe schema upgrade
The database SHALL migrate v1 Cookie rows to the canonical identity without losing a value for each identity, and SHALL treat migrated rows as secure-only because v1 did not persist the secure attribute. The exact v1 session sentinel `expiresAt = -1` SHALL become null; every other expiry value SHALL remain an absolute timestamp so expired Cookies cannot become sessions. If v1 contains duplicate canonical identities, the row with the greatest SQLite `rowid` SHALL be retained deterministically because v1 stores no creation timestamp. The expiry column SHALL be indexed for request-time cleanup. The v2-to-v3 migration SHALL preserve that Cookie table and index unchanged while removing only the obsolete CSRF token table.

#### Scenario: Existing Cookie migration
- **WHEN** a v1 database is opened by the v3 library
- **THEN** each canonical domain, path, and name has one retained row, that row is marked secure, and the obsolete CSRF token table is absent

#### Scenario: Expired epoch Cookie migration
- **WHEN** a v1 Cookie has `expiresAt = 0` or another non-sentinel past timestamp
- **THEN** migration preserves that timestamp instead of converting the Cookie into a session

#### Scenario: Version two Cookie preservation
- **WHEN** a v2 database is opened by the v3 library
- **THEN** all Cookie rows and the expiry index remain unchanged while only the CSRF token table is dropped

