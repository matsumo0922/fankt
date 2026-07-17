## Why

CSRF tokens are short-lived session credentials, but `Fanbox` currently appends every update to Room forever. Issue #21 already made requests resolve the latest token at send time, so Issue #24 can replace the persistence source without rebuilding the stable HTTP client graph.

## What Changes

- Keep the process session's current CSRF token in memory, share it across `Fanbox` instances like the process-local cookie database, and continue exposing it as `Flow<String?>`.
- Make `updateCsrfToken()` publish the fetched value before returning so the next request observes it through the Issue #21 request-time provider.
- Remove the internal `CSRFToken` Room entity, `TokenDao`, and token-table access.
- Migrate the Room schema from v2 to v3 by dropping only `fankt_csrf_tokens`, preserving all cookie rows and the cookie schema.
- Clear the in-memory token after a successful session-ID replacement, cookie reset, or `FANBOXSESSID` cookie replacement so credentials do not cross session boundaries. **(agent provisional)**
- Update the current README and OpenSpec wording from persisted-row semantics to per-`Fanbox` in-memory semantics.

## Capabilities

### New Capabilities
- `in-memory-csrf-token`: Process-session CSRF token lifetime, public Flow behavior, session-boundary clearing, and token-table removal migration.

### Modified Capabilities
- `request-time-csrf`: Requests resolve the current in-memory value rather than the latest persisted Room row; row ordering and timestamp semantics are removed.
- `persistent-cookie-safety`: A v2-to-v3 schema upgrade drops only the CSRF table and preserves existing cookie data, including the full v1-to-v3 migration chain.
- `room-database-lifecycle`: `Fanbox` dependency graphs obtain only the cookie DAO from the process-local Room database after token persistence is removed.

## Impact

- Internal Kotlin types and wiring: `FanboxDependencies`, `ClientBuilder`, `Fanbox`, `CSRFToken`, and `TokenDao`.
- Room schema version, Android/iOS migration registration, exported schema JSON, and migration tests.
- Existing CSRF production-path tests, README lifecycle guidance, and three current OpenSpec capabilities.
- The public `Fanbox.csrfToken: Flow<String?>`, `updateCsrfToken()`, and request APIs remain source-compatible. PixiView-KMP continues to update at startup/login and observe the same public Flow.
