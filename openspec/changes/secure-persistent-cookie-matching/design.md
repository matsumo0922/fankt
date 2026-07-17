## Context

`HttpCookies` delegates request scoping to `CookiesStorage`. The current DB-backed implementation ignores `requestUrl`, strips the Cookie `secure` attribute, does not enforce `expiresAt`, and identifies a host-defaulted Cookie using an ID built from the pre-default nullable domain. Because the same storage is installed in the generated FANBOX clients and the public raw `Fanbox.getHttpClient()`, this is a security boundary and a production hot path.

Ktor 3.2.3 already exposes `Cookie.matches(Url)`, which implements case-insensitive domain suffix, path-prefix, IP-host, and secure-scheme checks. Expiry is storage lifecycle state and remains the storage's responsibility.

## Goals / Non-Goals

**Goals:**

- Enforce domain, path, secure, and expiry before `HttpCookies` renders a request header.
- Remove expired rows as part of reads.
- Preserve `maxAge` across process restarts as an absolute timestamp.
- Give each logical Cookie a stable, non-null DB identity.
- Upgrade existing databases without dropping Cookie values or CSRF tokens.
- Exercise the real `HttpCookies -> PersistentCookieStorage` production call path in tests.

**Non-Goals:**

- Add a save-time allowlist for FANBOX/Pixiv domains; that is a consumer-side defense tracked separately.
- Change public `Fanbox` method signatures.
- Reimplement Ktor's matching algorithm.
- Persist `httpOnly`, extensions, or Cookie encoding, which do not participate in the Issue #20 send eligibility contract.

## Decisions

### 1. Delegate URL matching to Ktor

（ユーザー確認済み）After removing expired rows, convert each entity to `Cookie` and call Ktor's public `Cookie.matches(requestUrl)`. This keeps behavior aligned with the pinned Ktor version and covers domain suffix, path prefix, IP host, and secure scheme. A copied matcher was rejected because it would drift from the actual `HttpCookies` contract.

### 2. Convert max-age to absolute expiry at write time

（agent 仮決め）Inject a millisecond clock into `PersistentCookieStorage`. `maxAge` takes precedence over `expires`, matching Ktor `AcceptAllCookiesStorage`; positive values become `now + maxAge * 1000`, and non-positive values are immediately expired. Absolute storage makes expiry deterministic after restart and directly testable.

### 3. Use a composite key instead of a synthetic ID

（agent 仮決め）Remove `CookieEntity.id` and use `primaryKeys = [domain, path, name]`. Apply Ktor `fillDefaults(requestUrl)` before persistence, so host-only and missing-path Cookies have effective non-null identity fields. A revised delimited string ID was rejected because encoding rules and v1 nullable-domain IDs would remain migration hazards.

### 4. Migrate v1 rows with fail-safe secure semantics

（agent 仮決め）Use a Room v1→v2 migration that rebuilds only `fankt_cookies`, retains the newest SQLite row for duplicate `domain/path/name` tuples, and sets `secure = 1` for migrated rows. v1 discarded the original `secure` bit, so it cannot be reconstructed. Marking rows insecure could continue leaking a formerly secure session over HTTP; marking them secure preserves all existing HTTPS FANBOX flows and fails closed for HTTP. `fankt_csrf_token` remains untouched.

Rollback to a v1 library may use the existing downgrade-destructive policy and require reauthentication. Forward migration is additive from the user's perspective and retains Cookie values.

### 5. Delete expiry rows before selection

（agent 仮決め）Read the finite Cookie inventory once, partition expired rows using the injected clock, delete each expired composite key on the injected dispatcher, and match only the remaining rows. This avoids exposing expired entries even if cleanup fails to race ahead of header rendering; Room serializes DB operations, while a concurrent replacement under the same key remains a last-writer-wins Cookie operation.

## Risks / Trade-offs

- [A v1 non-secure HTTP Cookie becomes secure after migration] → This is a deliberate fail-safe restriction; the library's built-in FANBOX endpoints are HTTPS, and preventing secret disclosure takes priority over retaining HTTP Cookie delivery.
- [Ktor matching semantics change on dependency upgrade] → Tests describe externally required domain/path/secure behavior and will detect drift.
- [Clock arithmetic overflows for extreme `maxAge`] → Use saturating conversion to `Long.MAX_VALUE` rather than wrapping into an expired timestamp.
- [Read-triggered cleanup adds DB writes to the request path] → Writes occur only for expired rows; normal reads remain one query plus in-memory matching.
- [Concurrent replacement during cleanup] → Delete by the full composite key. Room's serialized operations produce deterministic last-operation behavior, and tests cover replacement identity; no multi-process guarantee is added.

## Migration Plan

1. Increase `FanktDatabase` from version 1 to 2.
2. Rebuild `fankt_cookies` with `domain/path/name` composite primary key and a non-null `secure` column.
3. Copy v1 rows in row order using `INSERT OR REPLACE`, so the newest duplicate tuple is retained, with `secure = 1`.
4. Drop the v1 table and rename the rebuilt table.
5. Register the same common migration in Android and iOS builders.
6. Verify generated schema v2 and a v1→v2 migration test or equivalent SQL-level assertion.

## Open Questions

なし。人間には agent 仮決め（composite key、maxAge の絶対化、v1 rows の secure 化）を PR で確認してもらう。
