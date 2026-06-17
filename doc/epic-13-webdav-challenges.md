# Epic #13 — WebDAV synchronization with safe conflict resolution

## Story Challenges and Answers

## Story #1 — Add local synchronization metadata to ShoLi items

### Challenge #1
**Role:** architect

**Question:**  
What exact identifier format will be assigned to each item, and when is it generated so that IDs remain stable across renames, reordering, imports, and offline edits?

**Answer:**  
Use a deterministic name-derived sync ID instead of a random UUID. Compute the initial sync_id as sha256("sholi:item:v1:" + canonical_item_name), where canonical_item_name is a stable normalized form of the item name, such as trimmed, repeated whitespace collapsed, and lowercased with a stable locale. This lets two devices that concurrently create the same item name produce the same sync identity and merge as one item. Once assigned, store the sync_id and keep it stable across renames, status changes, imports, reordering, and offline edits. Existing items receive their initial sync_id during schema migration from their current canonical name. If migration finds multiple existing items with the same canonical_item_name, it keeps one deterministic survivor, removes the duplicates, and when the conflict is only casing the surviving display name becomes the lowercase canonical name. If legacy imported data has no sync_id, generate it from the imported canonical name at the import boundary.

### Challenge #2
**Role:** architect

**Question:**  
How will modification timestamps be interpreted when device clocks differ or move backward?

**Answer:**  
Store modified_at as UTC epoch milliseconds from the device that made the change. Do not use timestamps to automatically resolve conflicts. Use baseline comparison plus WebDAV ETags or equivalent remote version checks to determine whether concurrent changes occurred. When changes do not conflict, the merged item keeps the timestamp of the selected changed value, or the maximum timestamp when independent fields are merged. When changes conflict, display both local and remote values with their respective modification timestamps and require the user to choose the final version.

### Challenge #3
**Role:** architect

**Question:**  
What is the retention policy for deletion metadata, and how will a deleted item avoid being resurrected by an older remote document?

**Answer:**  
Represent deletions as tombstones containing at least sync_id, deleted=true, and deletion modified_at. Keep tombstones until they have been included in at least one successful sync and are older than a conservative retention window such as 30 days. During merge, a tombstone prevents an older live version of the same sync_id from being treated as a new item. If a live remote version conflicts with a local tombstone, or vice versa, ShoLi must show both versions and timestamps to the user instead of resurrecting or deleting automatically.

## Story #2 — Define canonical JSON sync document for ShoLi lists

### Challenge #4
**Role:** architect

**Question:**  
What schema versioning and compatibility rules will the canonical JSON document use when future ShoLi versions add fields?

**Answer:**  
Use a top-level integer schema_version, starting at 1. Schema v1 required item fields are sync_id, name, status, deleted, modified_at, and modified_by.name. ShoLi may ignore unknown optional fields within a supported schema version. If any required v1 field is missing or invalid, parsing fails safely. If ShoLi sees an unsupported future schema_version, it must fail safely without modifying local data. Future versions should add optional fields where possible; incompatible changes require an explicit migration strategy.

### Challenge #5
**Role:** architect

**Question:**  
Is the remote JSON document a full list snapshot or an append-only change log, and what size of list is this expected to support before sync becomes slow?

**Answer:**  
Use a full-list snapshot for the first implementation. ShoLi is designed for short lists, so a complete JSON snapshot is simpler, inspectable, and adequate. The expected scale is hundreds of items, not tens of thousands. If snapshot size or sync time becomes a real issue later, a future schema version can introduce a change-log or segmented format without changing the v1 safety rules.

### Challenge #6
**Role:** architect

**Question:**  
Which fields are part of the canonical sync contract, and are presentation-only local fields excluded to avoid unnecessary conflicts?

**Answer:**  
Schema v1 includes semantic sync fields only. The user-visible device or user name that last modified an item is semantic because it is displayed during conflict resolution. Each item includes sync_id, name, status, deleted, modified_at, and modified_by.name; it may also include a stable client_id to distinguish devices with the same display name. modified_by.name is configured locally and updated whenever the item changes locally. Conflict UI must show both versions with changed fields, timestamps, and modified_by.name. Local database IDs, UI-only state, adapter/cache state, and Android-specific internals are excluded from the sync contract.

## Story #3 — Configure and test a WebDAV synchronization endpoint

### Challenge #7
**Role:** architect

**Question:**  
Where will WebDAV credentials be stored, and what prevents another local process or exported settings file from exposing them?

**Answer:**  
ShoLi will raise its minimum supported Android version to Android 6.0/API 23 for the sync-capable version. WebDAV passwords or app tokens must be stored with AndroidX Security EncryptedSharedPreferences backed by Android Keystore, following the same credential-storage direction used by DAVx5 WebDAV mounts. URL, username, remote path, and device/user display name may be stored in app-private settings, but the secret token/password must be encrypted. Credentials must never be written to sync JSON, exported list data, logs, debug output, or user-facing error messages. UI copy should recommend app-specific tokens over account passwords.

### Challenge #8
**Role:** architect

**Question:**  
What WebDAV endpoint validation will run before saving settings, especially for unsupported servers, wrong paths, missing write access, or non-HTTPS URLs?

**Answer:**  
When the user taps test connection, ShoLi validates URL syntax, warns before accepting non-HTTPS URLs, authenticates with the server, checks that the parent remote collection exists, and verifies read/write capability with safe WebDAV probes: PROPFIND on the collection and a temporary write/read/delete check where feasible. It reports clear errors for authentication failure, missing collection or path, unsupported WebDAV behavior, lack of write permission, and network timeout. Settings may be saved after a failed test only if the user explicitly confirms, but synchronization must not run automatically until a successful test exists or the user explicitly retries.

## Story #4 — Upload and download ShoLi sync documents through WebDAV using ETags

### Challenge #9
**Role:** architect

**Question:**  
Which conditional WebDAV operation will be used for upload, and how will the app handle servers that omit ETags or return weak/inconsistent ETags?

**Answer:**  
Use HTTP/WebDAV PUT with conditional headers. When creating a new remote sync file, use If-None-Match: * so ShoLi does not overwrite a file created concurrently. When updating an existing remote sync file, use If-Match with the last seen remote ETag so the upload succeeds only if the remote file is still the version ShoLi last synced. Before upload, ShoLi may use HEAD or PROPFIND to read current ETag and Last-Modified metadata. Strong ETags are preferred. Weak or missing ETags are treated as less reliable. If the server omits ETags or returns inconsistent ETags, ShoLi must avoid blind overwrite: download the current remote document, compare it with the last sync baseline, and only upload if the merge result is known safe. If no safe remote-version check is available, require explicit user confirmation before replacing remote data.

### Challenge #10
**Role:** architect

**Question:**  
What is the retry and recovery behavior if download succeeds but upload fails, or if the server returns 412 Precondition Failed?

**Answer:**  
Synchronization is staged and must preserve local data until the whole operation succeeds. If download succeeds but upload fails because of network or server error, keep local data and last successful sync metadata unchanged, report the sync failure, and allow retry. If upload returns 412 Precondition Failed, treat it as a concurrent remote modification: download the latest remote document again, compare it with local state and baseline, and either merge non-conflicting changes or surface conflicts to the user. If the merge is non-conflicting, retry upload once with the new ETag. If conflicts exist, stop without uploading and show conflict resolution. Only update the stored last remote ETag and sync baseline after a successful upload or successful pull-only sync.

## Story #5 — Merge non-conflicting local and remote list changes

### Challenge #11
**Role:** architect

**Question:**  
What baseline will be used to distinguish independent edits from conflicting edits during merge?

**Answer:**  
Store the last successfully synchronized canonical JSON document as the sync baseline, together with the remote ETag or equivalent remote version marker. During sync, compare three versions by sync_id: baseline, current local state, and current remote state. A field changed on only one side from the baseline is non-conflicting. The same field changed differently on both sides is a conflict. New items, deleted tombstones, and unchanged items are evaluated with the same three-way comparison. Update the baseline only after synchronization completes successfully.

### Challenge #12
**Role:** architect

**Question:**  
How will ordering changes merge when two devices independently reorder, insert, or delete items?

**Answer:**  
For the first sync version, item ordering is not treated as a separately synchronized semantic field unless ShoLi explicitly adds an order field. If order is required, store a canonical position field per item and include it in the same three-way merge rules: changed only locally means keep local order, changed only remotely means apply remote order, and changed differently on both sides means conflict. Deleted items and tombstones are excluded from visible ordering. If ShoLi's current UI sorts by status or name rather than explicit user order, the sync document should omit order to avoid unnecessary conflicts.

## Story #6 — Resolve conflicting concurrent item changes during synchronization

### Challenge #13
**Role:** architect

**Question:**  
What exact item fields can conflict, and will conflict resolution operate per item or per field?

**Answer:**  
In schema v1, conflicts can occur on semantic item fields: name, status, deleted, and optionally position if explicit ordering is added. Metadata fields such as modified_at and modified_by.name are displayed as evidence but are not independently resolved. Conflict resolution operates per item for the first implementation: the user chooses the complete local item version or complete remote item version. Non-conflicting fields may be pre-merged before presentation, but if any semantic field conflicts, ShoLi presents the item as a conflict with both versions and their timestamps and modified-by names.

### Challenge #14
**Role:** architect

**Question:**  
Where will unresolved conflicts be stored if the user closes the app or loses connectivity before choosing a final value?

**Answer:**  
Store unresolved conflicts locally in persistent sync metadata, separate from the active item table. Each conflict record should include the sync_id, baseline item snapshot, local item snapshot, remote item snapshot, remote ETag or version observed during conflict detection, conflict timestamp, and conflict status. The active local list remains unchanged until the user resolves conflicts. If the app closes or connectivity is lost, ShoLi reloads pending conflicts on next launch or sync and does not upload a merged document until all conflicts are resolved.

## Story #7 — Raise minimum Android version for secure WebDAV credential storage

### Challenge #15
**Role:** architect

**Question:**  
What is the rollout policy for users on Android 5.x/API 21-22 once minSdk is raised to API 23, including whether they receive a final compatible release or lose update access immediately?

**Answer:**  
The sync-capable release raises minSdkVersion to API 23. Users on API 14 through 22 will not be able to install this version. The project should document this compatibility break in release notes and, if publishing through stores that support version targeting, keep the previous non-sync-capable APK available as the final compatible release for older Android devices. No insecure credential fallback is added solely to preserve API 14 through 22 support.

### Challenge #16
**Role:** architect

**Question:**  
How will existing WebDAV credentials be migrated into encrypted storage, and what happens if migration fails or the Android Keystore entry becomes unavailable?

**Answer:**  
There are no existing WebDAV credentials before this feature ships, so the initial implementation does not need to migrate legacy WebDAV secrets. If future versions ever change credential storage, migration must read the old credential once, write it to encrypted storage, verify it can be read back, then delete the old secret. If encrypted storage initialization fails or Android Keystore data becomes unavailable, ShoLi must disable sync, preserve non-secret sync configuration, clear or ignore unusable secrets, and ask the user to re-enter credentials. It must not fall back to plaintext secret storage.

### Challenge #17
**Role:** architect

**Question:**  
Is AndroidX Security encrypted storage the only credential persistence mechanism, or do we need an abstraction so WebDAV auth can be tested and replaced without coupling sync logic to Android platform APIs?

**Answer:**  
Use AndroidX Security EncryptedSharedPreferences as the production secret store, but hide it behind a small CredentialStore abstraction. Sync logic should request credentials through this abstraction rather than directly depending on AndroidX Security APIs. Tests can use an in-memory or fake credential store. This also leaves room for future storage changes without rewriting WebDAV sync behavior. The production implementation for supported devices must remain encrypted; no plaintext production implementation is allowed.
