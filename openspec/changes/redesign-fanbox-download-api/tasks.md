## 1. Destination boundary and transport

- [ ] 1.1 Add a pure parser/HTTPS/host/userinfo validator for the FANBOX domain boundary and exact observed external media hosts
- [ ] 1.2 Add `SendingRequest` validation to the shared owned download client so every actual redirect attempt is rejected before transport and the prior call is cancelled
- [ ] 1.3 Replace generated download-client construction with the direct shared download client while preserving lifecycle rollback and close ownership
- [ ] 1.4 Preserve request-time CSRF on `fanbox.cc` downloads and remove the header at send time for external media hosts while preserving domain-matched cookie authentication

## 2. Public API replacement

- [ ] 2.1 Remove `FanboxDownloadApi`, `FanboxDownloadRepository`, and the three identifier-based public download methods
- [ ] 2.2 Add `Fanbox.download(url, onProgress): HttpStatement` with eager validation, exact URL use, deferred execution, and progress wiring
- [ ] 2.3 Replace obsolete `.jpg` route/exception tests with the URL-based production inventory and exclude `download` from the reflection sample

## 3. Verification and documentation

- [ ] 3.1 Add production-path tests for observed hosts, exact non-JPEG URLs, deferred execution, progress, FANBOX-host CSRF retention, external-host header absence, shared ownership, and use after close
- [ ] 3.2 Add tests for non-HTTPS, unrelated/broader/lookalike hosts, malformed/userinfo URLs, redirect rejection before transport, and prior-call cancellation
- [ ] 3.3 Update README and KDoc with the current destination, ownership, execution, progress, and error contract
- [ ] 3.4 Grep README/docs/KDoc for removed API names and run focused plus full validation with Scenario evidence at the validated HEAD
