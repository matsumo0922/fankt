## ADDED Requirements

### Requirement: CSRF token state is memory-only and observable
The process SHALL initialize its CSRF token to null, SHALL retain at most one current token in memory, SHALL share that value across `Fanbox` dependency graphs that use the process-local cookie database, and SHALL expose it through the existing `Fanbox.csrfToken: Flow<String?>` API. This requirement traces to Issue #24 tasks "CSRF トークンを in-memory 管理" and "公開 API は維持する".

#### Scenario: Fresh process has no token
- **WHEN** the first `Fanbox` instance is created in a fresh process before a token refresh
- **THEN** its `csrfToken` Flow exposes null and no persisted token is restored

#### Scenario: Sibling Fanbox observes current token
- **WHEN** one `Fanbox` completes a token update and another `Fanbox` exists in the same process
- **THEN** both `csrfToken` Flows expose the same current token

#### Scenario: Token update is observable
- **WHEN** `updateCsrfToken()` fetches a token and completes
- **THEN** the same `Fanbox` instance's `csrfToken` Flow exposes the fetched String

#### Scenario: Token update replaces instead of appends
- **WHEN** `updateCsrfToken()` completes more than once on the same `Fanbox`
- **THEN** only the most recently completed in-memory value is retained

### Requirement: Session replacement clears the in-memory token
`Fanbox` SHALL clear the process CSRF token after a session-ID replacement succeeds, SHALL clear it after reset-cookie clearing succeeds before adding replacement cookies, and SHALL clear it before an additive cookie update that contains `FANBOXSESSID`. Additive updates containing no session cookie SHALL preserve the current token. This requirement is an agent-provisional security invariant for Issue #24's session-scoped credential lifetime.

#### Scenario: Session ID is replaced
- **WHEN** `setFanboxSessionId()` successfully replaces the session cookie
- **THEN** `csrfToken` exposes null until a later token refresh completes

#### Scenario: Cookies are reset
- **WHEN** `setCookies(reset = true)` successfully clears stored cookies
- **THEN** the token is null before any replacement-cookie operation can fail

#### Scenario: Session cookie is replaced without reset
- **WHEN** `setCookies(reset = false)` is given a `FANBOXSESSID` cookie
- **THEN** the token is null before any supplied-cookie operation can fail

#### Scenario: Unrelated cookies are added without reset
- **WHEN** `setCookies(reset = false)` adds cookies that do not include `FANBOXSESSID`
- **THEN** the current token remains unchanged

### Requirement: Token-table removal preserves cookies
The Room v3 schema SHALL contain no CSRF token entity or DAO. Upgrading from v2 to v3 SHALL drop only `fankt_csrf_tokens`, and the supported v1-to-v3 migration chain SHALL preserve the canonical cookie rows produced by v1-to-v2. This requirement traces to Issue #24 acceptance criterion "既存ユーザーの DB を開いても cookie が消えないこと".

#### Scenario: Direct v2 upgrade
- **WHEN** a v2 database containing cookies and CSRF token rows is upgraded to v3
- **THEN** every cookie row remains unchanged and the `fankt_csrf_tokens` table no longer exists

#### Scenario: Chained v1 upgrade
- **WHEN** a v1 database is upgraded through v2 to v3
- **THEN** its canonicalized cookie rows and cookie index remain present while the CSRF token table is removed
