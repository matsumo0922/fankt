## MODIFIED Requirements

### Requirement: Fanbox.download destinations are allowlisted
`Fanbox.download` SHALL accept only absolute HTTPS URLs without userinfo whose normalized host is `fanbox.cc`, a dot-delimited subdomain of `fanbox.cc`, exact `pixiv.pximg.net`, or exact `fanbox.pixiv.net`, and SHALL normalize parse or validation failures to `IllegalArgumentException` before sending a request to any other destination. The same rule SHALL apply to every redirect request. Trace: issue #32 send-destination validation and out-of-allowlist rejection acceptance criterion plus the observed public response media hosts.

#### Scenario: FANBOX media subdomain is accepted
- **WHEN** `download` receives an HTTPS URL on `downloads.fanbox.cc`
- **THEN** it streams the response chunks for that URL

#### Scenario: Legacy pixiv FANBOX subdomain is accepted
- **WHEN** `download` receives an HTTPS URL on `fanbox.pixiv.net`
- **THEN** it streams the response without attaching a FANBOX CSRF token

#### Scenario: Observed pximg media host is accepted
- **WHEN** `download` receives an HTTPS URL on exact host `pixiv.pximg.net`
- **THEN** it streams the response without attaching a FANBOX CSRF token

#### Scenario: FANBOX-host download preserves request-time CSRF behavior
- **WHEN** `download` executes an HTTPS URL inside the `fanbox.cc` boundary after a token update
- **THEN** the request contains the current in-memory token while domain-matched cookies provide download authentication

#### Scenario: Non-HTTPS URL is rejected
- **WHEN** `download` receives an HTTP URL on an otherwise allowed host
- **THEN** it throws `IllegalArgumentException` before sending a request or invoking the chunk callback

#### Scenario: Disallowed, broader, and lookalike hosts are rejected
- **WHEN** `download` receives an HTTPS URL on an unrelated host, another `pixiv.net` or `pximg.net` host, `fanbox.cc.evil.example`, or `notfanbox.cc`
- **THEN** it throws `IllegalArgumentException` before sending a request or invoking the chunk callback

#### Scenario: Malformed or credential-bearing URL is rejected uniformly
- **WHEN** `download` receives a malformed absolute URL or a URL containing userinfo
- **THEN** it throws `IllegalArgumentException` rather than leaking a parser-specific exception or starting a request

#### Scenario: Redirect cannot escape the allowlist
- **WHEN** an allowed media URL redirects to a URL whose scheme or host is not allowed
- **THEN** execution rejects the redirect before the redirected request reaches transport

### Requirement: API-provided media URLs are streamed without reconstruction
`Fanbox.download` SHALL suspend while it creates and consumes one GET response using the complete caller-supplied URL without changing its path, filename extension, or query. It SHALL consume the response inside a bounded execution scope that releases the response on success, callback failure, cancellation, transport failure, or owner close. Each invocation SHALL use a download-specific Job whose optional parent is the caller context's Job, so Job-less coroutine contexts remain valid and owner close cancels the download without cancelling the caller's parent Job. It SHALL read and deliver one bounded response chunk at a time to a suspend callback in order, SHALL request the next read only after that callback completes, SHALL expose no whole-body return, and SHALL use a download client shared and owned by the `Fanbox` instance.

#### Scenario: File extension and query are preserved
- **WHEN** `download` receives an allowed URL ending in `.zip` with a query string
- **THEN** execution sends one GET request with the same path, `.zip` extension, and query values

#### Scenario: Response is delivered incrementally
- **WHEN** a response is larger than one read buffer
- **THEN** `download` invokes the suspend chunk callback in response order with bounded non-empty byte arrays rather than returning a whole-response byte array

#### Scenario: Slow consumer applies backpressure
- **WHEN** the suspend chunk callback has not completed for a delivered chunk
- **THEN** `download` does not request or deliver the next chunk

#### Scenario: Progress starts at zero
- **WHEN** an allowed response begins consumption
- **THEN** `onProgress` receives one initial `0f` before any chunk callback is invoked

#### Scenario: Progress is reported while consuming the response
- **WHEN** a download response has a positive content length
- **THEN** `onProgress` receives downloaded-byte progress as a finite `Float` fraction after the corresponding chunk callback completes

#### Scenario: Empty or unknown-length progress is safe
- **WHEN** a download response length is zero or unknown
- **THEN** `onProgress` receives the initial `0f` and never receives a non-finite value

#### Scenario: Chunk callback failure cancels consumption
- **WHEN** the suspend chunk callback throws or is cancelled
- **THEN** response consumption stops, the response is released, and the same failure is propagated without being normalized as an HTTP failure

#### Scenario: Caller context has no Job
- **WHEN** `download` is invoked from a coroutine context without a Job
- **THEN** it creates a download-specific Job and streams the response normally

#### Scenario: Owner close prevents new download work
- **WHEN** `download` is called after the owning `Fanbox` is closed
- **THEN** it fails with `IllegalStateException` before starting a network request

#### Scenario: Owner closes during active download
- **WHEN** the owning `Fanbox` closes while a download is consuming a response
- **THEN** response consumption stops, resources are released, the download-specific Job is cancelled without cancelling its caller parent, cancellation is rethrown unchanged or a non-cancellation transport failure is normalized to `FanboxException`, and already delivered chunks remain the caller's responsibility

#### Scenario: Download transport failure is normalized
- **WHEN** an allowed download receives a non-success HTTP response or the transport fails before or during body consumption
- **THEN** `download` throws `FanboxException` without retaining a raw response body

### Requirement: Extension-specific download APIs are removed
The public `Fanbox` API SHALL expose one URL-based chunk-streaming `download` operation instead of `downloadPostFile`, `downloadPostImage`, and `downloadPostThumbnailImage`, and production source SHALL contain no generated `FanboxDownloadApi` route declarations. Trace: issue #32 download API removal checklist.

#### Scenario: Public download operation inventory
- **WHEN** the production `Fanbox` download API surface and generated route declarations are inspected
- **THEN** only the URL-based public download operation remains and no `.jpg` download route is declared
