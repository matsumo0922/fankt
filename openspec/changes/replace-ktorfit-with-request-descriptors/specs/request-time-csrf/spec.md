## MODIFIED Requirements

### Requirement: Requests resolve the current CSRF token
The FANBOX descriptor executor SHALL resolve the current token from the `FanboxTokenStore` injected into that `Fanbox` when an accepted request is sent. The shared download client SHALL preserve that behavior for destinations inside the `fanbox.cc` boundary and SHALL remove the `x-csrf-token` header before sending to allowed external media hosts. Descriptor policy validation MUST complete before the token store is read. This requirement traces to Issue #21, Issue #24, Issue #32, and is revised by Issue #33 to make token ownership explicit.

#### Scenario: POST immediately after token update
- **WHEN** `updateCsrfToken()` completes without a concurrent token update and a POST descriptor request is started
- **THEN** the request contains the token published by that completed update in its `x-csrf-token` header

#### Scenario: Concurrent token updates
- **WHEN** multiple token updates overlap against one injected store and an accepted request starts after one of them completes
- **THEN** the request contains the value from that completed update or a token whose store completed later

#### Scenario: Later token update on an existing executor
- **WHEN** the executor has already sent a request and a later token update completes
- **THEN** the next accepted request through that executor contains the later token rather than the value used by the earlier request

#### Scenario: Rejected descriptor does not resolve the token
- **WHEN** descriptor endpoint, origin, method, or path validation fails
- **THEN** `FanboxTokenStore` is not read and no `x-csrf-token` header is created

#### Scenario: Explicit transport header is preserved
- **WHEN** the executor transport adapter has already supplied `x-csrf-token` on a validated request
- **THEN** request-time token resolution preserves that explicit value instead of replacing it with the injected-store default

#### Scenario: FANBOX-host download resolves the current token
- **WHEN** a download request is sent to `fanbox.cc` or one of its dot-delimited subdomains after a token update completes
- **THEN** the request contains the current token from that `Fanbox` store in its `x-csrf-token` header

#### Scenario: External media download omits the token
- **WHEN** a download request or redirect is sent to allowed external host `pixiv.pximg.net` or `fanbox.pixiv.net`
- **THEN** the request contains no `x-csrf-token` header

### Requirement: Token updates preserve the internal client graph
The FANBOX client SHALL construct its request executor, owned HttpClient, download client, parser dependencies, and repository graph once per `Fanbox` instance and SHALL NOT replace that graph when the in-memory CSRF token changes. This requirement traces to Issue #21 acceptance criterion "トークン更新後も HttpClient インスタンスが同一であること".

#### Scenario: Client identity remains stable
- **WHEN** the in-memory CSRF token is updated after the internal client has been constructed
- **THEN** requests before and after the update are sent by the same executor-owned HttpClient instance

#### Scenario: Repository graph is immutable
- **WHEN** the in-memory CSRF token emits any number of values
- **THEN** no executor, client, parser, mapper, or repository is reconstructed because of those emissions
