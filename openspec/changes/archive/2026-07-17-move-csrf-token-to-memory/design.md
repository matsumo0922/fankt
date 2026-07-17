## Context

Issue #21 installs a request-time Ktor plugin whose provider currently reads the latest `CSRFToken` Room row. `updateCsrfToken()` fetches metadata, inserts a new row, and exposes the DAO Flow as `Fanbox.csrfToken`. Room schema v2 therefore retains an unbounded append-only credential table even though PixiView refreshes the token at application startup and login.

Issue #23 makes the Room database process-local and Issue #22 makes HTTP clients `Fanbox`-owned. This change aligns credential state with those lifetimes: cookies remain process-persistent in the shared database, while one process-local in-memory token follows the shared cookie session and is read by every `Fanbox` client graph.

## Goals / Non-Goals

**Goals:**
- Preserve `Fanbox.csrfToken: Flow<String?>` and the Issue #21 guarantee that a completed update is visible to the next request.
- Bound CSRF token storage to one nullable value per process session.
- Remove the token entity, DAO, and table without deleting or rewriting cookie rows.
- Keep Android and iOS database upgrade paths valid from every exported schema version.

**Non-Goals:**
- Persist a CSRF token across `Fanbox` or process recreation; callers refresh it before protected requests.
- Change persistent cookie ownership, `Fanbox.close()` Flow behavior, or the process-local Room singleton.
- Introduce the Phase 3 `TokenStore` abstraction from Issue #33.
- Serialize arbitrary API requests with concurrent session/cookie mutation; callers remain responsible for credential-update ordering.

## Decisions

1. **Store one nullable String in a process-local state Flow. (user-confirmed)** A common top-level `MutableStateFlow<String?>(null)` is shared by every production `FanboxDependencies`, exposed as a read-only Flow, read through `.value` by the request-time provider, and updated when metadata refresh completes. This matches the process-local cookie database and preserves the existing cross-`Fanbox` token visibility without persisting the credential across process recreation.

2. **Publish before `updateCsrfToken()` returns. (user-confirmed)** The metadata helper stores the raw String synchronously through a suspend callback; assigning `MutableStateFlow.value` completes before the enclosing call returns. Overlapping updates use completion order: the last completed store becomes current. Explicit raw-client headers still override the default plugin header.

3. **Clear at every detectable session replacement boundary. (agent provisional)** After `setFanboxSessionId()` successfully replaces the persisted session cookie, the token becomes null. For `setCookies(reset = true)`, the token is cleared immediately after cookie clearing succeeds and before replacement cookies are added, so a partial add failure cannot retain a token from the removed session. For `setCookies(reset = false)`, the token is cleared before writes when the supplied list contains `FANBOXSESSID`; unrelated additive cookies preserve it.

4. **Do not tie process credential state to one HTTP-client owner. (agent provisional)** Accessing `fanbox.csrfToken` after `Fanbox.close()` remains rejected by Issue #22, while a Flow reference acquired before close does not complete automatically. Closing one `Fanbox` does not clear the process token because sibling instances share the same persisted cookie session; process recreation or an explicit session boundary clears it.

5. **Remove persistence types instead of adapting them. (agent provisional)** `ClientBuilder` accepts `suspend () -> String?`; `fetchAndStoreCsrfToken` stores a String; `CSRFToken` and `TokenDao` are deleted. These types are internal, so the public Kotlin API remains unchanged and timestamp/row-order semantics disappear from the current spec.

6. **Use an explicit v2-to-v3 destructive-to-token-only migration. (user-confirmed)** Database v3 contains only `CookieEntity`. `COOKIE_MIGRATION_2_3` runs `DROP TABLE IF EXISTS fankt_csrf_tokens`; both platform builders register migrations 1→2 and 2→3. Tests exercise direct v2→v3 and chained v1→2→3 statement paths and assert cookie values/schema survive. No destructive fallback is enabled.

7. **Keep migration verification proportional to the existing KMP harness. (agent provisional)** The existing Android JVM SQLite test executes the same statement lists used by the Room `Migration` objects, verifies cookie rows and indexes, and verifies the token table is absent. Schema export/build validation proves the annotated v3 shape; reviewer inspection proves both actual builders register 2→3. Adding a new Room instrumentation harness is outside this bounded removal.

## Risks / Trade-offs

- **[A fresh process starts without a token]** → README and KDoc state that callers refresh after startup or changing a session; PixiView already does so at startup/login.
- **[Concurrent refreshes complete out of request order]** → completion order is the observable state order, matching the completion guarantee required by Issue #21 without timestamp heuristics.
- **[Session mutation can race an API request or in-flight refresh]** → token clearing is fail-safe for sequentially subsequent requests; arbitrary request/session mutation serialization remains an explicit non-goal and `updateCsrfToken()` KDoc documents the ordering requirement.
- **[Downgrading a v3 database to a v2 library cannot restore removed token rows]** → tokens are intentionally ephemeral; the existing no-destructive-downgrade policy continues to reject unsupported downgrades instead of deleting cookies.
- **[Existing Flow collector survives owner close]** → this preserves Issue #22 behavior and avoids expanding into database/Flow lifecycle redesign.

## Migration Plan

1. Ship schema v3 with both 1→2 and 2→3 migrations registered on Android and iOS.
2. On first database open, Room drops only `fankt_csrf_tokens`; cookie rows remain in place.
3. The first `Fanbox` in a fresh process observes null; after the existing `updateCsrfToken()` call path completes, sibling instances observe the same current process-session token.
4. Rollback to a v2 binary is unsupported by the existing downgrade policy; reinstalling a newer binary reuses the preserved cookie table.

## Open Questions

- Human review should confirm the agent-provisional choice to share one process-session token and clear it on every detectable `FANBOXSESSID` replacement, rather than retaining per-instance credentials.
