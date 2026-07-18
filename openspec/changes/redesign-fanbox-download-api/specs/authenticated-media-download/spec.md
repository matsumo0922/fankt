## ADDED Requirements

### Requirement: Fanbox.download destinations are allowlisted

`Fanbox.download` SHALL accept only absolute HTTPS URLs without userinfo whose normalized host is `fanbox.cc`, a dot-delimited subdomain of `fanbox.cc`, exact `pixiv.pximg.net`, or exact `fanbox.pixiv.net`, and SHALL normalize parse or validation failures to `IllegalArgumentException` before sending a request to any other destination. The same rule SHALL apply to every redirect request; this guarantee applies to `download` and does not constrain generic `getHttpClient()` requests. Trace: issue #32 send-destination validation and out-of-allowlist rejection acceptance criterion plus the observed public response media hosts.

#### Scenario: FANBOX media subdomain is accepted

- **WHEN** `download` receives an HTTPS URL on `downloads.fanbox.cc`
- **THEN** it returns a deferred media request for that URL

#### Scenario: Legacy pixiv FANBOX subdomain is accepted

- **WHEN** `download` receives an HTTPS URL on `fanbox.pixiv.net`
- **THEN** it returns a deferred media request for that URL without attaching a FANBOX CSRF token

#### Scenario: Observed pximg media host is accepted

- **WHEN** `download` receives an HTTPS URL on exact host `pixiv.pximg.net`
- **THEN** it returns a deferred media request for that URL without attaching a FANBOX CSRF token

#### Scenario: FANBOX-host download preserves request-time CSRF behavior

- **WHEN** `download` executes an HTTPS URL inside the `fanbox.cc` boundary after a token update
- **THEN** the request contains the current in-memory token while domain-matched cookies provide download authentication

#### Scenario: Non-HTTPS URL is rejected eagerly

- **WHEN** `download` receives an HTTP URL on an otherwise allowed host
- **THEN** it throws `IllegalArgumentException` before returning a statement or sending a request

#### Scenario: Disallowed, broader, and lookalike hosts are rejected eagerly

- **WHEN** `download` receives an HTTPS URL on an unrelated host, another `pixiv.net` / `pximg.net` host, `fanbox.cc.evil.example`, or `notfanbox.cc`
- **THEN** it throws `IllegalArgumentException` before returning a statement or sending a request

#### Scenario: Malformed or credential-bearing URL is rejected uniformly

- **WHEN** `download` receives a malformed absolute URL or a URL containing userinfo
- **THEN** it throws `IllegalArgumentException` rather than leaking a parser-specific exception or returning a statement

#### Scenario: Redirect cannot escape the allowlist

- **WHEN** an allowed media URL redirects to a URL whose scheme or host is not allowed
- **THEN** the prior call is cancelled and execution throws `IllegalArgumentException` before the redirected request reaches transport

### Requirement: API-provided media URLs are requested without reconstruction

`Fanbox.download` SHALL create a deferred GET request using the complete caller-supplied URL without changing its path, filename extension, or query, and SHALL use a download client shared and owned by the `Fanbox` instance. Trace: issue #32 `.jpg` hard-code removal, shared-client dependency, and PixiView migration acceptance criterion.

#### Scenario: File extension and query are preserved

- **WHEN** `download` receives an allowed URL ending in `.zip` with a query string
- **THEN** execution sends one GET request with the same path, `.zip` extension, and query values

#### Scenario: Request remains deferred

- **WHEN** `download` returns a statement and the caller has not executed or consumed it
- **THEN** no network request has been sent

#### Scenario: Progress is reported while consuming the response

- **WHEN** the caller executes a download whose response has a known content length
- **THEN** `onProgress` receives downloaded-byte progress as a `Float` fraction compatible with the existing consumer callback

#### Scenario: Owner close applies to download work

- **WHEN** the owning `Fanbox` is closed before a returned download statement is executed
- **THEN** execution cannot start a network request through the closed shared download client

### Requirement: Extension-specific download APIs are removed

The public `Fanbox` API SHALL expose `download(url, onProgress)` instead of `downloadPostFile`, `downloadPostImage`, and `downloadPostThumbnailImage`, and production source SHALL contain no generated `FanboxDownloadApi` route declarations. Trace: issue #32 download API removal checklist.

#### Scenario: Public download operation inventory

- **WHEN** the production `Fanbox` download API surface and generated route declarations are inspected
- **THEN** only the URL-based public download operation remains and no `.jpg` download route is declared
