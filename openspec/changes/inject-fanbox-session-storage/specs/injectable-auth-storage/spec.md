## ADDED Requirements

### Requirement: Host-injectable authentication storage
`Fanbox` SHALL accept one `FanboxCookieStorage` and one `FanboxTokenStore` at construction time, SHALL use only those instances for Cookie and CSRF state, and SHALL expose a suspend `snapshot()` for request reads, atomic mutation operations, and Flow-based observation without requiring Room. The Cookie storage contract SHALL persist normalized records including host-only scope rather than implement request matching; one internal adapter SHALL apply host-only, domain, path, secure, and expiry policy for every backend. The observation Flow SHALL emit the current snapshot at least once to each collector, while request correctness SHALL use `snapshot()` rather than wait for Flow emission.

#### Scenario: Injected storage executes an API request
- **WHEN** a host constructs `Fanbox` with non-Room Cookie and token stores and executes any generated or raw FANBOX API request
- **THEN** the request reads authentication state from those injected stores and no Room database is opened

#### Scenario: Common matching protects every backend
- **WHEN** an injected store contains Cookies for both `.fanbox.cc` and `pixiv.net` and a request targets `https://api.fanbox.cc/`
- **THEN** the common adapter sends only the unexpired matching FANBOX Cookies regardless of the backend implementation

#### Scenario: Storage observation is delegated
- **WHEN** the injected Cookie or token store publishes a new value
- **THEN** `Fanbox.cookies` or `Fanbox.csrfToken` publishes the corresponding current value

#### Scenario: Observation starts with current snapshot
- **WHEN** a consumer starts collecting the injected Cookie storage observation Flow
- **THEN** the Flow emits the current snapshot at least once even when no mutation has occurred

#### Scenario: Request read is independent from observation scheduling
- **WHEN** a FANBOX request starts before an observation collector receives its emission
- **THEN** the request obtains a finite current value from `snapshot()` instead of waiting on the Flow

#### Scenario: Host-only Cookie is isolated
- **WHEN** storage contains a host-only Cookie created for `www.fanbox.cc` and a request targets `api.fanbox.cc`
- **THEN** the common adapter does not send that Cookie to the sibling host

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
The authentication adapter SHALL exclude expired Cookies from every request before attempting backend cleanup. Cleanup failure SHALL NOT deliver an expired Cookie and SHALL NOT fail the request; a later read SHALL retry cleanup.

#### Scenario: Expiry cleanup write fails
- **WHEN** a request snapshot contains an expired Cookie and backend deletion fails
- **THEN** the request proceeds without the expired Cookie and a later snapshot read may retry deletion

### Requirement: Explicit legacy Room access
Until the Room artifact separation is released, fankt SHALL provide an explicit legacy Room-backed `FanboxCookieStorage` factory or equivalent migration bridge that reads Android `getDatabasePath("fankt.db")` or iOS `NSDocumentDirectory/fankt.db` and implements the same storage contract. The bridge SHALL keep schema v3 unchanged, SHALL expose every legacy row with `hostOnly = false`, and SHALL coerce legacy-backend writes to `hostOnly = false` because the schema cannot preserve that distinction. Each bridge SHALL own its database instance, SHALL close it before file deletion, and SHALL permit a later factory call to create a fresh instance after close. Default `Fanbox` construction SHALL NOT select this bridge implicitly.

#### Scenario: Existing database is opened for migration
- **WHEN** a host explicitly requests the legacy Room storage on a device with existing Cookie rows
- **THEN** the bridge exposes those rows through `FanboxCookieStorage.cookies` with their original fields unchanged and `hostOnly = false`

#### Scenario: Legacy write preserves current domain behavior
- **WHEN** the legacy bridge stores a Cookie record supplied with `hostOnly = true`
- **THEN** it reloads that record with `hostOnly = false` without changing the Room schema version

#### Scenario: Legacy FANBOX session still reaches API host
- **WHEN** a v1, v2, or v3 fixture contains the existing domain-scoped `FANBOXSESSID` and is opened through the legacy bridge
- **THEN** the common adapter sends that session to `https://api.fanbox.cc/`

#### Scenario: New default avoids legacy database
- **WHEN** a host constructs `Fanbox()` without requesting the legacy bridge
- **THEN** fankt does not open `fankt.db`

#### Scenario: Closed bridge can be reacquired for cleanup retry
- **WHEN** a legacy bridge closes and a later cleanup retry requests the bridge again in the same process
- **THEN** the factory returns a fresh usable database instance rather than the closed instance
