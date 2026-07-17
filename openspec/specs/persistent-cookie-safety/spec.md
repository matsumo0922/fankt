# Persistent Cookie Safety

## Purpose

Define how persisted FANBOX Cookies are selected, expired, identified, migrated, and dispatched without leaking authentication data across request boundaries.

## Requirements

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
Persistent Cookie storage SHALL represent session Cookies with a null expiry, exclude Cookies with a non-null expiry at or before the read clock from responses, and attempt to delete every row that is still expired after the snapshot read. Cleanup failure SHALL NOT permit expired Cookie delivery and SHALL be retried by a later read. `maxAge` SHALL be converted to an absolute expiry when the Cookie is stored. This requirement traces to Issue #20 acceptance criterion 3.

#### Scenario: Expired Cookie is removed
- **WHEN** a persisted Cookie expiry is earlier than the read clock
- **THEN** storage does not return the Cookie and attempts to delete every currently expired DB row

#### Scenario: Cookie expires at the read boundary
- **WHEN** a persisted Cookie expiry equals the read clock
- **THEN** storage treats the Cookie as expired

#### Scenario: Cleanup write fails
- **WHEN** expired-row deletion fails after storage reads a Cookie snapshot
- **THEN** storage still returns only unexpired matching Cookies and retries cleanup on a later read

#### Scenario: Max-age survives persistence
- **WHEN** a Cookie with positive `maxAge` is stored
- **THEN** its persisted absolute expiry equals the storage clock plus `maxAge` seconds

#### Scenario: Session Cookie remains active
- **WHEN** a Cookie has neither `maxAge` nor `expires`
- **THEN** storage persists a null expiry and does not classify it as expired

### Requirement: Stable Cookie identity
Persistent Cookies SHALL be uniquely identified by their effective domain, path, and name, including Cookies whose input domain or path is absent. This requirement traces to Issue #20 task "CookieEntity.id generation review".

#### Scenario: Host-only Cookie is replaced
- **WHEN** two Cookies with the same name and effective request host and path are stored
- **THEN** the later Cookie replaces the earlier Cookie instead of creating a nullable-domain duplicate

#### Scenario: Equivalent domain spellings are replaced
- **WHEN** Cookies differ only by domain case or a leading domain dot
- **THEN** storage canonicalizes them to one domain/path/name identity and returns at most one Cookie value

### Requirement: Safe schema upgrade
The database SHALL migrate v1 Cookie rows to the canonical identity without losing a value for each identity, and SHALL treat migrated rows as secure-only because v1 did not persist the secure attribute. The exact v1 session sentinel `expiresAt = -1` SHALL become null; every other expiry value SHALL remain an absolute timestamp so expired Cookies cannot become sessions. If v1 contains duplicate canonical identities, the row with the greatest SQLite `rowid` SHALL be retained deterministically because v1 stores no creation timestamp. The expiry column SHALL be indexed for request-time cleanup.

#### Scenario: Existing Cookie migration
- **WHEN** a v1 database is opened by the new library
- **THEN** each canonical domain, path, and name has one retained row and that row is marked secure

#### Scenario: Expired epoch Cookie migration
- **WHEN** a v1 Cookie has `expiresAt = 0` or another non-sentinel past timestamp
- **THEN** migration preserves that timestamp instead of converting the Cookie into a session

### Requirement: Injected IO execution
Cookie writes SHALL invoke the DAO from the `ioDispatcher` supplied to `PersistentCookieStorage`. Room MAY perform the database operation on its configured query context. This requirement traces to Issue #20 task "addCookie dispatcher unification".

#### Scenario: Cookie write dispatch
- **WHEN** `addCookie` persists a Cookie
- **THEN** storage enters the injected `ioDispatcher` before invoking the DAO
