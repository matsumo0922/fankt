## 1. Request-time token resolution

- [x] 1.1 Add production-shared request configuration that resolves the CSRF default header at send time while preserving an explicit request value
- [x] 1.2 Make latest-token selection deterministic by ordering on the auto-generated insertion id
- [x] 1.3 Construct the internal HttpClient, Ktorfit API, mapper, and repository graph once as immutable properties
- [x] 1.4 Remove the token collector, rebuild coroutine scope, and construction-time token parameter

## 2. Verification and documentation

- [x] 2.1 Add production-call-path MockEngine tests proving immediate updated-token visibility, last-committed-wins semantics, explicit-header precedence, deterministic latest selection, and stable client identity
- [x] 2.2 Update README CSRF guidance to state the completed-update postcondition
- [x] 2.3 Run focused and full validation and record Scenario evidence at the validated HEAD
