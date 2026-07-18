# pixiview-secure-session-migration Specification

## Purpose
Define the downstream PixiView-KMP contract for platform-secure FANBOX Cookie persistence, crash-safe migration from legacy fankt storage, safe logout, and credential-safe observability.

## Requirements

### Requirement: Platform-secure Cookie persistence
PixiView-KMP SHALL provide one app-owned `FanboxCookieStorage` whose credential payload is protected by a non-user-authenticated Android Keystore AES-256-GCM key and atomic app-private file on Android and by a `kSecAttrSynchronizable=false`, `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` Keychain item on iOS. The persisted payload SHALL contain the complete FANBOX Cookie records needed for matching and one migration-completion marker, and SHALL be committed as one atomic destination value. The Android secure payload and legacy `fankt.db` SHALL be excluded from cloud backup and device transfer.

#### Scenario: New login persists only to secure storage
- **WHEN** a user logs in after secure storage is active
- **THEN** the FANBOX Cookie records are written to the platform-secure payload and no new rows are written to the legacy fankt Room database

#### Scenario: App restart restores the session
- **WHEN** the secure payload contains a valid `FANBOXSESSID` and the app process restarts
- **THEN** the injected storage exposes that session before the first authenticated FANBOX request

#### Scenario: iOS credential is not synchronized
- **WHEN** PixiView persists the secure payload on iOS
- **THEN** the Keychain item is device-only and is not eligible for iCloud Keychain synchronization

#### Scenario: iOS accesses credentials after first unlock
- **WHEN** the device has been unlocked once after reboot and PixiView performs background FANBOX work while locked
- **THEN** the Keychain item remains accessible without user interaction

#### Scenario: Android backup omits credential artifacts
- **WHEN** Android cloud backup or device transfer collects PixiView app data
- **THEN** neither the Keystore-encrypted payload nor legacy `fankt.db` and its sidecars are included

#### Scenario: Observation initializes consumer state
- **WHEN** routing selects the secure destination, legacy fallback, an empty recovered store, or remains in retryable initialization failure
- **THEN** the injected Cookie observation Flow emits the selected snapshot or empty records so `FanboxRepository.sessionId` and combined UI state can produce a value

### Requirement: Recoverable secure-storage failure
PixiView-KMP SHALL classify a missing encryption key, AEAD authentication failure, and unsupported/corrupt payload as permanent credential corruption and SHALL quarantine or delete that payload regardless of whether a usable legacy source exists. With a usable legacy source it SHALL select that source and retry migration into a new destination; without one it SHALL initialize an empty secure store and permit a new login. This permanent-corruption path SHALL remain distinct from a decryptable records-without-marker conflict, which preserves the destination. Transient Keystore, Keychain, protected-data, or I/O unavailability SHALL retain the payload and return a retryable initialization failure without accepting replacement writes.

#### Scenario: Restored ciphertext has no key
- **WHEN** an Android secure payload exists but its Keystore key is absent and no usable legacy source exists
- **THEN** PixiView removes or quarantines the unreadable payload and permits the user to log in again to a new secure payload

#### Scenario: Secure API is temporarily unavailable
- **WHEN** the platform secure API reports a retryable unavailable condition
- **THEN** PixiView preserves the existing payload, exposes an empty read snapshot and observation value, releases initialization gating for UI and unauthenticated paths, rejects replacement writes with a retryable initialization failure, does not delete credentials because a request reports unauthenticated, and retries initialization later

### Requirement: Crash-safe legacy migration
Before the first authenticated FANBOX request, PixiView-KMP SHALL serialize Cookie access and migrate legacy fankt Room records by reading the source snapshot, atomically writing the destination payload with a completion marker, reading the destination back, verifying exact equality of `domain`, `path`, `name`, `value`, `expiresAtEpochMilliseconds`, `secure`, and `hostOnly`, switching the routing storage to the secure destination, and only then clearing, closing, and deleting the legacy source. A failure before verified destination commit SHALL retain and use the legacy source for that process and SHALL be retryable on a later launch.

#### Scenario: Successful upgrade preserves login
- **WHEN** a user with a valid legacy Room `FANBOXSESSID` launches the upgraded app
- **THEN** the first authenticated request uses the same session from the verified secure destination and the legacy source is cleared afterward

#### Scenario: Crash after secure commit
- **WHEN** the process stops after the destination payload and completion marker are committed but before legacy cleanup
- **THEN** the next launch selects the secure destination and retries legacy cleanup without overwriting the destination

#### Scenario: Secure write or verification fails
- **WHEN** destination write or read-back verification fails
- **THEN** the legacy source remains intact, the routing storage continues using it for that process, and migration is eligible for retry

#### Scenario: Concurrent Cookie access waits for routing decision
- **WHEN** a Cookie read or mutation overlaps first-launch migration
- **THEN** it waits for the serialized migration decision and is applied to the selected active backend rather than being lost between stores

#### Scenario: Destination records exist without completion marker
- **WHEN** a decryptable secure payload contains Cookie records but no migration-completion marker
- **THEN** migration preserves the destination records, does not overwrite them from legacy Room, and reports a destination-conflict failure

### Requirement: Legacy pre-Room import remains safe
The existing `OldCookieDataStore` one-way import SHALL run only after the routing storage is initialized, SHALL write its parsed `FANBOXSESSID` through the active injected storage, and SHALL clear its source only after the destination read-back confirms the same value.

#### Scenario: Very old installation is upgraded
- **WHEN** Room has no session but `OldCookieDataStore` contains a `FANBOXSESSID`
- **THEN** PixiView writes and verifies that session in the active secure storage before clearing the old preference

#### Scenario: Cookie value contains an equals sign
- **WHEN** the old Cookie text contains a value with `=` characters
- **THEN** import parsing preserves the complete value rather than truncating it

#### Scenario: Session Cookie is not the first header element
- **WHEN** the old Cookie text contains whitespace before a non-leading `FANBOXSESSID` element
- **THEN** import trims each element before splitting its name and value and finds the session Cookie

### Requirement: Logout cannot resurrect migrated credentials
Logout SHALL synchronously clear and verify the active secure Cookie storage and any uncleared legacy Room or pre-Room source before reporting completion or emitting the logout UI trigger. It SHALL remove the session rather than persist `FANBOXSESSID` with an empty value. WebView Cookie cleanup failure SHALL NOT prevent durable store cleanup. A later launch SHALL NOT restore a session from a stale migration source.

#### Scenario: Logout during cleanup grace period
- **WHEN** the user logs out while legacy cleanup is still pending
- **THEN** secure and legacy credential sources are cleared and the next launch remains logged out

#### Scenario: Logout after legacy deletion
- **WHEN** migration state records completed legacy deletion and the user logs out
- **THEN** logout clears the active secure and pre-Room sources without opening a legacy bridge or recreating `fankt.db`

### Requirement: Credential-safe migration observability
PixiView-KMP SHALL record migration started, succeeded, fallback-used, cleanup-pending, and failed outcomes without logging Cookie values, raw payloads, or stable credential-derived identifiers.

#### Scenario: Migration failure is reported
- **WHEN** migration falls back to the legacy backend
- **THEN** telemetry records the platform, app version, migration stage, and coarse error category without any Cookie value
