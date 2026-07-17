## 1. Owned client graph

- [x] 1.1 Retain the three generated clients and one raw client per content-negotiation configuration in the immutable `Fanbox` resource graph
- [x] 1.2 Return the retained raw client from `getHttpClient()` without changing its public signature or configuration behavior
- [x] 1.3 Close clients allocated by a partially failed resource-graph construction before propagating the failure

## 2. Lifecycle contract

- [x] 2.1 Implement idempotent `AutoCloseable` lifecycle state and close every retained client even when a prior close reports a failure
- [x] 2.2 Reject public Flow-property, storage, token, repository, and raw-client entry points started after close completes
- [x] 2.3 Document ownership, close responsibility, asynchronous Ktor shutdown, post-close behavior, and deferred-request exception limitations in class, raw-client, and download KDoc plus README
- [x] 2.4 Close the sample app's owned `Fanbox` from `FanboxViewModel.onCleared()`

## 3. Verification

- [x] 3.1 Update existing client-count assertions and add production-path MockEngine tests for same-configuration identity, distinct configuration behavior, bounded construction, and request-time CSRF preservation
- [x] 3.2 Add tests proving all observable owned clients reject requests after close, repeated sequential close is safe, partial construction rolls back returned clients, and public entry points reject use after close
- [x] 3.3 Run focused and full validation and record Scenario evidence at the validated HEAD
