## ADDED Requirements

### Requirement: Request-scoped Cookie selection
Persistent Cookie storage SHALL return only unexpired Cookies whose domain, path, and secure attributes match the request URL according to Ktor's Cookie matching semantics. This requirement traces to Issue #20 acceptance criteria 1 and 2.

#### Scenario: FANBOX domain Cookie is selected
- **WHEN** storage contains a Cookie for `.fanbox.cc` and a request targets `https://api.fanbox.cc/`
- **THEN** storage returns that Cookie

#### Scenario: Cross-origin Cookie is rejected
- **WHEN** storage contains a Cookie for `pixiv.net` and a request targets `https://api.fanbox.cc/`
- **THEN** storage does not return that Cookie

#### Scenario: Path mismatch is rejected
- **WHEN** storage contains a Cookie scoped to `/account/` and a request targets `/post.info`
- **THEN** storage does not return that Cookie

#### Scenario: Secure Cookie is rejected on HTTP
- **WHEN** storage contains a secure Cookie and a request uses `http`
- **THEN** storage does not return that Cookie

### Requirement: Expired Cookie cleanup
Persistent Cookie storage SHALL exclude expired Cookies from responses and delete their persisted rows during a read. `maxAge` SHALL be converted to an absolute expiry when the Cookie is stored. This requirement traces to Issue #20 acceptance criterion 3.

#### Scenario: Expired Cookie is removed
- **WHEN** a persisted Cookie expiry is earlier than the read clock
- **THEN** storage does not return the Cookie and deletes its DB row

#### Scenario: Max-age survives persistence
- **WHEN** a Cookie with positive `maxAge` is stored
- **THEN** its persisted absolute expiry equals the storage clock plus `maxAge` seconds

### Requirement: Stable Cookie identity
Persistent Cookies SHALL be uniquely identified by their effective domain, path, and name, including Cookies whose input domain or path is absent. This requirement traces to Issue #20 task "CookieEntity.id generation review".

#### Scenario: Host-only Cookie is replaced
- **WHEN** two Cookies with the same name and effective request host and path are stored
- **THEN** the later Cookie replaces the earlier Cookie instead of creating a nullable-domain duplicate

### Requirement: Safe schema upgrade
The database SHALL migrate v1 Cookie rows to the new identity without losing their values, and SHALL treat migrated rows as secure-only because v1 did not persist the secure attribute.

#### Scenario: Existing Cookie migration
- **WHEN** a v1 database is opened by the new library
- **THEN** each distinct effective domain, path, and name has one retained row and that row is marked secure

### Requirement: Injected IO execution
Cookie writes SHALL use the `ioDispatcher` supplied to `PersistentCookieStorage`. This requirement traces to Issue #20 task "addCookie dispatcher unification".

#### Scenario: Cookie write dispatch
- **WHEN** `addCookie` persists a Cookie
- **THEN** the DAO write runs on the injected `ioDispatcher`
