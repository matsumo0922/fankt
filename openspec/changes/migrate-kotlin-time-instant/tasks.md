## 1. Stdlib timestamp migration

- [ ] 1.1 Replace every production `kotlinx.datetime.Instant` and `Clock` use with `kotlin.time` ownership across public models, mappers, expiry, and diagnostics
- [ ] 1.2 Migrate tests and add focused parsing and serialization round-trip coverage for representative public timestamp models
- [ ] 1.3 Regenerate Android and Kotlin/Native ABI baselines and prove they contain stdlib Instant descriptors only

## 2. Dependency and publication boundary

- [ ] 2.1 Remove the kotlinx-datetime version catalog entry and `fanbox` API dependency after verifying no production calendar/time-zone use remains
- [ ] 2.2 Add a boundary gate that rejects deprecated datetime source/ABI references and any kotlinx-datetime component in Android or KMP publication metadata
- [ ] 2.3 Update the isolated consumer to compile `kotlin.time.Instant` plus model serialization and reject kotlinx-datetime on compile/runtime classpaths
- [ ] 2.4 Run the isolated multi-publication and consumer boundary script with configuration-cache verification

## 3. Consumer migration and documentation

- [ ] 3.1 Update the existing README v0.1.0 migration section with the stdlib timestamp replacement, bridge removal, and independent normal datetime rule
- [ ] 3.2 Validate the known PixiView timestamp call sites against the isolated fankt candidate in a disposable worktree without modifying its dirty main checkout
- [ ] 3.3 Search README and OpenSpec documentation for stale public timestamp or compat dependency guidance and update affected current-spec wording

## 4. Final validation and delivery

- [ ] 4.1 Run focused Android/common/iOS tests, ABI and dependency gates, detekt, strict OpenSpec validation, and full repository validation on the final implementation HEAD
- [ ] 4.2 Create the PR with breaking migration notes, validation evidence, and documentation impact
- [ ] 4.3 Complete clean-context review, close every accepted finding, and obtain green required CI on the final review-fix HEAD
