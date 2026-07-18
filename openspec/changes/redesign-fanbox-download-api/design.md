## Context

`FanboxDownloadApi` currently synthesizes three `downloads.fanbox.cc` paths ending in `.jpg`. The public response models already preserve API-provided image `originalUrl` / `thumbnailUrl` and file `url`, and PixiView-KMP downloads those exact values through `getHttpClient().prepareGet(...)`. The replacement therefore needs no post/item identifiers and must make the destination trust boundary part of the public API.

The shared clients add cookies with Ktor's domain/path matching and resolve the process CSRF token at request time. The existing download client therefore sends the token to `downloads.fanbox.cc`, but external CDN GETs do not require that mutation credential. Validating only the initial URL would be insufficient because Ktor follows redirects; an allowlisted URL could redirect to an arbitrary host. Destination enforcement and external-host header removal must run both when `download` creates the statement and in Ktor's send pipeline for every request attempt issued by its client.

One browser-fingerprint request to the repository sample public post (`post.info`, post `8909488`) returned `200 application/json` and exposed URL hosts `downloads.fanbox.cc` and `pixiv.pximg.net`; the response body, URL values, and credentials were not printed or retained. The current gallery-dl FANBOX extractor consumes `originalUrl` / `url` unchanged and recognizes `downloads.fanbox.cc` plus legacy `fanbox.pixiv.net`. PixiView-KMP uses `FanboxPostDetail.ImageItem.originalUrl` / `thumbnailUrl` and `FileItem.url` directly in both Android and iOS download paths.

## Goals / Non-Goals

**Goals:**

- Replace three extension-specific operations with one URL-based authenticated GET operation.
- Accept HTTPS URLs on `fanbox.cc` and its subdomains plus the observed `pixiv.pximg.net` and legacy `fanbox.pixiv.net` hosts, while rejecting broader lookalike suffixes and redirect escapes.
- Preserve the exact API-provided path, query, deferred execution, progress callback, and current PixiView Android/iOS consumption patterns.
- Keep the download client bounded, shared for the lifetime of a `Fanbox`, and closed by `Fanbox.close()`.
- Verify allowlist enforcement through the public production call path, including redirects.

**Non-Goals:**

- Migrating the separate PixiView-KMP repository in this PR.
- Allowing all `pixiv.net` / `pximg.net` subdomains beyond the evidenced FANBOX media hosts.
- Synthesizing thumbnail paths; consumers use the API-provided `thumbnailUrl`.
- Removing Ktor types from all public APIs; issue #35 owns that broader boundary.
- Changing cookie matching, CSRF lifetime, database state, response mapping, or generic `getHttpClient()` behavior.

## Decisions

1. **（ユーザー確認済み）Replace identifier-based methods with `download(url, onProgress)`.** Remove the Ktorfit download interface and repository rather than retaining deprecated wrappers, because every wrapper would continue to encode an invalid path contract. The new method passes the supplied URL through unchanged.

2. **（agent 仮決め）Repurpose the dedicated generated download client as one direct, shared download client.** The resource graph remains bounded at five clients: two generated API clients, one direct download client, and two raw clients. The download client shares cookie storage, request-time CSRF lookup, error normalization, lifecycle ownership, and client-factory construction. Its send-time destination hook preserves the current CSRF header on `fanbox.cc` and its subdomains, but removes the header before transport to exact external media hosts. This retains current FANBOX-host behavior without offering a mutation token to CDN domains. Using the unrestricted raw client would validate only the initial statement unless its behavior were globally changed; creating a client per call would regress issue #22's bounded lifecycle.

3. **（agent 仮決め）Enforce the allowlist twice with one pure validator.** `download` catches URL parse failures and validates eagerly so malformed, credential-bearing, non-HTTPS, and disallowed initial URLs fail as `IllegalArgumentException` before a statement is returned. The download client installs a `createClientPlugin` hook at Ktor 3.2.3 `SendingRequest`, which intercepts `HttpSendPipeline.State` for both original and redirected requests immediately before transport. Ktor's `DefaultSender` cancels the prior call before executing that pipeline, so a rejected redirect does not leave the first response open. The same validator therefore rejects redirect escapes before their transport and after the prior call is cancelled; an `onRequest` hook is explicitly insufficient because Ktor documents that it runs only for the original request.

4. **（agent 仮決め）Allow only the FANBOX domain boundary and evidenced external media hosts.** Accept `fanbox.cc` and its dot-delimited subdomains, exact `pixiv.pximg.net`, and exact legacy `fanbox.pixiv.net` over HTTPS. Do not accept other `pixiv.net` or `pximg.net` hosts. The validator lowercases the parsed ASCII host, rejects userinfo and a trailing-dot variant, and uses exact/dot-boundary comparisons so `fanbox.cc.evil.example` and `notfanbox.cc` fail. Ports, paths, and query strings are preserved because the issue constrains scheme and host, not those components.

5. **（agent 仮決め）Include exact `pixiv.pximg.net` and omit a thumbnail helper.** A live public response establishes that FANBOX media uses this host, so excluding it would break the acceptance path. Exact-host matching and send-time CSRF removal keep the credential boundary narrower than allowing every `pximg.net` subdomain. API-provided thumbnail URLs already preserve server-selected formats and transformations, so locally inserting `/w/1200/` would recreate the same brittle abstraction this change removes.

6. **（agent 仮決め）Keep `HttpStatement` for this Phase 2 compatibility boundary.** The issue explicitly permits the existing return type, and checked PixiView Android `execute` streaming and iOS `body<ByteArray>()` paths can migrate by replacing only statement construction. Eager destination failures and redirect-destination failures are `IllegalArgumentException`; network/HTTP failures remain `FanboxException`, while execute-after-close retains Ktor's closed-client failure. PixiView wraps the full operation in `suspendRunCatching`, so these execution failures remain inside its existing failure path. KDoc documents this current contract in present tense; the future type-removal plan remains in issue #35 rather than documentation.

7. **（agent 仮決め）Use `PublicRaw` diagnostics for media URLs.** Media paths are dynamic and must not retain response fragments as if they were fixed generated API routes. Failures remain normalized as `FanboxException` with endpoint `custom-request`, matching the current PixiView raw-client behavior without exposing downloaded content in diagnostics.

8. **（agent 仮決め）Prove the production route with an injected MockEngine.** Tests instantiate `Fanbox` through its real resource builder, call public `download`, execute the returned statement, and observe the exact request URL and progress. Separate cases prove parse-error normalization, eager rejection, lookalike-host rejection, exact observed hosts, redirect rejection before the second transport, prior-call cancellation, CSRF retention on `fanbox.cc`, CSRF absence on external media hosts (header absent, not empty), shared client count, and close behavior. `FanboxRouteDriftTest` becomes an inventory assertion that no generated download interface or `.jpg` route remains; the former Ktorfit download exception test moves to the public production path.

## Risks / Trade-offs

- **A legitimate future FANBOX CDN uses another host** → The request fails closed with `IllegalArgumentException`; add a host only with an observed API response and focused tests.
- **A permitted legacy or CDN URL redirects to an unlisted host** → The redirect is rejected rather than silently widening credential exposure. The caller can retain the API-provided direct FANBOX URL or a later change can add an evidenced host.
- **`HttpStatement` remains a public Ktor type** → The method preserves immediate consumer compatibility; issue #35 owns the coordinated public-type migration.
- **Callback progress is unavailable when content length is unknown** → Preserve the existing behavior of reporting `0f` until Ktor provides a length; consumers still set terminal `1f` after body completion.
- **Removing public methods is source-breaking** → This is intentional and traced to issue #32; the only known consumer does not call them and can replace its raw request builder with `download`.

## Migration Plan

Consumers replace `fanbox.getHttpClient().prepareGet { url(url); onDownload { ... } }` with `fanbox.download(url, onProgress)`, keep their existing `HttpStatement.execute` or `body` handling, handle `IllegalArgumentException` from both statement creation and redirect execution as a rejected destination, and continue closing the owning `Fanbox`. PixiView's existing `suspendRunCatching` boundary already handles both phases. Rollback restores the removed interface/repository/methods and the generated download client; no persisted state or schema is involved.

## Open Questions

None. The external host set is grounded in one current public response plus the legacy extractor path and remains fail-closed for unobserved hosts.
