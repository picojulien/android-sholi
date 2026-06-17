# SPEC: WebDAV Synchronization with Safe Conflict Resolution

## 1. Overview

This section explains what the feature is meant to do and where its boundaries are.

ShoLi is an offline-first Android app for maintaining short shopping-style lists. This specification adds WebDAV-based synchronization so a user can keep the same list on multiple Android devices without silently losing local or remote changes.

The sync-capable version adds:

- Stable item synchronization identity through stored `sync_id` values.
- Local modification timestamps, modifier names, deletion tombstones, and sync bookkeeping.
- A canonical full-snapshot JSON document for remote list state.
- WebDAV settings, including URL, username, encrypted password or app token, remote file path, and device/user display name.
- Safe WebDAV upload and download using ETags or the safest available remote version marker.
- Three-way merge using the last successful sync baseline.
- Automatic merge for independent changes.
- Explicit user choice for conflicting concurrent item changes.
- Android 6.0/API 23 as the minimum supported Android version for the sync-capable release.

The first implementation does not include real-time collaboration, background scheduling, multi-user sharing semantics, arbitrary cloud providers beyond WebDAV, or insecure plaintext credential storage for older Android versions.

## 2. User Roles

This section lists the people or system actors named by the stories and what each one needs.

| Role | Description |
| --- | --- |
| ShoLi user | Uses ShoLi on one or more Android devices and needs list synchronization, safe merging, clear conflict resolution, and secure WebDAV configuration. |
| ShoLi synchronizer | The sync component that exports, imports, parses, validates, merges, uploads, and downloads canonical sync documents. |
| ShoLi maintainer | Maintains the Android app and needs secure credential storage, API level policy, testable abstractions, and release compatibility behavior. |

## 3. Functional Requirements

This section turns every accepted story into concrete behavior the implementation must provide.

### Epic: WebDAV Synchronization with Safe Conflict Resolution

#### Story #1: Add Local Synchronization Metadata to ShoLi Items

Priority: Must

As a ShoLi user, I want give each list item a stable sync identity, modification timestamp, and deletion metadata, so that future synchronization can compare local and remote changes safely.

Acceptance criteria:

- Given an existing ShoLi database contains items without sync metadata, when the database is upgraded, then each existing item receives a stable stored `sync_id`, initially derived from its canonical current name, and an initial local modification timestamp without changing its status.
- Given multiple existing items normalize to the same canonical name, when the database is upgraded, then migration keeps one deterministic survivor, removes duplicates, and when duplicates differ only by casing the surviving display name becomes the lowercase canonical name.
- Given a user adds, renames, checks, unchecks, removes, or restores an item, when ShoLi updates the local database, then the affected item records a fresh local modification timestamp.
- Given a user deletes or removes an item from the active list, when sync metadata is enabled, then ShoLi preserves enough tombstone or deletion metadata to propagate the deletion during synchronization.
- Given the database schema changes for synchronization, when an existing user upgrades the app, then their current list remains intact and the migration can be rerun safely without duplicating metadata.

#### Story #2: Define Canonical JSON Sync Document for ShoLi Lists

Priority: Must

As a ShoLi synchronizer, I want define a stable remote JSON representation of list state, so that different devices can exchange item identity, status, timestamps, and deletions consistently.

Acceptance criteria:

- Given the local database contains active and deleted sync-tracked items, when ShoLi exports a sync document, then the JSON includes `schema_version` plus each item `sync_id`, `name`, `status`, `deleted`, `modified_at`, and `modified_by.name`, with optional `client_id` for distinguishing same-named devices.
- Given ShoLi imports a valid sync document containing a `sync_id` already present locally, when the import is applied through sync logic, then the existing local item with the same `sync_id` is updated instead of creating a duplicate.
- Given ShoLi reads a sync document with an unsupported future document version, when the document is parsed, then ShoLi fails safely and leaves local data unchanged.
- Given a generated sync document is uploaded by one device, when another ShoLi device downloads and parses it, then item identity, status, timestamps, modifier name, and deletion state are preserved.

#### Story #3: Configure and Test a WebDAV Synchronization Endpoint

Priority: Must

As a ShoLi user, I want enter WebDAV connection settings, including a device/user display name, so that ShoLi knows where and how to synchronize my list.

Acceptance criteria:

- Given a user opens ShoLi settings, when they enter a WebDAV URL, username, password or app token, remote file path, and device/user display name, then ShoLi stores non-secret sync configuration and stores the password or app token only through encrypted credential storage.
- Given the configured WebDAV server is reachable with the provided credentials, when the user taps test connection, then ShoLi reports that the connection succeeds without modifying list data.
- Given the configured URL, credentials, or remote path are invalid, when the user taps test connection, then ShoLi reports a clear failure message and leaves existing sync metadata unchanged.
- Given sync configuration exists, when a synchronization run starts, then ShoLi uses the configured WebDAV endpoint and remote file path.

#### Story #4: Upload and Download ShoLi Sync Documents Through WebDAV Using ETags

Priority: Must

As a ShoLi user, I want detect remote WebDAV changes before upload, so that my device does not overwrite changes pushed by another device.

Acceptance criteria:

- Given no remote sync document exists at the configured WebDAV path, when the user synchronizes, then ShoLi creates the remote document from local list state and stores the resulting remote metadata.
- Given the remote document ETag matches the ETag recorded at the last successful sync, when ShoLi uploads local changes, then the upload succeeds conditionally and ShoLi stores the new ETag returned by the server.
- Given the remote document ETag differs from the ETag recorded at the last successful sync, when ShoLi attempts to upload local changes, then ShoLi does not overwrite the remote document and instead downloads the remote state for merge analysis.
- Given the WebDAV server does not provide usable ETags, when ShoLi synchronizes, then ShoLi falls back to the safest available remote timestamp or comparison mechanism and avoids blind overwrites.

#### Story #5: Merge Non-Conflicting Local and Remote List Changes

Priority: Must

As a ShoLi user, I want merge independent local and remote edits automatically, so that synchronization does not interrupt me when there is no real conflict.

Acceptance criteria:

- Given item A changed locally and item B changed remotely since the last successful sync, when synchronization runs, then ShoLi preserves both changes in the merged local and remote state.
- Given an item changed only locally since the last successful sync, when synchronization runs, then the local item version is included in the merged state.
- Given an item changed only remotely since the last successful sync, when synchronization runs, then the remote item version is applied locally.
- Given a non-conflicting merge succeeds, when synchronization completes, then ShoLi updates local sync metadata and records the latest remote ETag or equivalent remote version marker.

#### Story #6: Resolve Conflicting Concurrent Item Changes During Synchronization

Priority: Must

As a ShoLi user, I want choose the final value when the same item changed locally and remotely, so that I can avoid losing the version I care about.

Acceptance criteria:

- Given the same item changed locally and remotely after the last successful sync, when synchronization compares local and remote state, then ShoLi marks the item as conflicted instead of automatically choosing a winner.
- Given a conflicted item exists, when ShoLi presents the conflict, then the user can compare the local value, remote value, changed fields, timestamps, status or deletion state, and `modified_by.name` for both versions of that item.
- Given the user chooses the local version for a conflict, when synchronization resumes, then ShoLi keeps the local item data and prepares it for upload as the resolved version.
- Given the user chooses the remote version for a conflict, when synchronization resumes, then ShoLi applies the remote item data locally as the resolved version.
- Given one or more conflicts remain unresolved, when the synchronization run exits, then ShoLi does not upload a merged document and preserves the unresolved local state.

#### Story #7: Raise Minimum Android Version for Secure WebDAV Credential Storage

Priority: Must

As a ShoLi maintainer, I want raise the app minimum Android version to Android 6.0/API 23 for secure credential storage, so that WebDAV credentials can use AndroidX Security encrypted storage backed by Android Keystore.

Acceptance criteria:

- Given the project builds the app, when Gradle evaluates Android configuration, then `minSdkVersion` is 23.
- Given a user saves WebDAV credentials, when the app runs on a supported API 23+ device, then the password or app token is stored through AndroidX Security `EncryptedSharedPreferences`.
- Given credentials are stored for synchronization, when ShoLi writes logs, sync JSON, exported list data, or error messages, then the WebDAV password or app token is not included.
- Given a device runs Android API 14 through 22, when the user tries to install the new sync-capable ShoLi build, then the device is no longer supported by this app version.
- Given sync logic needs WebDAV credentials, when it requests credentials for a synchronization operation, then it uses a `CredentialStore` abstraction with an encrypted production implementation and a fake or in-memory test implementation.

## 4. Non-Functional Requirements

This section captures quality, safety, and operational constraints that are not single user actions but must still be implemented.

### Performance Requirements

- ShoLi must use a full-list snapshot JSON document for schema version 1.
- The v1 sync design must support short lists at the expected scale of hundreds of items.
- The v1 sync design is not required to optimize for tens of thousands of items.
- ShoLi must not synchronize item ordering as a separate semantic field unless an explicit order field is added.

### Security Requirements

- WebDAV passwords and app tokens must be stored only through AndroidX Security `EncryptedSharedPreferences` backed by Android Keystore.
- ShoLi must not write WebDAV passwords or app tokens to sync JSON, exported list data, logs, debug output, or user-facing error messages.
- ShoLi must not add a plaintext production credential storage fallback.
- Sync logic must access secrets through a `CredentialStore` abstraction.
- Production credential storage must be encrypted on supported API 23+ devices.
- UI copy should recommend app-specific tokens over account passwords.
- ShoLi must warn before accepting non-HTTPS WebDAV URLs.

### Reliability Requirements

- Existing supported-device data must be preserved during database migration.
- Database migration for sync metadata must be safe to rerun without duplicating metadata.
- `sync_id` values must remain stable across renames, status changes, imports, reordering, deletion, restoration, conflict resolution, and offline edits.
- ShoLi must fail safely when parsing invalid sync documents or unsupported future schema versions.
- ShoLi must use conditional upload semantics when WebDAV ETags are usable.
- ShoLi must avoid blind overwrites when ETags are weak, missing, or inconsistent.
- ShoLi must preserve local data and last successful sync metadata when synchronization fails before completion.
- ShoLi must update the stored sync baseline and remote version marker only after successful upload or successful pull-only sync.
- ShoLi must persist unresolved conflicts locally and must not upload a merged document until all conflicts are resolved.
- If encrypted credential storage initialization fails or Android Keystore data becomes unavailable, ShoLi must disable sync, preserve non-secret configuration, clear or ignore unusable secrets, and ask the user to re-enter credentials.

### Usability Requirements

- Test connection must report clear success or failure without modifying list data.
- WebDAV endpoint validation must report clear errors for authentication failure, missing collection or path, unsupported WebDAV behavior, lack of write permission, and network timeout.
- Conflict presentation must show local and remote values, changed fields, timestamps, status or deletion state, and modifier names.
- When conflicts exist, the user must be able to choose either the complete local item version or the complete remote item version.
- Release notes must document that the sync-capable release requires Android 6.0/API 23 or newer.

## 5. Quality Gates

This section defines measurable checks that show whether the implementation satisfies the specification.

| Quality gate | What is measured | Target value | How to verify |
| --- | --- | --- | --- |
| Minimum Android version | Gradle Android configuration | `minSdkVersion` equals `23` | Inspect Gradle configuration in build tests or CI. |
| Existing data migration | Existing items after upgrade | All pre-existing items remain available with status unchanged, except deterministic duplicate removal for canonical-name duplicates | Run migration tests using pre-sync database fixtures. |
| Stable `sync_id` generation | Stored item identity | `sync_id` is `sha256("sholi:item:v1:" + canonical_item_name)` on initial migration/import and remains unchanged after later item edits | Unit test canonicalization, hashing, migration, rename, status change, import, reorder, delete, and restore paths. |
| Idempotent migration | Repeated database migration behavior | Running migration again does not create duplicate metadata or alter already migrated identities | Run migration twice on the same fixture and compare results. |
| Tombstone retention | Deleted-item metadata | Tombstones contain at least `sync_id`, `deleted=true`, and deletion `modified_at`; tombstones are kept until included in at least one successful sync and older than 30 days | Unit test deletion, successful sync marking, and retention cleanup. |
| JSON schema validation | Sync document parser | Missing or invalid required v1 fields fail safely with no local data changes | Parser tests with invalid documents and database state assertions. |
| Future schema handling | Unsupported `schema_version` | Parser fails safely and leaves local data unchanged | Parser test with future schema version. |
| Credential storage | Secret persistence | WebDAV password or app token is stored through encrypted production credential storage on API 23+ | Instrumented Android test or implementation-level test using `CredentialStore`. |
| Secret exclusion | Logs, sync JSON, exports, and errors | Password or app token never appears | Automated tests that serialize sync JSON, exports, logs, and error messages after saving credentials. |
| WebDAV connection test | Endpoint validation | Valid endpoint succeeds; invalid endpoint reports clear failure and does not modify list data | Integration tests with a WebDAV test server or mock server. |
| Conditional create | New remote file creation | Upload uses `If-None-Match: *` and does not overwrite a concurrently created remote file | WebDAV client tests against mocked responses. |
| Conditional update | Existing remote file update | Upload uses `If-Match` with the last seen ETag when ETag is usable | WebDAV client tests asserting request headers. |
| Precondition failure recovery | HTTP 412 response handling | ShoLi treats 412 as concurrent remote modification, downloads latest remote state, and does not blindly overwrite | WebDAV sync flow tests. |
| Safe fallback without ETags | Missing, weak, or inconsistent ETags | ShoLi downloads and compares remote document with baseline before upload; if no safe check exists, explicit user confirmation is required before replacement | Sync flow tests using mocked WebDAV metadata. |
| Non-conflicting merge | Independent local and remote changes | Both changes appear in the merged local and remote state | Three-way merge unit tests. |
| Conflict detection | Same field changed differently on both sides | Item is marked conflicted and no automatic winner is chosen | Three-way merge unit tests. |
| Conflict persistence | App close or connectivity loss during conflict | Conflict records survive and upload remains blocked until resolution | Persistence tests for conflict metadata. |
| Baseline update timing | Stored baseline and remote version marker | Updated only after successful upload or successful pull-only sync | Sync transaction tests with injected failures. |

## 6. Decisions Log

This section records decisions already made in the accepted stories, including why they were chosen.

### Deterministic Item Identity

Context: ShoLi needs stable item identities that can converge across devices when the same item is created independently.

Decision: Compute initial `sync_id` as `sha256("sholi:item:v1:" + canonical_item_name)`, where the canonical name is trimmed, repeated whitespace is collapsed, and text is lowercased with a stable locale. Store the `sync_id` and keep it stable after assignment.

Alternatives considered: Random UUIDs.

Rationale: A deterministic name-derived identity lets two devices that independently create the same item name produce the same sync identity and merge as one item.

### Duplicate Handling During Migration

Context: Existing databases may contain multiple items that normalize to the same canonical name.

Decision: Migration keeps one deterministic survivor and removes duplicates. If duplicates differ only by casing, the survivor’s display name becomes the lowercase canonical name.

Alternatives considered: Keep all duplicates with separate identities.

Rationale: Keeping duplicates with the same canonical identity would break sync identity uniqueness.

### Timestamp Interpretation

Context: Device clocks can differ or move backward.

Decision: Store `modified_at` as UTC epoch milliseconds from the device that made the change. Do not use timestamps to automatically resolve conflicts.

Alternatives considered: Last-write-wins conflict resolution based only on timestamps.

Rationale: Timestamp-only resolution can lose data when clocks are wrong. Baseline comparison and remote version checks are safer.

### Deletion Tombstones

Context: A deleted item must not be resurrected by an older remote document.

Decision: Represent deletions as tombstones containing at least `sync_id`, `deleted=true`, and deletion `modified_at`. Keep tombstones until included in at least one successful sync and older than 30 days.

Alternatives considered: Immediate hard delete with no sync metadata.

Rationale: Tombstones allow deletion propagation and prevent older live versions from being treated as new items.

### Sync Document Schema

Context: Devices need a stable remote data format.

Decision: Use top-level integer `schema_version`, starting at `1`. Schema v1 item fields are `sync_id`, `name`, `status`, `deleted`, `modified_at`, and `modified_by.name`; `client_id` is optional.

Alternatives considered: Unversioned JSON or local database export format.

Rationale: Explicit schema versioning allows safe parsing and future compatibility rules.

### Full-Snapshot Remote Document

Context: The first implementation must be simple and inspectable.

Decision: Use a full-list snapshot JSON document for v1.

Alternatives considered: Append-only change log or segmented format.

Rationale: ShoLi is intended for short lists at a scale of hundreds of items, so snapshots are adequate and simpler.

### Semantic Sync Fields Only

Context: Including local UI or platform state could cause unnecessary conflicts.

Decision: Sync only semantic item fields and modifier metadata needed for conflict display. Exclude local database IDs, UI-only state, adapter/cache state, and Android internals.

Alternatives considered: Syncing the full local database model.

Rationale: A smaller semantic contract reduces unnecessary conflicts and implementation coupling.

### Encrypted Credential Storage

Context: WebDAV credentials must be stored safely.

Decision: Raise the sync-capable release to Android 6.0/API 23 and store secrets with AndroidX Security `EncryptedSharedPreferences` backed by Android Keystore.

Alternatives considered: Plaintext preferences or insecure fallback for older Android versions.

Rationale: Secret material must not be stored in plaintext, and API 23+ supports the chosen secure storage approach.

### WebDAV Endpoint Validation

Context: Users need to know whether their sync settings work before sync runs.

Decision: Test connection validates URL syntax, warns on non-HTTPS URLs, authenticates, checks the parent collection, and verifies read/write capability with safe WebDAV probes where feasible.

Alternatives considered: Save settings without validation.

Rationale: Early validation gives clear feedback and avoids unsafe sync attempts.

### Conditional WebDAV Uploads

Context: ShoLi must not overwrite remote changes from another device.

Decision: Use `PUT` with conditional headers. Use `If-None-Match: *` when creating a new file and `If-Match` with the last seen ETag when updating an existing file.

Alternatives considered: Plain unconditional `PUT`.

Rationale: Conditional writes prevent accidental overwrites when the remote file changed concurrently.

### Fallback When ETags Are Not Usable

Context: Some WebDAV servers may omit, weaken, or inconsistently return ETags.

Decision: Download the current remote document, compare with the last sync baseline, and only upload when the merge result is known safe. If no safe remote-version check exists, require explicit user confirmation before replacing remote data.

Alternatives considered: Blind upload without ETag protection.

Rationale: The implementation must fail safely rather than silently replacing remote changes.

### Staged Synchronization

Context: Sync can fail after some network steps complete.

Decision: Preserve local data and last successful sync metadata until the whole operation succeeds. Update stored ETag and baseline only after successful upload or successful pull-only sync.

Alternatives considered: Update metadata after each partial step.

Rationale: Staging avoids corrupting sync state after partial failures.

### Three-Way Merge Baseline

Context: ShoLi needs to distinguish independent edits from conflicts.

Decision: Store the last successfully synchronized canonical JSON document as the baseline with the remote ETag or equivalent version marker. Compare baseline, current local state, and current remote state by `sync_id`.

Alternatives considered: Two-way local-versus-remote comparison.

Rationale: Three-way comparison can tell whether each field changed on one side or both sides.

### Ordering Scope

Context: Independent reorder operations can create conflicts.

Decision: Do not treat ordering as a separately synchronized semantic field in v1 unless ShoLi explicitly adds an order field.

Alternatives considered: Always sync list order.

Rationale: If current UI sorts by status or name, syncing order would create unnecessary conflicts.

### Conflict Resolution Granularity

Context: Concurrent changes to the same item need user choice.

Decision: Resolve conflicts per item in v1. The user chooses the complete local item version or complete remote item version.

Alternatives considered: Per-field conflict resolution.

Rationale: Per-item resolution is simpler for the first implementation while still preventing silent data loss.

### Persistent Conflict Records

Context: The app may close or lose connectivity before conflict resolution.

Decision: Store unresolved conflicts in persistent sync metadata separate from the active item table. Active local list data remains unchanged until the user resolves conflicts.

Alternatives considered: Keep conflicts only in memory.

Rationale: Persistent records prevent unresolved conflicts from being lost and block unsafe uploads.

### Android Compatibility Rollout

Context: The sync-capable release raises the minimum Android version.

Decision: Devices running Android API 14 through 22 cannot install the sync-capable version. Release notes document the break, and stores that support version targeting should keep the previous non-sync APK available.

Alternatives considered: Add insecure credential fallback for old devices.

Rationale: Security requirements take priority over preserving installability on unsupported Android versions.

### Credential Store Abstraction

Context: Sync logic should be testable and not tightly coupled to AndroidX Security APIs.

Decision: Use a `CredentialStore` abstraction with encrypted production implementation and fake or in-memory test implementations.

Alternatives considered: Direct dependency on AndroidX Security from sync logic.

Rationale: The abstraction supports tests and future storage changes while preserving encrypted production storage.

## 7. Open Questions

This section lists unresolved issues that still need an answer before implementation.

No unresolved challenges remain in the accepted stories. All architect challenges provided in the story set have corresponding decisions.

## 8. Glossary

This section defines technical terms used in the specification. Links point to stable public references.

| Term | Definition |
| --- | --- |
| Android API 23 | Android platform API level for Android 6.0 Marshmallow. ShoLi’s sync-capable release requires API 23 or newer. Reference: [Android 6.0 APIs](https://developer.android.com/about/versions/marshmallow/android-6.0). |
| Android Keystore | Android system for storing cryptographic keys so apps can use them without directly exposing key material. Reference: [Android Keystore system](https://developer.android.com/privacy-and-security/keystore). |
| AndroidX Security `EncryptedSharedPreferences` | AndroidX API for storing key-value data in encrypted shared preferences. ShoLi uses it for WebDAV passwords or app tokens. Reference: [EncryptedSharedPreferences](https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences). |
| App token | A password-like secret created for a specific app instead of using the user’s main account password. |
| Baseline | The last successfully synchronized canonical JSON document stored locally. ShoLi compares the baseline with current local and remote state to detect changes and conflicts. |
| Canonical item name | A normalized item name used for deterministic identity creation. In this spec, it is trimmed, repeated whitespace is collapsed, and text is lowercased with a stable locale. |
| Client ID | Optional stable identifier used to distinguish devices that have the same visible device/user display name. |
| Conditional upload | An HTTP upload that only succeeds if a condition is true, such as matching a known ETag. References: [RFC 9110 HTTP Semantics](https://www.rfc-editor.org/rfc/rfc9110), [RFC 4918 WebDAV](https://www.rfc-editor.org/rfc/rfc4918). |
| Conflict | A case where the same semantic item field changed differently in local and remote state after the last successful sync. |
| `CredentialStore` | ShoLi abstraction used by sync logic to read or write WebDAV credentials without depending directly on Android platform security APIs. |
| ETag | HTTP entity tag used as a validator for a specific version of a remote resource. ShoLi uses ETags to avoid overwriting changed remote sync documents. Reference: [RFC 9110 HTTP Semantics](https://www.rfc-editor.org/rfc/rfc9110). |
| Full-list snapshot | A sync document containing the complete current sync state of active items and tombstones, rather than only a log of changes. |
| JSON | Text data format used for ShoLi’s canonical sync document. Reference: [RFC 8259 JSON](https://www.rfc-editor.org/rfc/rfc8259). |
| `modified_at` | UTC epoch millisecond timestamp recorded when an item changes locally. It is shown during conflict resolution but is not used by itself to automatically choose a winner. |
| `modified_by.name` | User-visible device or user display name that last modified an item. |
| PROPFIND | WebDAV method used to retrieve properties for a remote resource or collection. ShoLi uses it during endpoint validation and remote metadata checks. Reference: [RFC 4918 WebDAV](https://www.rfc-editor.org/rfc/rfc4918). |
| Remote collection | WebDAV folder-like location that contains the configured ShoLi sync document path. |
| Remote version marker | Metadata, such as an ETag or safest available fallback, used to identify the remote sync document version seen at last successful sync. |
| Schema version | Integer field in the sync JSON document that identifies the document format. Schema v1 starts with `schema_version: 1`. |
| SHA-256 | Cryptographic hash function used here to derive deterministic item `sync_id` values from canonical item names. Reference: [SHA-2](https://en.wikipedia.org/wiki/SHA-2). |
| `sync_id` | Stable stored item identifier used to match the same item across local and remote state. |
| Tombstone | Deletion metadata for a removed item. It preserves at least `sync_id`, `deleted=true`, and deletion `modified_at` so deletion can sync safely. |
| Three-way merge | Merge process that compares baseline, local state, and remote state to detect independent changes and conflicts. |
| WebDAV | HTTP extension protocol for remote file management. ShoLi uses it to store and retrieve the sync JSON document. Reference: [RFC 4918 WebDAV](https://www.rfc-editor.org/rfc/rfc4918). |
