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

（agent 仮決め）Inject a millisecond clock into `PersistentCookieStorage`. `maxAge` takes precedence over `expires`, matching Ktor `AcceptAllCookiesStorage`; positive values become `now + maxAge * 1000`, and non-positive values are immediately expired. Persist session Cookies as `expiresAt = NULL`, replacing the v1 `-1` sentinel. Absolute or null storage makes expiry deterministic after restart and gives cleanup and selection one unambiguous predicate.

### 3. Use a composite key instead of a synthetic ID

（agent 仮決め）Remove `CookieEntity.id` and use `primaryKeys = [domain, path, name]`. Apply Ktor `fillDefaults(requestUrl)` before persistence, then canonicalize the effective domain to lowercase without leading dots. This makes `.fanbox.cc`, `fanbox.cc`, and case variants one identity, matching the equivalence used by `Cookie.matches`. Host-only and missing-path Cookies also receive effective non-null identity fields. A revised delimited string ID was rejected because encoding rules and v1 nullable-domain IDs would remain migration hazards.

### 4. Migrate v1 rows with fail-safe secure semantics

（ユーザー確認済み）Use a Room v1→v2 migration that rebuilds only `fankt_cookies`, canonicalizes stored domains, retains the row with the greatest SQLite `rowid` for each duplicate canonical `domain/path/name` tuple, and sets `secure = 1` for migrated rows. v1 has no creation timestamp, so "newest Cookie" cannot be proven; greatest `rowid` is only a deterministic tie-breaker. v1 also discarded the original `secure` bit, so it cannot be reconstructed. The user chose HTTPS-only retention to avoid logging out existing users while failing closed for HTTP. All built-in FANBOX endpoints remain usable because they are HTTPS; a consumer that intentionally used an existing Cookie over HTTP must store that Cookie again after upgrading. `fankt_csrf_tokens` remains untouched.

Rollback to a v1 library may use the existing downgrade-destructive policy and require reauthentication. Forward migration is additive from the user's perspective and retains Cookie values.

### 5. Delete expiry rows before selection

（agent 仮決め）Read the finite inventory first, exclude `expiresAt != NULL && expiresAt <= now` from the returned snapshot, then issue one conditional DAO cleanup using exactly the same predicate. A key-only delete was rejected because a concurrent fresh replacement under the same key could be deleted; the expiry predicate preserves that replacement. A concurrent expired insert after the snapshot was not part of this read and is removed by the next read. Only the exact v1 session sentinel `expiresAt = -1` migrates to `NULL`; `0` and other negative real timestamps remain expired timestamps. Newly added Cookies whose real expiry is at or before `now` delete the matching identity immediately instead of being stored.

Expired-row deletion is best-effort after a successful snapshot query. If cleanup fails (for example `SQLITE_BUSY` or `SQLITE_FULL`), storage still returns the in-memory filtered unexpired Cookies, so cleanup failure cannot leak expired data or stop all FANBOX traffic. The next read retries cleanup. Snapshot query failure still propagates because storage has no safe Cookie inventory to return.

### 6. Wire and test the production configuration

（agent 仮決め）Construct `PersistentCookieStorage(cookieDao, ioDispatcher)` in `Fanbox`, rather than silently using its default dispatcher. Permit the internal `buildHttpClient` factory to receive a test engine so a `MockEngine` test invokes the same production factory, `HttpCookies` installation, storage `get`, and rendered request headers. A manually assembled test client was rejected because it could pass while production wiring regressed.

`withContext(ioDispatcher)` guarantees that storage invokes the DAO from the supplied dispatcher. Room's generated suspend DAO may then dispatch the database operation to the builder's configured query context (`Dispatchers.IO`); changing Room's database-wide execution policy is outside Issue #20 and is not claimed by the spec.

## Risks / Trade-offs

- [A v1 non-secure HTTP Cookie becomes secure after migration] → This is a deliberate fail-safe restriction; the library's built-in FANBOX endpoints are HTTPS, and preventing secret disclosure takes priority over retaining HTTP Cookie delivery.
- [Ktor matching semantics change on dependency upgrade] → Tests describe externally required domain/path/secure behavior and will detect drift.
- [Clock arithmetic overflows for extreme `maxAge`] → Use saturating conversion to `Long.MAX_VALUE` rather than wrapping into an expired timestamp.
- [Read-triggered cleanup examines expiry on the request path] → Add an `expiresAt` index; the conditional delete writes only when expired rows exist, followed by in-memory matching over the finite Cookie inventory.
- [Concurrent Cookie replacement after the snapshot] → Cleanup deletes only rows whose current DB expiry is still at or before `now`; a fresh replacement survives, while an expired row not present in the snapshot is handled by the next read.

## Migration Plan

1. Increase `FanktDatabase` from version 1 to 2.
2. Rebuild `fankt_cookies` with `domain/path/name` composite primary key, a non-null `secure` column, nullable and indexed `expiresAt`.
3. Canonicalize domains and copy the greatest-`rowid` v1 row for each canonical identity, with `secure = 1`; convert only the exact v1 `expiresAt = -1` session sentinel to `NULL`. Preserve every other timestamp for first-read expiry cleanup. The row tie-breaker is deterministic but does not claim a creation-time ordering that v1 cannot prove.
4. Drop the v1 table and rename the rebuilt table.
5. Register the same common migration in Android and iOS builders.
6. Verify generated schema v2, expiry index, Cookie rows, and untouched CSRF rows against an actual v1 SQLite database.

## Open Questions

なし。v1 rows の HTTPS-only retention はユーザー確認済み。人間には残る agent 仮決め（composite key、maxAge の絶対化、best-effort cleanup）を PR で確認してもらう。
