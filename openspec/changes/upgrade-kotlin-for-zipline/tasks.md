## 1. Toolchain Update

- [x] 1.1 Set Kotlin to 2.3.21 and kotlinx.serialization to 1.10.0 in the version catalog, with the Kotlin 2.3.x Zipline compatibility constraint documented next to the version.
- [x] 1.2 Run the Kotlin 2.3 toolchain and fix only compile errors, warning-as-error failures, deprecations, and obsolete `ExperimentalTime` boundaries that block or contradict the Issue #88 toolchain contract.

## 2. Repository Validation

- [x] 2.1 Run the initial implementation full validation with `build` and the explicit `:fankt:fanbox:jsTest` task under an isolated Gradle home and the validation lease.
- [x] 2.2 Inspect README, `docs/`, and KDoc references to the changed Kotlin / serialization toolchain and update any statement made inaccurate by this change.
- [x] 2.3 Validate the completed OpenSpec change and record each Scenario's command, result, scope, and implementation HEAD.

## 3. External Compatibility Evidence

- [x] 3.1 Apply `app.cash.zipline` 1.27.0 only in a disposable copy of the final fankt implementation, disable guest API tracking, and verify `build` plus `:fankt:fanbox:jsTest`.
- [x] 3.2 Publish `fanbox` and `fanbox-persistence-room` with a unique version to an empty isolated Maven repository; verify a detached PixiView-KMP `origin/main` worktree resolves only those artifacts with matching SHA-256 and compiles `core:repository` for Android and iOS using Kotlin 2.4.0.

## Validation Evidence

Implementation HEAD for the external compatibility runs: `d7d0577e5ef1ef30b9318b6f87e4a440e83f7a0d`.

| ID | Command / inspection | Result and scope |
| --- | --- | --- |
| V1 | Inspect `gradle/libs.versions.toml` and run `rg -n 'Kotlin 2\\.[234]\|2\\.2\\.10\|2\\.3\\.21\|serialization 1\\.(9\|10)\|ExperimentalTime\|Zipline' README.md fankt --glob '*.md' --glob '*.kt' --glob '*.kts'` | Kotlin 2.3.21, serialization 1.10.0, the adjacent Kotlin 2.4 guard, stable stdlib time usage, current README guidance, and no obsolete source opt-in are present in fankt at the implementation HEAD. |
| V2 | In a disposable fankt worktree, apply `app.cash.zipline` 1.27.0 to `:fankt:fanbox`, set `zipline.apiTracking=false`, then run the validation-lease-wrapped `./gradlew build :fankt:fanbox:jsTest` with isolated Gradle, Konan, and Android homes. | `BUILD SUCCESSFUL in 6m 50s`; 517 tasks (455 executed, 62 from cache). Android, iOS, JS, FANBOX timestamp mapper/serialization tests, ABI/boundary checks, and both explicit JS and portable fixture tests pass at the implementation HEAD. |
| V3 | Run `openspec validate upgrade-kotlin-for-zipline --strict`; inspect README, the stdlib-time delta, FANBOX `Clock.System` call sites, timestamp tests, and the absence of `toStdlibInstant()` in detached PixiView-KMP `origin/main`. | OpenSpec is valid; stable stdlib ownership, consumer compiler minimum, bridge-removal guidance, independent calendar dependency guidance, runtime clock ownership, and the timestamp parse/round-trip scenarios are coherent with the implementation and tests. |
| V4 | From a second disposable fankt worktree, set version `0.1.1-issue88-final` and run `./gradlew -Dmaven.repo.local=/private/tmp/fankt-issue88-maven-final :fankt:fanbox:publishToMavenLocal :fankt:fanbox-persistence-room:publishToMavenLocal`. | `BUILD SUCCESSFUL in 1m 48s`; 238 tasks. Android, iOS, JS, and multiplatform publications for both coordinates are written only to the empty isolated repository. |
| V5 | In detached PixiView-KMP `origin/main` (`a0682ffee623d78260bc873ed710de8de91e2979`, Kotlin 2.4.0), route `me.matsumo.fankt` exclusively to V4 and run `./gradlew --no-configuration-cache :core:repository:verifyIssue88FanktProvenance`; the temporary verifier depends on `compileAndroidMain` and `compileKotlinIosSimulatorArm64`. | `BUILD SUCCESSFUL`; both production compilations pass. `fanbox-android` resolves with SHA-256 `79ec8e7c5186ed5a77f264f74e463e87f17ce5a1eae95b22a7e52aadad9e794f`, and `fanbox-persistence-room-android` resolves with `ed2bbfa11b542f17995b1d9ca948bd330a9f9edab548c49ac91ce3aa90df0c79`; both hashes match V4 byte-for-byte. |

### Scenario Matrix

| Scenario | Evidence |
| --- | --- |
| Public timestamp ABI is inspected | V2 |
| FANBOX timestamps are parsed | V2 |
| Public model timestamp is serialized | V2 |
| Runtime clocks use stdlib ownership | V1, V3 |
| Stable time boundary is current | V1, V2 |
| PixiView relative-time helpers use stable stdlib time | V3, V5 |
| PixiView retains calendar APIs independently | V3 |
| Consumer guidance is current | V1, V3 |
| Portable core survives the toolchain update | V2 |
| Version catalog selects the compatible versions | V1 |
| Full build passes | V2 |
| JavaScript tests pass explicitly | V2 |
| Disposable plugin-applied build passes | V2 |
| Isolated local artifact consumption succeeds | V4, V5 |
| Consumer compatibility boundary is explicit | V1, V3 |
