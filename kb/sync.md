# WebDAV Sync Knowledge

## Current sync model

- Runtime sync entry point is `name.soulayrol.rhaa.sholi.sync.android.AndroidWebDavSyncRunner`.
- The Android runner must reuse `synchronizeOrResume()` so normal sync and resolved-conflict resume share the same safe path.
- Core safety remains in `WebDavSyncEngine`, `SyncMerger`, and `ResolvedConflictSyncService`:
  - use WebDAV ETags / `If-Match` when possible;
  - never blind-overwrite remote data;
  - use baseline + local + remote three-way merge;
  - persist unresolved conflicts instead of silently choosing a side.
- SQLite remains the source of truth for local state. UI refreshes must reload through the data layer, not cache assumptions.

## Compatibility constraints

- Java package/class namespace stays `name.soulayrol.rhaa.sholi.*`.
- Fork Android application id is `io.github.picojulien.sholidav`.
- User-facing app label is `sholi-dav`.
- Existing sync identifiers and document format are preserved:
  - sync ID namespace: `sholi:item:v1:`;
  - WebDAV sync JSON schema remains the v1 full-list snapshot.
- Do not edit greenDAO generated files in `src-gen` manually.

## Secrets and diagnostics

- Never log, export, serialize, or show WebDAV passwords/tokens in plaintext.
- Diagnostics may include ETags, but must not include URLs with credentials or authorization headers.
- `WebDavSafeText`, credential redaction helpers, and story assertions protect secret-bearing output.

## Nextcloud / gzip ETag handling

- Some Nextcloud responses may expose strong ETags ending in `-gzip`.
- `WebDavEtag.classify(...)` canonicalizes strong quoted gzip variants, e.g. `"v1-gzip"` → `"v1"`.
- `WebDavClient.putIfMatch(...)` must send the classified/canonical ETag value, not the raw marker.
- WebDAV client default headers request `Accept-Encoding: identity` unless the caller supplies an explicit value.
- Regression coverage lives mainly in `tests/story4/src/WebDavSyncStoryTest.java` and `tests/story8/src/SyncOrchestrationStoryTest.java`.

## Conflict UI and resolved-conflict sync

- Conflict UI presents all unresolved conflicts in one dialog with local/remote columns.
- Present-only sides are auto-selectable; missing sides must not be selectable.
- Confirm is enabled only when every row has a valid choice.
- Choices are saved atomically through `ResolvedConflictSyncService.chooseAll(...)`.
- After choices are saved, the UI immediately resumes sync via the stale-aware runner.
- `ResolvedConflictAwareSyncRunner` falls back to normal sync when stored resolved conflicts are stale, preserving diagnostics.
- Successful resolved upload records the resolved document as baseline and clears conflicts before any later normal sync.

## UI refresh after sync

- A successful sync can update SQLite without triggering fragment lifecycle callbacks.
- `AbstractListFragment.refreshList()` explicitly clears the DAO session and resets the lazy list.
- `MainActivity` refreshes the visible list after successful manual/automatic sync results.
- `SyncConflictDialogFragment` refreshes the visible list after successful resolved-conflict sync.
- Refresh calls must guard detached/destroyed fragments and null lazy lists to avoid lifecycle crashes.

## Foreground automatic sync

- Foreground automatic sync is intentionally not background sync.
- `ForegroundWebDavAutoSyncController` is responsible only for foreground scheduling:
  - immediate tick on start;
  - periodic tick using `FOREGROUND_SYNC_INTERVAL_SECONDS = 30L`;
  - stop cancels future ticks;
  - in-flight WebDAV sync is not force-cancelled.
- `MainActivity` owns lifecycle wiring:
  - starts auto-sync on resume when main UI is visible;
  - stops future ticks on pause/destroy;
  - guards result callbacks against detached/stopped activity.
- Manual and automatic sync share a static no-overlap guard in `MainActivity`.
- Automatic result policy:
  - `UP_TO_DATE` is silent;
  - success refreshes the visible list;
  - conflicts reuse `SyncConflictDialogFragment` and avoid duplicates;
  - repeated automatic errors are rate-limited;
  - manual sync remains explicit and reports when a sync is already running.

## Documentation state

- `README.md`, `doc/manual.md`, and `doc/manual_es.md` should describe foreground automatic sync plus manual sync availability.
- Documentation must not promise WebDAV push, real-time collaboration, or closed-app background sync.

## Quality gates

Use these commands after sync-related changes:

```bash
source env/setup.sh && tests/story4/run.sh
source env/setup.sh && tests/story8/run.sh
source env/setup.sh && tests/run-all.sh
source env/setup.sh && JAVA_HOME=$PWD/env/java11 ANDROID_HOME=$PWD/env/android-sdk GRADLE_USER_HOME=$PWD/.gradle-home GRADLE_OPTS='-Dorg.gradle.native=false' ./env/gradle/bin/gradle :sholi:assembleDebug
git diff --check
```

If a connected Android device is available, install and smoke-test:

```bash
source env/setup.sh && adb install -r sholi/build/outputs/apk/defaultFlavor/debug/sholi-defaultFlavor-debug.apk
```

## Current roster artifacts

- `briefs/automatic-sync-intake.md`
- `briefs/automatic-sync-plan.md`
- `briefs/automatic-sync-implementer.md`
- `briefs/automatic-sync-reviewer.md`
- `briefs/automatic-sync-qa-scope.md`
- `briefs/automatic-sync-impl.md`
