## 1. Request-time token resolution

- [ ] 1.1 Add production-shared request configuration that resolves and replaces the CSRF header at send time
- [ ] 1.2 Construct the internal HttpClient, Ktorfit API, mapper, and repository graph once as immutable properties
- [ ] 1.3 Remove the token collector, rebuild coroutine scope, and construction-time token parameter

## 2. Verification and documentation

- [ ] 2.1 Add MockEngine tests proving immediate updated-token visibility and stable client identity
- [ ] 2.2 Update README CSRF guidance to state the completed-update postcondition
- [ ] 2.3 Run focused and full validation and record Scenario evidence at the validated HEAD
