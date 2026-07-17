## Why

`Fanbox` currently embeds the latest CSRF token while constructing its clients, so token updates race with an asynchronous rebuild and repeatedly replace every API and repository instance. A completed `updateCsrfToken()` must instead establish the token observed by the next request without leaking clients or publishing repository references across threads.

## What Changes

- Resolve the latest persisted CSRF token when each FANBOX request is sent and set `x-csrf-token` on that request.
- Preserve the current default-header policy: every request receives the persisted token unless that request explicitly supplies its own CSRF header.
- Construct the internal HttpClient, Ktorfit API, mapper, and repository graph once during `Fanbox` initialization.
- Remove the token collector and token-triggered client graph rebuild.
- Add MockEngine coverage for immediate token visibility and stable client identity.
- Document the postcondition of `updateCsrfToken()`.

## Capabilities

### New Capabilities

- `request-time-csrf`: Request-time CSRF token resolution and stable FANBOX client lifecycle behavior.

### Modified Capabilities

None.

## Impact

- `fankt/fanbox` client construction, token DAO access, and common tests.
- README guidance for CSRF token refresh semantics.
- The latest-token query changes to use the auto-generated insertion id; no public API signature or database schema migration changes.
- Issue acceptance criteria: #21.
