## 1. Owned client graph

- [ ] 1.1 Retain the three generated clients and one raw client per content-negotiation configuration in the immutable `Fanbox` resource graph
- [ ] 1.2 Return the retained raw client from `getHttpClient()` without changing its public signature or configuration behavior
- [ ] 1.3 Close clients allocated by a partially failed resource-graph construction before propagating the failure

## 2. Lifecycle contract

- [ ] 2.1 Implement idempotent `AutoCloseable` lifecycle state and close every retained client even when a prior close reports a failure
- [ ] 2.2 Reject public storage, token, repository, and raw-client entry points started after close completes
- [ ] 2.3 Document ownership, close responsibility, post-close behavior, and deferred-request limitations in KDoc and README
- [ ] 2.4 Close the sample app's owned `Fanbox` from `FanboxViewModel.onCleared()`

## 3. Verification

- [ ] 3.1 Add production-path MockEngine tests for same-configuration identity, distinct configuration behavior, bounded construction, and request-time CSRF preservation
- [ ] 3.2 Add tests proving all owned clients close, repeated close is safe, and public entry points reject use after close
- [ ] 3.3 Run focused and full validation and record Scenario evidence at the validated HEAD
