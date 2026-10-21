# Implementer Brief — automatic-sync

**Date:** 2026-06-07
**Status:** VALIDATED

## Goal

Implement foreground automatic WebDAV synchronization for sholi-dav. When the main list UI is open/resumed, run an immediate sync and continue periodic foreground sync ticks so changes pushed by another device are detected and synchronized automatically. Preserve all existing WebDAV safety behavior by reusing `AndroidWebDavSyncRunner.synchronizeOrResume()`.

## Scope boundary

Out of scope:
- Background sync after the app is closed/stopped/background-only.
- WebDAV push/server subscriptions.
- Sync document schema/database/package/application-id changes.
- New credential storage or plaintext credential handling.
- Bypassing existing ETag/merge/conflict safety.
- User-facing poll interval setting unless unavoidable.

## Files to modify

| File | Intended role | Key snippet from intake |
|---|---|---|
| `sholi/src/main/java/name/soulayrol/rhaa/sholi/MainActivity.java` | Lifecycle and manual/auto result routing. | `case R.id.action_sync: startSynchronization();` / `new AndroidWebDavSyncRunner(MainActivity.this).synchronizeOrResume();` / `if (result.isSuccess()) { refreshVisibleList(); }` |
| `sholi/src/main/java/name/soulayrol/rhaa/sholi/AbstractListFragment.java` | Existing refresh API to reuse. | `public void refreshList() { ... _adapter.setLazyList(createList(getActivity())); }` |
| `sholi/src/main/java/name/soulayrol/rhaa/sholi/SyncConflictDialogFragment.java` | Existing conflict UI and resolved-conflict sync callback. | `result = runner.synchronizeOrResume();` / `showNext(...)` / `refreshVisibleList(activity);` |
| `sholi/src/main/java/name/soulayrol/rhaa/sholi/sync/android/AndroidWebDavSyncRunner.java` | Android sync facade to call; avoid parallel engine. | `public WebDavSyncResult synchronizeOrResume() { ... }` |
| `tests/story8/src/SyncOrchestrationStoryTest.java` | Test-first source regressions for automatic sync wiring. | `verifySyncMenuAndConflictUiSources()` source assertions. |
| `tests/story4/src/WebDavSyncStoryTest.java` | Keep existing WebDAV behavior green; add only if client behavior changes. | Story #4 WebDAV safe sync coverage. |
| `README.md`, `doc/manual.md`, localized manuals | Update manual-only sync wording. | `Synchronization is manual and offline-first...` |

## Sequential steps

1. Add failing story8/source assertions for automatic foreground sync: lifecycle start/stop, named interval constant, no-overlap guard, use of `AndroidWebDavSyncRunner.synchronizeOrResume()`, silent automatic `UP_TO_DATE`, success refresh, conflict dialog reuse, and automatic error suppression/rate-limit hooks.
2. Add an explicit foreground auto-sync controller or equivalent isolated MainActivity helper with start/stop/tick, immediate run, periodic run, named interval constant, and thread-safe in-progress guard.
3. Route manual sync and automatic sync through a shared execution path/guard so concurrent `synchronizeOrResume()` calls cannot happen.
4. Implement automatic result handling separately from manual result handling: automatic `UP_TO_DATE` silent; automatic success refreshes visible list; automatic conflicts show existing dialog only when no dialog is already present; repeated automatic errors are suppressed/rate-limited; manual sync still shows explicit feedback.
5. Wire lifecycle: start auto-sync on `MainActivity` resume/open when main list UI is visible; stop on pause/destroy by cancelling scheduled future ticks. Do not force-cancel an in-flight WebDAV sync; instead guard result callbacks against detached/stopped activity.
6. Preserve list refresh lifecycle safety via existing `AbstractListFragment.refreshList()` and attached-fragment checks.
7. Update docs (`README.md`, `doc/manual.md`, localized manuals if they mention manual sync) to describe foreground automatic sync and manual sync.
8. Run quality gates and report exact commands/results.

## Points of attention

- Do not create a parallel sync engine. Use `AndroidWebDavSyncRunner.synchronizeOrResume()`.
- The overlap guard must cover both manual and automatic sync.
- Avoid repeated automatic toasts. `UP_TO_DATE` is silent; errors should not toast every tick.
- Do not leak credentials in docs, logs, strings, exceptions, or tests.
- Avoid duplicate conflict dialogs; reuse `SyncConflictDialogFragment.showNext(...)` behavior.
- Avoid unsafe cancellation of WebDAV writes. Stopping auto-sync cancels future ticks and UI callbacks, not necessarily an already-started sync operation.
- Keep diffs focused; no schema/app-id/package changes.

## Quality gates

```bash
# Build
JAVA_HOME=$PWD/env/java11 ANDROID_HOME=$PWD/env/android-sdk GRADLE_USER_HOME=$PWD/.gradle-home GRADLE_OPTS='-Dorg.gradle.native=false' ./env/gradle/bin/gradle :sholi:assembleDebug

# Tests
tests/run-all.sh

# Lint/Format
git diff --check
```

Also run targeted `tests/story8/run.sh` before full `tests/run-all.sh`.
