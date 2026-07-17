## Context

`Fanbox` currently constructs three internal `HttpClient` instances and five Ktorfit APIs in `buildKtorfit(csrfToken)`. A collector rebuilds that entire graph whenever the Room `Flow` of CSRF tokens emits. `updateCsrfToken()` returns after the DAO insert, before the collector is guaranteed to publish the rebuilt repositories, so the next POST can use the previous token. The old clients are not closed, and mutable `lateinit` repository references are reassigned from a background coroutine.

The persisted Room token remains the source of truth in this change. Moving the token to an in-memory source is a separate follow-up named by issue #21.

## Goals / Non-Goals

**Goals:**

- Make the first request started after `updateCsrfToken()` completes resolve the newly persisted token.
- Build the internal client/API/repository graph once and keep its identities stable across token updates.
- Cover request headers with Ktor `MockEngine` and verify the same client instance serves requests before and after a token update.
- Preserve public API signatures and persisted token behavior.

**Non-Goals:**

- Moving CSRF tokens from Room into an in-memory store.
- Changing token acquisition, expiry policy, database schema, or public API signatures.
- Changing the lifecycle contract of separate custom clients returned by `getHttpClient()`; each call still creates a caller-owned client.

## Decisions

1. **（agent 仮決め）Install a request-time CSRF client plugin backed by a suspend token provider.** The plugin resolves `tokenDao.getLatestToken().first()` as a request enters the client pipeline, removes any existing `x-csrf-token` value, and sets the latest persisted value. This keeps the Room source of truth and creates a happens-after boundary: DAO `insert` completes before `updateCsrfToken()` returns, and the next provider query observes the committed row. A request already in flight is not retroactively changed. A custom plugin is preferred over a token parameter in `defaultRequest`, because `defaultRequest` captures construction-time state. `HttpSend` was considered, but no retry-specific token refresh behavior is required by the issue.

2. **（agent 仮決め）Construct the internal graph eagerly as immutable properties.** The three Ktorfit clients, generated APIs, mappers, and repositories are constructed in property initialization. The token collector, `CoroutineScope`, and `buildKtorfit` reassignment function are removed. This eliminates rebuild races and abandoned internal clients. The separate raw client returned by `getHttpClient()` also uses the request-time provider, while retaining its documented per-call construction behavior.

3. **（agent 仮決め）Test the production request configuration through a MockEngine.** The request-default/plugin installation is exposed as an internal `HttpClientConfig` helper used by the production builder and tests. The test performs a token store update through the production-shared `fetchAndStoreCsrfToken`, sends a POST through one MockEngine client, and asserts the new header and client identity. A second request after another store update proves that the same client resolves a later value rather than a captured value.

4. **（ユーザー確認済み）Keep the change within issue #21.** The issue explicitly separates in-memory token storage as a later change, so this implementation accepts one Room query per request and does not introduce a cache or synchronization subsystem.

## Risks / Trade-offs

- **A Room read occurs for every request** → This is the deliberate boundary of issue #21; the follow-up in-memory change can remove the I/O without changing the client plugin contract.
- **A request already past the plugin may retain the old token** → The guarantee begins with requests started after `updateCsrfToken()` completes, matching the acceptance criterion; in-flight mutation is not attempted.
- **An explicit caller-supplied CSRF header is replaced** → Persisted token state remains authoritative, matching existing library-owned behavior and preventing duplicate conflicting headers.
- **A missing token produces an empty header** → Preserve current wire behavior until the separate token-policy issue changes it.

## Migration Plan

No schema or consumer migration is required. Replace graph construction and request configuration atomically in one release. Rollback is a source rollback; persisted token rows are unchanged.

## Open Questions

None.
