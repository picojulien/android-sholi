# Reviewer Brief — automatic-sync

**Date:** 2026-06-07
**Status:** VALIDATED

## What should be implemented

Foreground automatic WebDAV sync while the main list UI is open. The implementation should run an immediate sync on resume/open and periodic foreground sync ticks, use the existing `AndroidWebDavSyncRunner.synchronizeOrResume()` path, avoid overlapping manual/automatic syncs, keep automatic `UP_TO_DATE` silent, refresh the visible list after successful automatic sync, reuse the existing conflict dialog, and suppress/rate-limit repeated automatic errors.

## Files to audit first

| File | Audit focus |
|---|---|
| `sholi/src/main/java/name/soulayrol/rhaa/sholi/MainActivity.java` | Lifecycle wiring, manual/auto shared guard, result routing, UI callback guards. |
| Any new foreground auto-sync class/helper | Scheduler, interval, overlap guard, thread lifecycle, stop semantics. |
| `sholi/src/main/java/name/soulayrol/rhaa/sholi/SyncConflictDialogFragment.java` | Conflict dialog reuse and duplicate-dialog prevention. |
| `sholi/src/main/java/name/soulayrol/rhaa/sholi/AbstractListFragment.java` | Refresh safety if touched. |
| `sholi/src/main/java/name/soulayrol/rhaa/sholi/sync/android/AndroidWebDavSyncRunner.java` | Must remain the sync facade; no parallel unsafe sync path. |
| `tests/story8/src/SyncOrchestrationStoryTest.java` | Regression coverage for lifecycle wiring and result policies. |
| `README.md`, `doc/manual*.md` | Documentation accurately describes foreground automatic sync and safety boundaries. |

## Risks to verify

- Manual and automatic sync cannot overlap.
- Stop/pause cancels future ticks and prevents detached UI callbacks.
- In-flight sync is not unsafely interrupted mid-WebDAV operation.
- Automatic `UP_TO_DATE` does not toast.
- Repeated automatic errors do not toast-spam.
- Conflict dialog does not duplicate if already open.
- Successful automatic pull/merge/upload refreshes the visible list.
- Credentials/secrets are not logged, exported, serialized, or shown in new messages.
- Documentation does not promise background sync or real-time push.

## Expected behaviors

- Opening/resuming the main list starts one immediate automatic sync and schedules periodic sync.
- Leaving/pausing the activity stops future automatic ticks.
- Manual *Sync* remains available and explicit.
- If a remote change exists, automatic sync pulls/merges it and the visible list updates without app restart.
- If a conflict exists, existing conflict UI is shown.
- If no change exists, automatic sync is silent.

## Quality evidence expected

```bash
tests/story8/run.sh
tests/run-all.sh
JAVA_HOME=$PWD/env/java11 ANDROID_HOME=$PWD/env/android-sdk GRADLE_USER_HOME=$PWD/.gradle-home GRADLE_OPTS='-Dorg.gradle.native=false' ./env/gradle/bin/gradle :sholi:assembleDebug
git diff --check
```
