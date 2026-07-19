# injectable-auth-storage Specification

## Purpose
Define host-injectable Cookie and CSRF storage, Room-free defaults, common Cookie policy, atomic mutation, expiry cleanup, and explicit legacy Room access for `Fanbox`.

## Requirements

### Requirement: Host-injectable authentication storage
`Fanbox` SHALL accept one `FanboxCookieStorage` and one `FanboxTokenStore` at construction time, SHALL use only those instances for Cookie and CSRF state, and SHALL expose a suspend `snapshot()` for request reads, atomic mutation operations, and Flow-based observation without requiring Room. Public Cookie observation and mutation SHALL use the fankt-owned `FanboxCookieRecord`; each record's required `domain` and explicit `hostOnly` fields SHALL be the sole scope authority, and `Fanbox.setCookies` SHALL NOT accept a competing request URL. The Cookie storage contract SHALL persist normalized records including host-only scope rather than implement request matching; one internal adapter SHALL apply host-only, domain, path, secure, and expiry policy for every backend. The observation Flow SHALL emit the current snapshot at least once to each collector, while request correctness SHALL use `snapshot()` rather than wait for Flow emission.

#### Scenario: Injected storage executes an API request
- **WHEN** a host constructs `Fanbox` with non-Room Cookie and token stores and executes any generated FANBOX API request
- **THEN** the request reads authentication state from those injected stores and no Room database is opened

#### Scenario: Common matching protects every backend
- **WHEN** an injected store contains Cookies for both `.fanbox.cc` and `pixiv.net` and a request targets `https://api.fanbox.cc/`
- **THEN** the common adapter sends only the unexpired matching FANBOX Cookies regardless of the backend implementation

#### Scenario: Storage observation is delegated
- **WHEN** the injected Cookie or token store publishes a new value
- **THEN** `Fanbox.cookies` publishes the current `FanboxCookieRecord` values or `Fanbox.csrfToken` publishes the current token

#### Scenario: Observation starts with current snapshot
- **WHEN** a consumer starts collecting the injected Cookie storage observation Flow
- **THEN** the Flow emits the current snapshot at least once even when no mutation has occurred

#### Scenario: Request read is independent from observation scheduling
- **WHEN** a FANBOX request starts before an observation collector receives its emission
- **THEN** the request obtains a finite current value from `snapshot()` instead of waiting on the Flow

#### Scenario: Host-only Cookie is isolated
- **WHEN** storage contains a host-only Cookie created for `www.fanbox.cc` and a request targets `api.fanbox.cc`
- **THEN** the common adapter does not send that Cookie to the sibling host

#### Scenario: Explicit host-only record uses its declared owner
- **WHEN** `Fanbox.setCookies()` receives a `FanboxCookieRecord(domain = "www.fanbox.cc", hostOnly = true)`
- **THEN** the Cookie is not sent to `api.fanbox.cc`; a caller that requires the conventional cross-subdomain session scope uses `setFanboxSessionId()`

### Requirement: Public cookie mutation preserves expiry semantics
`Fanbox.setCookies` SHALL treat a supplied record whose expiry is at or before the mutation time as a deletion for that cookie identity. Additive mutation SHALL delete that identity instead of storing the expired record, and replacement mutation SHALL atomically omit every expired record.

#### Scenario: Expired additive session record deletes stored session
- **WHEN** additive `setCookies` receives an expired `FANBOXSESSID` record matching a currently stored session
- **THEN** the stored session identity is deleted and the expired record is not published by `Fanbox.cookies`

#### Scenario: Replacement omits expired records
- **WHEN** replacement `setCookies` receives both live and expired records
- **THEN** the atomic replacement contains only the live records

### Requirement: Room-free defaults
The public `Fanbox` constructor SHALL default to newly allocated in-memory Cookie and token stores, SHALL preserve source-compatible construction for callers that pass no storage arguments, and SHALL NOT persist default authentication state across process recreation. Documentation SHALL identify explicit storage injection as required for persistence.

#### Scenario: Default client runs without Room
- **WHEN** a caller constructs `Fanbox()` and executes authenticated mock-engine requests after setting a session and token
- **THEN** all requests complete using in-memory state without constructing or accessing Room

#### Scenario: Default instances are isolated
- **WHEN** two default `Fanbox` instances set different session Cookies and CSRF tokens
- **THEN** each instance observes and sends only its own authentication state

#### Scenario: Source-compatible construction changes durability
- **WHEN** an existing caller upgrades and continues to call `Fanbox()` without storage arguments
- **THEN** construction and in-process API behavior remain available but Cookie state is not restored after process recreation

### Requirement: Linearizable storage mutation
The in-memory implementations and the `Fanbox` authentication adapter SHALL serialize mutations so a completed Cookie replacement, clear, or token update is visible to every later request using the same store. `replaceAll()` SHALL atomically replace the complete Cookie snapshot, and failure SHALL retain the complete previous snapshot. `Fanbox.setCookies(reset = true)` SHALL perform one `replaceAll()` rather than clear followed by individual writes. A session replacement SHALL clear the associated token store according to the existing reset semantics.

#### Scenario: Session replacement precedes request
- **WHEN** `setFanboxSessionId()` completes and a request starts afterward
- **THEN** the request uses the replacement session and observes a cleared CSRF token

#### Scenario: Atomic Cookie replacement fails
- **WHEN** `replaceAll()` cannot commit a replacement Cookie set
- **THEN** later reads observe the complete pre-replacement Cookie snapshot rather than an empty or partially replaced set

### Requirement: Non-blocking expiry cleanup
The authentication adapter SHALL exclude expired Cookies from every request before attempting backend cleanup. `FanboxCookieStorage.deleteExpired(nowEpochMilliseconds)` SHALL remove only records whose current expiry is at or before the supplied instant in one conditional mutation, so a same-identity Cookie refreshed after the adapter snapshot survives cleanup. Cleanup cancellation SHALL be rethrown. Any other cleanup failure SHALL NOT deliver an expired Cookie and SHALL NOT fail the request; a later read SHALL retry cleanup.

#### Scenario: Expiry cleanup write fails
- **WHEN** a request snapshot contains an expired Cookie and backend deletion fails
- **THEN** the request proceeds without the expired Cookie and a later snapshot read may retry deletion

#### Scenario: Cookie is refreshed after the request snapshot
- **WHEN** a request snapshot contains an expired Cookie and the same identity is refreshed before conditional cleanup commits
- **THEN** the request still excludes the expired snapshot value and cleanup retains the refreshed current record

### Requirement: Explicit legacy Room access
The optional `fanbox-persistence-room` artifact SHALL provide an explicit Room-backed `FanboxCookieStorage` factory that reads Android `getDatabasePath("fankt.db")` or iOS `NSDocumentDirectory/fankt.db` and implements the same storage contract. The backend SHALL keep schema v3 unchanged, SHALL expose every legacy row with `hostOnly = false`, and SHALL coerce writes to `hostOnly = false` because the schema cannot preserve that distinction. Each storage SHALL own its database instance and SHALL permit a later factory call to create a fresh instance after close. It SHALL NOT expose database-file deletion because one instance cannot prove that other owners for the same path are closed. The `fanbox` artifact and default `Fanbox` construction SHALL NOT contain or select this backend implicitly.

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
