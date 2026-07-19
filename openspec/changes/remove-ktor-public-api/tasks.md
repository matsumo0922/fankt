## 1. Public API boundary

- [ ] 1.1 Add `FanboxLogLevel` and map it to the security-capped internal logging levels
- [ ] 1.2 Change `Fanbox.cookies` and `Fanbox.setCookies` to use `FanboxCookieRecord`
- [ ] 1.3 Remove `getHttpClient` and the unused raw-client graph while preserving close and construction-failure cleanup

## 2. Streaming download

- [ ] 2.1 Replace `HttpStatement` exposure with suspend, backpressured chunk streaming
- [ ] 2.2 Preserve destination/redirect validation, authentication scoping, progress, and error normalization tests
- [ ] 2.3 Add tests for bounded ordered chunks, slow consumers, callback failure identity, cancellation, and closed-owner behavior

## 3. Dependency and ABI enforcement

- [ ] 3.1 Move common, Android, and iOS Ktor dependencies from `api` to `implementation`
- [ ] 3.2 Enable Kotlin ABI validation, commit the intended `fanbox` ABI baseline, and reject `io.ktor` references
- [ ] 3.3 Generate and inspect Gradle module metadata so no API variant publishes Ktor dependencies
- [ ] 3.4 Compile a consumer fixture that selects a compatible Ktor version without inheriting Ktor through the fankt compile API

## 4. Consumers and documentation

- [ ] 4.1 Migrate repository sample/compose consumers and tests to fankt-owned types and streaming downloads
- [ ] 4.2 Update README migration guidance, platform-code boundary, dependency compatibility wording, and affected KDoc
- [ ] 4.3 Search README and docs for stale Ktor-facing API references and correct them

## 5. Validation and delivery

- [ ] 5.1 Run focused common, Android, iOS, download, ABI, and publication checks with isolated caches
- [ ] 5.2 Run the required repository test and static-analysis suites
- [ ] 5.3 Validate OpenSpec artifacts strictly and reconcile completed task state
