## MODIFIED Requirements

### Requirement: Host-injectable authentication storage
`Fanbox` SHALL accept one `FanboxCookieStorage` and one `FanboxTokenStore` at construction time, SHALL use only those instances for Cookie and CSRF state, and SHALL expose a suspend `snapshot()` for request reads, atomic mutation operations, and Flow-based observation without requiring Room. Public Cookie observation and mutation SHALL use the fankt-owned `FanboxCookieRecord`. The Cookie storage contract SHALL persist normalized records including host-only scope rather than implement request matching; one internal adapter SHALL apply host-only, domain, path, secure, and expiry policy for every backend. The observation Flow SHALL emit the current snapshot at least once to each collector, while request correctness SHALL use `snapshot()` rather than wait for Flow emission.

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

#### Scenario: Domain omission uses strict origin ownership
- **WHEN** `Fanbox.setCookies()` receives a `FanboxCookieRecord` without `domain` for `https://www.fanbox.cc`
- **THEN** the Cookie is host-only for `www.fanbox.cc` and is not sent to `api.fanbox.cc`; a caller that requires cross-subdomain delivery supplies `domain = ".fanbox.cc"` or uses `setFanboxSessionId()` for the session Cookie

