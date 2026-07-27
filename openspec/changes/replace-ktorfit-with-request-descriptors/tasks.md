## 1. PR 1 — Portable request and response core

- [x] 1.1 Add serializable internal `FanboxEndpointId`, `FanboxHttpMethod`, `FanboxQueryParameter`, and `RequestDescriptor` value types and verify that the public ABI remains unchanged
- [x] 1.2 Define stable endpoint ID constants and separate complete inventories for 29 request operations, 28 unique request routes, and 29 response decode operations, including one shared `plan.listSupporting` descriptor with explicit strict/tolerant parsers
- [x] 1.3 Implement and test pure `FanboxEndpoints` builders for post GET operations and pagination query omission/encoding
- [x] 1.4 Implement and test pure `FanboxEndpoints` builders for like/comment POST operations, including JSON value types and nullable parent-ID omission
- [x] 1.5 Implement and test pure `FanboxEndpoints` builders for creator/follow, search, plan/payment/newsletter/bell, and homepage operations
- [x] 1.6 Add a Ktor-free URL/query/host parser and migrate `FanboxCursor`, `Platform.fromUrl`, `FanboxPostMapper` pagination extraction, and `FanboxUserMapper` pagination extraction while preserving encoded-value behavior
- [x] 1.7 Add endpoint-specific `FanboxResponses` JSON/HTML parsers that reuse the production `Json`, entities, metadata parser, and mappers, with separate strict and tolerant `plan.listSupporting` functions bound by repository overload
- [x] 1.8 Split `FanboxExceptionFactory` into primitive pure failure/sanitization/Retry-After logic and a temporary Ktor adapter without changing public exception semantics
- [x] 1.9 Replace direct Napier use in `FanboxListItemDecoder` with an injected default-no-op diagnostic sink and preserve mismatch callbacks, index paths, redaction, and bounded fragments
- [x] 1.10 Add golden response, malformed URL/query, HTTP-date, schema-mismatch, and tolerant-decode tests for the pure core
- [x] 1.11 Add a source import verification task that rejects Ktor, Room, and Napier imports from the endpoint/response/mapper/parser portable boundary
- [x] 1.12 Run fanbox common tests, Android unit tests, detekt, ABI checks, import gates, and `git diff --check` with runtime repositories still using Ktorfit

## 2. PR 2 — Credential-safe Ktor executor

- [ ] 2.1 Add internal raw response and `FanboxRequestExecutor` contracts that expose only primitive status, headers, and body text to repositories
- [ ] 2.2 Add an exhaustive host-owned endpoint policy mapping every endpoint ID to exact HTTPS origin, allowed method, and credential behavior
- [ ] 2.3 Implement relative-path and final-URL validation that rejects scheme, authority, userinfo, query/fragment injection, traversal segments, unknown IDs, and method mismatches before storage access; allow empty path only for homepage and normalize it to exact `GET https://www.fanbox.cc/`
- [ ] 2.4 Implement one raw-body Ktor request client for both GET and POST without ContentNegotiation variants; encode query values once and preserve common origin, referer, user-agent, shared Cookie storage, request-time CSRF provider, and explicit `x-csrf-token` non-overwrite behavior
- [ ] 2.5 Disable automatic redirects and implement bounded redirect handling that preserves current effective behavior for same-origin redirects while revalidating exact origin and allowed method before every redirected request and credential lookup; cover 301/302/307/308
- [ ] 2.6 Connect non-success responses, network errors, cancellation, schema mismatches, sanitized fragments, and Retry-After values to the pure failure interpreter
- [ ] 2.7 Wire the unused executor alongside the generated APIs without changing public operation routing; register its client in the `Fanbox` owned-client list immediately, reuse the exact same `FanboxDependencies.cookieStorage` and `getCsrfToken` provider as generated clients, and verify construction-failure and repeated-close cleanup
- [ ] 2.8 Add MockEngine tests proving accepted API requests, exact homepage `GET /`, validation-before-credential ordering, disallowed origins/methods/paths, redirect escape rejection, latest-token resolution, explicit-token non-overwrite, and executor-client close
- [ ] 2.9 Run executor security tests, lifecycle tests, existing exception/CSRF/storage tests, detekt, and `git diff --check`

## 3. PR 3 — Post repository migration

- [ ] 3.1 Migrate post list, creator list/pagination, tagged list, info, and comments GET operations to descriptor → executor → parser
- [ ] 3.2 Migrate post/comment like and comment add/delete POST operations to descriptor → executor while preserving Unit responses and exact JSON bodies
- [ ] 3.3 Preserve post cursor extraction, empty pagination, tolerant comment decoding, mismatch callbacks, and endpoint-specific typed failures on the new path
- [ ] 3.4 Move post MockEngine and fixture tests from generated API setup to executor setup and add temporary old/new wire differential assertions where needed
- [ ] 3.5 Verify the mixed graph sends each post operation exactly once, leaves creator/search/user on Ktorfit, shares Cookie/CSRF state across metadata refresh and executor POST, and closes both generated and executor-owned clients safely
- [ ] 3.6 Run post golden tests, comment submission tests, pagination tests, tolerant decoding tests, HTTP exception tests, lifecycle tests, all fanbox tests, detekt, and `git diff --check`

## 4. PR 4 — Remaining repository migration and generated API removal

- [ ] 4.1 Migrate creator detail/list/plan/featured-tag GET operations and follow create/delete POST operations to descriptor → executor → parser
- [ ] 4.2 Migrate creator/tag search operations to descriptor → executor → parser
- [ ] 4.3 Migrate supporting plan strict/tolerant, payment, newsletter, bell, and homepage metadata operations to descriptor → executor → parser
- [ ] 4.4 Update `Fanbox.buildResources()` and repository constructors to own one normal request executor/client plus the separate download client
- [ ] 4.5 Delete the four fanbox Ktorfit interfaces, generated `createFanbox*Api` calls, ContentNegotiation split, and obsolete Ktor schema-mismatch plugin code
- [ ] 4.6 Replace annotation-based `FanboxRouteDriftTest` with exhaustive endpoint builder, trusted policy, parser-mode, diagnostic-label, 29-operation, and 28-route inventory checks
- [ ] 4.7 Update request-time CSRF, lifecycle, download inventory, creator/search/user, storage, fixture, and MockEngine tests for the final executor graph
- [ ] 4.8 Run all fanbox tests, Android unit tests, boundary fixtures, lifecycle/download tests, detekt, ABI checks, and `git diff --check`

## 5. PR 5 — Fanbox build cleanup and final verification

- [ ] 5.1 Remove the Ktorfit convention plugin, runtime dependency, and Ktorfit KSP processor from `:fankt:fanbox` only
- [ ] 5.2 Make every sources/publication task retain a mandatory dependency on `kspCommonMainKotlinMetadata` when that task exists and skip only modules where the task is absent
- [ ] 5.3 Verify Fantia and Room persistence modules retain their required Ktorfit/Room KSP plugins and processors, that generated metadata tasks complete before every sources artifact, and that generated sources remain included
- [ ] 5.4 Confirm descriptor/parser symbols remain absent from API dumps and all pre-existing `Fanbox` operations and domain model signatures remain compatible
- [ ] 5.5 Update README with the current portable request/response core, internal Ktor executor boundary, credential validation order, and separate download path
- [ ] 5.6 Search README and `docs/` for Ktorfit, generated API, CSRF client graph, route inventory, and request/response boundary descriptions and correct all stale statements in this PR
- [ ] 5.7 Run fanbox `allTests`, Android unit tests, detekt, Ktor/persistence/import boundary gates, legacy ABI checks, compatibility tests, publication task configuration, Fantia build checks, and `git diff --check`
- [ ] 5.8 Confirm the final source and dependency inventories contain no fanbox Ktorfit annotation, generated API symbol, or Ktorfit processor dependency and validate the OpenSpec change
