## 1. Toolchain Update

- [ ] 1.1 Set Kotlin to 2.3.21 and kotlinx.serialization to 1.10.0 in the version catalog, with the Kotlin 2.3.x Zipline compatibility constraint documented next to the version.
- [ ] 1.2 Run the Kotlin 2.3 toolchain and fix only compile errors, warning-as-error failures, and deprecations that block the Issue #88 acceptance commands.

## 2. Repository Validation

- [ ] 2.1 Run the initial implementation full validation with `build` and the explicit `:fankt:fanbox:jsTest` task under an isolated Gradle home and the validation lease.
- [ ] 2.2 Inspect README, `docs/`, and KDoc references to the changed Kotlin / serialization toolchain and update any statement made inaccurate by this change.
- [ ] 2.3 Validate the completed OpenSpec change and record each Scenario's command, result, scope, and implementation HEAD.

## 3. External Compatibility Evidence

- [ ] 3.1 Apply `app.cash.zipline` 1.27.0 only in a disposable copy of the final fankt implementation, disable guest API tracking, and verify `build` plus `:fankt:fanbox:jsTest`.
- [ ] 3.2 Publish the final fankt artifacts to an empty isolated Maven repository and verify a detached Kotlin 2.4.0 PixiView-KMP worktree builds while resolving those local artifacts.
