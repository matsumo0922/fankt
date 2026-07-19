## 1. Public API boundary

- [ ] 1.1 Add `FanboxLogLevel` and map it to the security-capped internal logging levels
- [ ] 1.2 Change `Fanbox.cookies` and `Fanbox.setCookies` to use `FanboxCookieRecord`, remove the competing URL parameter, and preserve expired-record deletion/omission
- [ ] 1.3 Remove `getHttpClient` and the unused raw-client graph while preserving close and construction-failure cleanup

## 2. Streaming download

- [ ] 2.1 Replace `HttpStatement` exposure with scoped suspend streaming that emits initial `0f`, reads bounded chunks one at a time, and reports known-length progress after callback completion
- [ ] 2.2 Preserve destination/redirect validation, authentication scoping, and HTTP error tests; add mid-stream transport normalization coverage
- [ ] 2.3 Add tests for bounded ordered chunks, slow consumers, callback failure identity, cancellation, response release, and close before/during download

## 3. Dependency and ABI enforcement

- [ ] 3.1 Move common, Android, and iOS Ktor dependencies from `api` to `implementation`
- [ ] 3.2 Verify Kotlin 2.2.10 ABI tooling for Android/iOS, use a deterministic compiler/KLib ABI dump fallback for unsupported targets, commit the intended `fanbox` baselines, and reject `io.ktor` references
- [ ] 3.3 Generate and inspect Gradle module metadata with provider-backed configuration-cache-compatible inputs so no API variant publishes Ktor dependencies
- [ ] 3.4 Publish to an isolated local repository and compile a consumer fixture that resolves that metadata and selects a compatible Ktor version without inheriting Ktor through the fankt compile API
- [ ] 3.5 Wire common/Android boundary checks to the Linux PR job, iOS ABI and publication-metadata checks to the macOS PR job, and the complete gate before release publication

## 4. Consumers and documentation

- [ ] 4.1 Migrate repository sample/compose consumers and tests to fankt-owned types and streaming downloads
- [ ] 4.2 Update README migration guidance including cookie field conversion, partial-file handling, diagnostic endpoint labels, platform-code boundary, dependency compatibility wording, and affected KDoc
- [ ] 4.3 Search README and docs for stale Ktor-facing API references and correct them

## 5. Validation and delivery

- [ ] 5.1 Run focused common, Android, iOS, download, ABI, locally published consumer, and publication checks with isolated caches
- [ ] 5.2 Run the required repository test and static-analysis suites
- [ ] 5.3 Validate OpenSpec artifacts strictly and reconcile completed task state
