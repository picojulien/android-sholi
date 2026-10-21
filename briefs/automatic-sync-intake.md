# Intake Brief — automatic-sync

**Date:** 2026-06-06
**Status:** VALIDATED
**Type:** feature

## Goal

Add foreground automatic WebDAV synchronization so an open sholi-dav client detects changes pushed to the server by another device and synchronizes without requiring the user to tap *Sync*. This should make multi-device usage feel faster while preserving the existing safe sync semantics: no blind overwrites, unresolved conflicts still surface to the user, credentials remain redacted/encrypted, and the SQLite database remains the source of truth.

The proposed behavior is: when the main list UI is visible, start an immediate automatic sync on open/resume, then continue a periodic foreground auto-sync tick using the existing `AndroidWebDavSyncRunner.synchronizeOrResume()` path. The tick must avoid overlapping sync runs, refresh the visible list after successful pulls/merges/uploads, stay quiet for `UP_TO_DATE` results to avoid toast spam, and show the existing conflict UI when conflicts are detected. Manual *Sync* remains available and should continue to show explicit feedback.

## Scope Boundary

What is explicitly OUT of scope:
- Background sync when the app is closed, stopped, or only in the background.
- Real-time push notifications or server-side subscriptions; WebDAV has no push channel here, so detection is foreground polling.
- Changing the WebDAV sync document schema, item identity namespace, database name, package namespace, or application id.
- Adding new credential storage mechanisms or plaintext credential handling.
- Bypassing existing ETag, merge, stale-check, and conflict-resolution safety rules.
- Syncing while a conflict dialog is already open beyond the existing stale-aware conflict resolution flow.
- Adding a user-facing poll interval setting unless implementation needs it; use a single conservative code default first.

## Relevant Files

| File | Role | Key snippet |
|---|---|---|
| `sholi/src/main/java/name/soulayrol/rhaa/sholi/MainActivity.java` | Manual sync entry point and UI result handling; likely host for foreground auto-sync lifecycle. | `case R.id.action_sync: startSynchronization();` / `new AndroidWebDavSyncRunner(MainActivity.this).synchronizeOrResume();` / `if (result.isSuccess()) { refreshVisibleList(); }` |
| `sholi/src/main/java/name/soulayrol/rhaa/sholi/AbstractListFragment.java` | Existing explicit list refresh API after sync changes DB state. | `public void refreshList() { if (!isAdded() || getActivity() == null || _session == null || _adapter == null) { return; } _session.clear(); _adapter.setLazyList(createList(getActivity())); }` |
| `sholi/src/main/java/name/soulayrol/rhaa/sholi/SyncConflictDialogFragment.java` | Existing conflict UI and resolved-conflict sync callback; automatic sync must reuse conflict display behavior. | `result = runner.synchronizeOrResume();` / `if (result.getStatus() == WebDavSyncResult.Status.CONFLICTS) { ... showNext(...) }` / `if (result.isSuccess()) { refreshVisibleList(activity); }` |
| `sholi/src/main/java/name/soulayrol/rhaa/sholi/sync/android/AndroidWebDavSyncRunner.java` | Android-facing sync facade that opens stores, loads endpoint credentials, and runs normal/resolved sync. | `public WebDavSyncResult synchronizeOrResume() { ... new ResolvedConflictAwareSyncRunner(...).synchronizeOrResume(); }` |
| `sholi/src/main/java/name/soulayrol/rhaa/sholi/sync/orchestration/WebDavSyncController.java` | Sync orchestration gate that blocks unresolved conflicts and creates the WebDAV engine. | `if (SyncStateRecorder.hasUnresolvedConflicts(metadataStore)) { return WebDavSyncResult.conflicts(conflicts); }` / `return engineFactory.create(endpoint, localStore, metadataStore).synchronize(conflictTimestamp);` |
| `sholi/src/main/java/name/soulayrol/rhaa/sholi/sync/webdav/WebDavClient.java` | Existing WebDAV HEAD/GET/PUT client; periodic sync can rely on current engine’s initial HEAD and safe conditional writes. | `public WebDavMetadataResult head(String url)` / `public WebDavGetResult get(String url)` / `public WebDavPutResult putIfMatch(...)` |
| `sholi/src/main/res/xml/preferences.xml` | Existing settings screen; no automatic sync preference currently exists. | `<PreferenceCategory android:title="@string/settings_category_webdav_sync" ...>` with URL, username, token, remote path, display name, test connection. |
| `doc/manual.md` | User documentation currently states sync is manual; must be updated. | `Synchronization is manual and offline-first... choose the Sync entry from the main menu.` |
| `tests/story8/src/SyncOrchestrationStoryTest.java` | Existing JDK story/source assertions for Android sync orchestration and UI wiring; likely place for automatic sync source regressions. | `verifySyncMenuAndConflictUiSources()` reads MainActivity, conflict dialog, resources, and asserts sync UI wiring. |
| `tests/story4/src/WebDavSyncStoryTest.java` | Existing WebDAV sync behavior tests; should remain green and may cover any detector/client changes if added. | Story #4 covers ETag-safe WebDAV upload/download, conflicts, unsafe markers, and diagnostics. |

## Architecture Notes

- Current synchronization is manual and already safe. Automatic sync should call the same `AndroidWebDavSyncRunner.synchronizeOrResume()` path rather than adding a parallel sync engine.
- The foreground auto-sync controller should be explicit and lifecycle-aware: start from `MainActivity.onResume()`/after initial UI setup, stop in `onPause()`/`onDestroy()`, and never run overlapping network threads.
- A periodic full sync tick is acceptable because `WebDavSyncEngine` starts with metadata (`HEAD`) and quickly returns `UP_TO_DATE` when there is no work. This also lets local-only changes upload automatically under the same ETag safety rules instead of implementing separate dirty tracking.
- Automatic sync result UX should differ from manual sync: automatic `UP_TO_DATE` should be silent; successful changes should refresh the visible list and may use minimal feedback; conflicts should use the existing dialog; configuration/auth/network/server errors should avoid repeated toast spam.
- Because WebDAV has no real push mechanism in this codebase, “server sync detected” means detected by foreground polling. A conservative initial interval such as 30 seconds is proposed, plus an immediate sync on resume/open. The interval should be a named constant and covered by tests/source assertions.
- Tests should remain JDK-only where possible: add source-level story8 checks for lifecycle start/stop, no-overlap guard, silent up-to-date handling, conflict UI reuse, visible-list refresh, and use of `AndroidWebDavSyncRunner.synchronizeOrResume()`.
- Documentation must update `README.md`/`doc/manual*.md` to replace “manual only” with foreground automatic behavior while preserving offline-first and safety language.

## Quality Gates

```bash
# Build
JAVA_HOME=$PWD/env/java11 ANDROID_HOME=$PWD/env/android-sdk GRADLE_USER_HOME=$PWD/.gradle-home GRADLE_OPTS='-Dorg.gradle.native=false' ./env/gradle/bin/gradle :sholi:assembleDebug

# Tests
tests/run-all.sh

# Lint/Format
git diff --check
```

## Open Questions

_(empty if everything is resolved)_
