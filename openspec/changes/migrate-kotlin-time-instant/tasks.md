## 1. Stdlib timestamp migration

- [x] 1.1 Replace every production `kotlinx.datetime.Instant` and `Clock` use with explicitly opted-in `kotlin.time` ownership across public models, mappers, expiry, and diagnostics
- [x] 1.2 Migrate tests and add focused parsing and serialization round-trip coverage for representative public timestamp models
- [x] 1.3 Regenerate Android and Kotlin/Native ABI baselines and prove they contain stdlib Instant descriptors only

## 2. Dependency and publication boundary

- [x] 2.1 Remove the kotlinx-datetime version catalog entry, `infra-api` bundle member, and `fanbox` API dependency after verifying no production calendar/time-zone use remains
- [x] 2.2 Add a boundary gate that rejects deprecated datetime source/ABI references and any kotlinx-datetime component in Android or KMP publication metadata
- [x] 2.3 Update the isolated consumer to opt in and compile `kotlin.time.Instant` plus model serialization, then reject kotlinx-datetime on compile/runtime classpaths
- [x] 2.4 Run the isolated multi-publication and consumer boundary script with configuration-cache verification

## 3. Consumer migration and documentation

- [x] 3.1 Update the existing README v0.1.0 migration section with the stdlib timestamp replacement, `ExperimentalTime` caveat, four bridge removals, and independent normal datetime rule
- [x] 3.2 Validate all four known PixiView bridge call sites with explicit opt-ins against the isolated fankt candidate in a disposable worktree without modifying its dirty main checkout
- [x] 3.3 Search README and OpenSpec documentation for stale public timestamp or compat dependency guidance and update affected current-spec wording

## 4. Final validation and delivery

- [ ] 4.1 Run focused Android/common/iOS tests, ABI and dependency gates, detekt, strict OpenSpec validation, and full repository validation on the final implementation HEAD
- [ ] 4.2 Create the PR with breaking migration notes, validation evidence, and documentation impact
- [ ] 4.3 Complete clean-context review, close every accepted finding, and obtain green required CI on the final review-fix HEAD
