## MODIFIED Requirements

### Requirement: Requests resolve the current CSRF token
The FANBOX generated API and public raw clients SHALL resolve the process session's current in-memory CSRF token when a request is sent. The shared download client SHALL preserve that behavior for destinations inside the `fanbox.cc` boundary and SHALL remove the `x-csrf-token` header before sending to allowed external media hosts. This requirement traces to Issue #21 acceptance criterion "`updateCsrfToken()` 直後の POST に新トークンが載ること", Issue #24's storage replacement, and Issue #32's authenticated download destination boundary.

#### Scenario: POST immediately after token update
- **WHEN** `updateCsrfToken()` completes without a concurrent token update and a POST request is started
- **THEN** the request contains the in-memory token published by that completed update in its `x-csrf-token` header

#### Scenario: Concurrent token updates
- **WHEN** multiple token updates overlap and a request starts after one of them completes
- **THEN** the request contains the value from that completed update or a token whose store completed later

#### Scenario: Later token update on an existing client
- **WHEN** a client has already sent a request and a later token update completes
- **THEN** the next request through that client contains the later token rather than the value used by the earlier request

#### Scenario: Explicit raw-client header
- **WHEN** a request made by the public raw client explicitly supplies `x-csrf-token`
- **THEN** that request preserves the explicit value instead of adding the in-memory default

#### Scenario: FANBOX-host download resolves the current token
- **WHEN** a download request is sent to `fanbox.cc` or one of its dot-delimited subdomains after a token update completes
- **THEN** the request contains the current in-memory token in its `x-csrf-token` header

#### Scenario: External media download omits the token
- **WHEN** a download request or redirect is sent to allowed external host `pixiv.pximg.net` or `fanbox.pixiv.net`
- **THEN** the request contains no `x-csrf-token` header
