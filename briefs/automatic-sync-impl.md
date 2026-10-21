# Implementation Brief — automatic-sync

**Date:** 2026-06-09
**Mode:** full
**Status:** COMPLETED

## Modified files

| File | Change |
|---|---|
| `tests/story8/src/SyncOrchestrationStoryTest.java` | Added source assertions for foreground auto-sync lifecycle wiring, interval constant, shared no-overlap guard, runner reuse, automatic result policy, duplicate conflict-dialog avoidance, and error rate-limit hook. |
| `sholi/src/main/java/name/soulayrol/rhaa/sholi/ForegroundWebDavAutoSyncController.java` | Added foreground-only scheduler with immediate tick, 30s periodic polling, stop/shutdown handling, and main-thread delegated sync start. |
| `sholi/src/main/java/name/soulayrol/rhaa/sholi/MainActivity.java` | Wired resume/pause/destroy auto-sync lifecycle, shared manual/automatic in-flight guard, shared `AndroidWebDavSyncRunner.synchronizeOrResume()` execution path, and separate manual/automatic result policies. |
| `sholi/src/main/java/name/soulayrol/rhaa/sholi/SyncConflictDialogFragment.java` | Exposed duplicate-dialog detection and reused it from existing `showNext(...)`. |
| `sholi/src/main/res/values/strings.xml` | Added explicit message for manual sync attempts while another sync is running. |
| `README.md` | Documented foreground automatic WebDAV sync and retained manual sync wording. |
| `doc/manual.md` | Replaced manual-only sync wording with foreground automatic behavior, polling boundary, manual sync, conflict, and error policy details. |
| `doc/manual_es.md` | Updated Spanish WebDAV sync section for foreground automatic sync and manual sync availability. |
| `skills-meta/friction.jsonl` | Appended task friction entry for `automatic-sync`. |
| `briefs/automatic-sync-impl.md` | Added this implementation handoff report. |

## Decisions made

- Used a dedicated `ForegroundWebDavAutoSyncController` for scheduling only; all WebDAV work still runs through `AndroidWebDavSyncRunner.synchronizeOrResume()`.
- Kept the poll interval as a named `FOREGROUND_SYNC_INTERVAL_SECONDS = 30L` constant.
- Made the manual/automatic synchronization guard static in `MainActivity` so an in-flight sync remains protected across activity recreation.
- Stopping foreground auto-sync cancels future scheduled ticks and does not interrupt an already-running WebDAV sync thread.
- Automatic `UP_TO_DATE` results are silent; successful changed results refresh the visible list; automatic conflicts reuse `SyncConflictDialogFragment` only when one is not already shown; repeated automatic errors are rate-limited.
- Manual *Sync* remains explicit and reports when another manual/automatic sync is already in progress.
- TDD red phase observed before production edits: `tests/story8/run.sh` failed with `NoSuchFileException` for the missing `ForegroundWebDavAutoSyncController.java`.

## Quality Gates

- ✅ `source env/setup.sh && tests/story8/run.sh` — PASS (`Story #8 orchestration tests passed`).
- ✅ `source env/setup.sh && tests/run-all.sh` — PASS (`Documentation QA passed`; stories 1–8 passed).
- ✅ `source env/setup.sh && JAVA_HOME=$PWD/env/java11 ANDROID_HOME=$PWD/env/android-sdk GRADLE_USER_HOME=$PWD/.gradle-home GRADLE_OPTS='-Dorg.gradle.native=false' ./env/gradle/bin/gradle :sholi:assembleDebug` — PASS (`BUILD SUCCESSFUL`).
- ✅ `git diff --check` — PASS (no output).

## Points of attention for review

- Automatic sync is verified by JDK source assertions plus Gradle compile, not by Android lifecycle instrumentation.
- Automatic configuration/auth/network/server errors show at most one toast per repeated status/rate-limit window while foreground; `UP_TO_DATE` stays silent.
- The existing resolved-conflict dialog upload path remains unchanged and still calls the Android runner directly.
- Gradle generation rewrote greenDAO generated files during assemble, but no schema/package/app-id changes were made.

## Identified out-of-scope

- Background sync after pause/close.
- WebDAV push/subscription support.
- User-configurable poll interval.
- Sync document schema, database schema, package namespace, or application-id changes.
- New credential storage or plaintext credential handling.
