## 1. Request-time token resolution

- [ ] 1.1 Add production-shared request configuration that resolves the CSRF default header at send time while preserving an explicit request value
- [ ] 1.2 Make latest-token selection deterministic by ordering on the auto-generated insertion id
- [ ] 1.3 Construct the internal HttpClient, Ktorfit API, mapper, and repository graph once as immutable properties
- [ ] 1.4 Remove the token collector, rebuild coroutine scope, and construction-time token parameter

## 2. Verification and documentation

- [ ] 2.1 Add production-call-path MockEngine tests proving immediate updated-token visibility, explicit-header precedence, deterministic latest selection, and stable client identity
- [ ] 2.2 Update README CSRF guidance to state the completed-update postcondition
- [ ] 2.3 Run focused and full validation and record Scenario evidence at the validated HEAD
