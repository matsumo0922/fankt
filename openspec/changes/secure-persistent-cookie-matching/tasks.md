## 1. Persistence model and migration

- [ ] 1.1 Replace synthetic Cookie IDs with a domain/path/name composite key and persist the secure attribute
- [ ] 1.2 Add and register the v1-to-v2 Room migration, expiry index, and HTTPS-only retention while preserving Cookie and CSRF values
- [ ] 1.3 Verify the generated v2 schema and migration behavior

## 2. Cookie storage behavior

- [ ] 2.1 Normalize Cookies and persist absolute expiry on the injected IO dispatcher
- [ ] 2.2 Filter snapshots, best-effort delete currently expired rows, and apply Ktor `Cookie.matches(requestUrl)`
- [ ] 2.3 Handle immediate expiry, max-age precedence, and overflow safely

## 3. Verification and documentation

- [ ] 3.1 Add storage tests for domain, cross-origin, path, secure, expiry cleanup, replacement identity, and dispatcher use
- [ ] 3.2 Add an HTTP client test through the production client factory that proves the `HttpCookies -> PersistentCookieStorage` call path does not emit cross-origin or expired Cookies
- [ ] 3.3 Update affected README/docs/KDoc if repository search finds user-facing Cookie behavior that needs clarification
- [ ] 3.4 Run OpenSpec validation and full project validation, recording Scenario evidence at the tested HEAD
