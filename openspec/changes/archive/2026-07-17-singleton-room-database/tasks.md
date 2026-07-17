## 1. Database lifecycle

- [x] 1.1 Wrap the Android `FanktDatabase` builder in a synchronized process-local lazy and return it from `getFanktDatabase()`
- [x] 1.2 Wrap the iOS `FanktDatabase` builder in a synchronized process-local lazy and return it from `getFanktDatabase()`
- [x] 1.3 Confirm `createFanboxDependencies()` obtains both DAOs from one accessor result and adjust only if the invariant is not already present

## 2. Verification

- [x] 2.1 Add Robolectric / `androidx.test:core` test dependencies and direct production-accessor tests for repeated database identity on Android and iOS simulator
- [x] 2.2 Add a concurrent first-access assertion that proves all successful callers observe one database reference when initialization succeeds
- [x] 2.3 Search README, docs, and KDoc for affected Room lifecycle descriptions and update only stale current-spec documentation
- [x] 2.4 Run OpenSpec validation and the initial full test, lint, and build validation, recording Scenario evidence at the tested HEAD
