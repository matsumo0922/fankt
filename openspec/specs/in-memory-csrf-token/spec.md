# in-memory-csrf-token Specification

## Purpose
Keep each host-owned CSRF token in memory, observable through the public Flow, isolated by the injected store, and separate from Cookie persistence.

## Requirements
### Requirement: CSRF token state is memory-only and observable
Each `Fanbox` SHALL retain at most one current CSRF token in its injected `FanboxTokenStore`, SHALL initialize a newly allocated default store to null, SHALL share a value only between `Fanbox` dependency graphs that are explicitly given the same store instance, and SHALL expose it through `Fanbox.csrfToken: Flow<String?>`. The default token store SHALL NOT persist the value across process recreation. This requirement traces to Issue #24 and is revised by Issue #33 for host ownership and multi-account isolation.

#### Scenario: Fresh default store has no token
- **WHEN** a `Fanbox` instance is created with a new default token store before a token refresh
- **THEN** its `csrfToken` Flow exposes null and no persisted token is restored

#### Scenario: Explicitly shared store shares current token
- **WHEN** one `Fanbox` updates a token and another `Fanbox` was constructed with the identical token store instance
- **THEN** both `csrfToken` Flows expose the same current token

#### Scenario: Independent stores isolate current token
- **WHEN** two `Fanbox` instances use different token store instances and one updates its token
- **THEN** the other instance's token value does not change

#### Scenario: Token update is observable
- **WHEN** `updateCsrfToken()` fetches a token and completes
- **THEN** the same `Fanbox` instance's `csrfToken` Flow exposes the fetched String

#### Scenario: Token update replaces instead of appends
- **WHEN** `updateCsrfToken()` completes more than once against the same store
- **THEN** only the most recently completed in-memory value is retained

### Requirement: Session replacement clears the in-memory token
`Fanbox` SHALL clear its injected token store after a session-ID replacement succeeds, SHALL clear it before an atomic `replaceAll()` for `setCookies(reset = true)`, and SHALL clear it before an additive Cookie update that contains `FANBOXSESSID`. A failed atomic replacement MAY retain the previous Cookie snapshot but SHALL leave the token cleared. Additive updates containing no session Cookie SHALL preserve the current token.

#### Scenario: Session ID is replaced
- **WHEN** `setFanboxSessionId()` successfully replaces the session Cookie
- **THEN** the injected token store exposes null until a later token refresh completes

#### Scenario: Cookies are reset
- **WHEN** `setCookies(reset = true)` is called
- **THEN** the token is null before `replaceAll()` starts and remains null whether the replacement commit succeeds or fails

#### Scenario: Session Cookie is replaced without reset
- **WHEN** `setCookies(reset = false)` is given a `FANBOXSESSID` Cookie
- **THEN** the token is null before any supplied-cookie operation can fail

#### Scenario: Unrelated Cookies are added without reset
- **WHEN** `setCookies(reset = false)` adds Cookies that do not include `FANBOXSESSID`
- **THEN** the current token remains unchanged

### Requirement: Token-table removal preserves cookies
The Room v3 schema SHALL contain no CSRF token entity or DAO. Upgrading from v2 to v3 SHALL drop only `fankt_csrf_tokens`, and the supported v1-to-v3 migration chain SHALL preserve the canonical cookie rows produced by v1-to-v2. This requirement traces to Issue #24 acceptance criterion "既存ユーザーの DB を開いても cookie が消えないこと".

#### Scenario: Direct v2 upgrade
- **WHEN** a v2 database containing cookies and CSRF token rows is upgraded to v3
- **THEN** every cookie row remains unchanged and the `fankt_csrf_tokens` table no longer exists

#### Scenario: Chained v1 upgrade
- **WHEN** a v1 database is upgraded through v2 to v3
- **THEN** its canonicalized cookie rows and cookie index remain present while the CSRF token table is removed
