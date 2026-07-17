## 1. In-memory token graph

- [ ] 1.1 Replace Room-backed token dependencies with one process-local `MutableStateFlow<String?>` shared across production `Fanbox` graphs while preserving the public `Flow<String?>`
- [ ] 1.2 Change the request-time plugin and metadata update helper to read and store nullable/String token values without the Room entity or timestamps
- [ ] 1.3 Clear token state at successful session replacement, reset-cookie, and additive `FANBOXSESSID` boundaries while preserving it for unrelated additive cookies and failed pre-boundary operations

## 2. Room schema removal

- [ ] 2.1 Remove `CSRFToken`, `TokenDao`, and token DAO exposure; bump `FanktDatabase` from v2 to v3 with only `CookieEntity`
- [ ] 2.2 Add and register an explicit v2-to-v3 migration on Android and iOS that drops only `fankt_csrf_tokens`
- [ ] 2.3 Export and review schema v3 so the cookie table, primary key, and expiry index match v2

## 3. Verification and documentation

- [ ] 3.1 Adapt production-path CSRF tests for immediate update-to-request visibility, Flow emission, stable clients, concurrent completion order, and explicit-header precedence
- [ ] 3.2 Add credential-boundary and multi-instance tests for successful/failed session replacement, reset-cookie partial failure, additive `FANBOXSESSID`, unrelated cookie preservation, and shared process state
- [ ] 3.3 Extend migration tests for direct v2-to-v3 and chained v1-to-v3 cookie preservation plus token-table removal
- [ ] 3.4 Update README, sample session-refresh flow, KDoc, and the current request-time OpenSpec Purpose to describe process-session in-memory lifetime and required ordering
- [ ] 3.5 Run focused and final full validation through the shared validation lease and record Scenario evidence at the validated HEAD
