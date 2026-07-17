## ADDED Requirements

### Requirement: Requests resolve the current CSRF token
The FANBOX client SHALL resolve the latest persisted CSRF token when a request is sent. This requirement traces to issue #21 acceptance criterion "`updateCsrfToken()` 直後の POST に新トークンが載ること".

#### Scenario: POST immediately after token update
- **WHEN** `updateCsrfToken()` completes and a POST request is started
- **THEN** the request contains the token persisted by that completed update in its `x-csrf-token` header

#### Scenario: Later token update on an existing client
- **WHEN** a client has already sent a request and a later token update completes
- **THEN** the next request through that client contains the later token rather than the value used by the earlier request

#### Scenario: Equal or non-monotonic update timestamps
- **WHEN** persisted token rows have equal or non-monotonic creation timestamps
- **THEN** the client resolves the row inserted last according to its auto-generated primary key

#### Scenario: Explicit raw-client header
- **WHEN** a request made by the public raw client explicitly supplies `x-csrf-token`
- **THEN** that request preserves the explicit value instead of adding the persisted default

### Requirement: Token updates preserve the internal client graph
The FANBOX client SHALL construct its internal HttpClient, Ktorfit API, and repository graph once per `Fanbox` instance and SHALL NOT replace that graph when the CSRF token changes. This requirement traces to issue #21 acceptance criterion "トークン更新後も HttpClient インスタンスが同一であること".

#### Scenario: Client identity remains stable
- **WHEN** the persisted CSRF token is updated after the internal client has been constructed
- **THEN** requests before and after the update are sent by the same HttpClient instance

#### Scenario: Repository graph is immutable
- **WHEN** the persisted CSRF token emits any number of values
- **THEN** no client, generated API, mapper, or repository is reconstructed because of those emissions
