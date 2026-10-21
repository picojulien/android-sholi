# Plan — automatic-sync

**Date:** 2026-06-07
**Status:** VALIDATED

## Consensus Table

| Point | Voice 1 | Voice 2 | Status |
|---|---|---|---|
| Use existing sync path | ✅ `AndroidWebDavSyncRunner.synchronizeOrResume()` | ✅ parallel sync engine would increase risk | AGREE |
| Foreground lifecycle controller | ✅ explicit start/stop/tick | ✅ lifecycle is primary failure point | AGREE |
| Manual/auto overlap guard | ⚠️ must be global across manual and auto paths | ⚠️ missing guard risks concurrent DB/WebDAV writes | AGREE |
| Fixed 30s polling risk | ⚠️ arbitrary without backoff | ⚠️ battery/rate-limit risk | AGREE |
| Error spam risk | ⚠️ rate-limit repeated errors | ⚠️ auth/network failures can toast-spam | AGREE |
| Conflict dialog reuse | ✅ reuse existing dialog | ⚠️ avoid duplicate/interruption loops | AGREE |
| JDK-only test limitation | ⚠️ source assertions cannot prove lifecycle runtime | ⚠️ manual QA still required | AGREE |

## Sequential steps

1. **Add automatic-sync regression assertions** — Update `tests/story8/src/SyncOrchestrationStoryTest.java` before production changes. Cover named foreground auto-sync controller, immediate-on-resume start, lifecycle stop, no-overlap guard shared by manual and automatic sync, silent automatic `UP_TO_DATE`, successful-result refresh, conflict dialog reuse, error suppression/backoff hooks, and named polling interval. Completion criterion: story8 fails before implementation for missing automatic-sync wiring.
2. **Introduce foreground auto-sync orchestration** — Add a small explicit foreground auto-sync controller or equivalent isolated class. It schedules an immediate tick and periodic ticks, uses a named interval constant, keeps a thread-safe in-progress guard, cancels pending future ticks on stop, and does not start new sync runs after stop. Completion criterion: automatic ticks call `AndroidWebDavSyncRunner.synchronizeOrResume()` on a background thread and post results only if still active.
3. **Share sync execution and overlap control with manual sync** — Route manual *Sync* and automatic ticks through one shared execution path/guard so they cannot overlap. Manual sync remains user-visible and should give clear feedback if a sync is already running or if it joins/skips because one is in progress. Completion criterion: no separate manual path can launch a concurrent `synchronizeOrResume()` call.
4. **Implement automatic result policy** — Route automatic results separately from manual results: `UP_TO_DATE` is silent; success refreshes the visible list; conflicts open the existing `SyncConflictDialogFragment` only if not already open; repeated configuration/auth/network/server errors are suppressed or rate-limited; no secret-bearing messages are introduced. Completion criterion: automatic result handling preserves existing manual result behavior and conflict diagnostics.
5. **Wire MainActivity lifecycle** — Start foreground auto-sync when the main activity/list is resumed and stop it when paused/destroyed. Stop means cancel future scheduled ticks; in-flight syncs that already started are allowed to finish safely but must not update a detached activity. Completion criterion: lifecycle callbacks are guarded against rotation/back/background races.
6. **Preserve visible-list refresh safety** — Reuse `AbstractListFragment.refreshList()` for successful automatic pulls/merges/uploads and preserve existing lifecycle guards so refresh is skipped for detached fragments. Completion criterion: automatic success updates the currently visible list without requiring app restart.
7. **Update documentation** — Update `README.md`, `doc/manual.md`, and localized manuals if present in scope to explain foreground automatic sync, immediate-on-open behavior, manual sync availability, polling nature, and safety/conflict behavior. Completion criterion: docs no longer say synchronization is manual-only.
8. **Run deterministic quality gates** — Run `tests/run-all.sh`, Gradle assemble, and `git diff --check`. Completion criterion: all required gates pass.
9. **Review and QA** — Reviewer audits concurrency, lifecycle, security, and WebDAV safety risks; architect audits layering and maintainability; QA installs APK if a device is connected and manually retests foreground auto-sync if possible. Completion criterion: no blocking issues remain.

## Dependencies

- Step 1 must precede implementation because project rules require test-first workflow and the brief explicitly requires source-level story8 coverage.
- Step 2 must precede lifecycle wiring because `MainActivity` needs a concrete controller/execution abstraction to start and stop.
- Step 3 must precede result-policy and lifecycle completion because manual/auto overlap is a core safety property.
- Step 4 depends on Step 3 so result routing knows whether a run is manual or automatic.
- Step 5 depends on Step 2 and Step 4 because lifecycle callbacks need controller start/stop and safe callback behavior.
- Step 7 follows implementation so documentation matches actual behavior and names.
- Step 8 precedes review/QA; Tier 1 failures block Tier 2.

## Identified risks

| Risk | Probability | Impact | Mitigation |
|---|---|---|---|
| Manual and automatic sync overlap | Medium | High | Use one shared thread-safe in-progress guard/execution path for both manual and automatic sync. |
| Activity pauses/destroys while sync is in flight | High | High | Stop future ticks; let existing sync finish safely; suppress UI callbacks unless activity/controller is still active and attached. |
| Fixed polling causes battery/server load | Medium | Medium | Use a named conservative interval, skip overlapping/redundant runs, and suppress repeated errors; keep future configurability possible. |
| Repeated auth/network/server failures toast-spam | High | Medium | Automatic errors are rate-limited or suppressed; manual sync remains explicit. |
| Conflict dialog interrupts/duplicates | Medium | Medium | Reuse existing dialog, check existing dialog tag, and pause/skip automatic conflict presentation while one is already open. |
| List refresh causes jank or scroll jumps | Medium | Medium | Refresh only on successful sync and only visible attached list; manual QA should verify real UX. |
| JDK story tests cannot prove Android lifecycle behavior | High | Medium | Add source assertions plus reviewer/QA manual lifecycle scenarios; Gradle build verifies compile. |
| Existing sync runner lacks cancellation | Medium | Medium | Do not attempt unsafe mid-flight cancellation; no new ticks after stop; guarded result callbacks. |
| Documentation remains manual-only | Medium | Low | Update README/manual/manual_es with automatic foreground behavior. |

## Decisions made

| Point | Decision | Reason |
|---|---|---|
| Sync mechanism | Reuse `AndroidWebDavSyncRunner.synchronizeOrResume()` | Preserves existing ETag/merge/conflict safety. |
| Scope | Foreground-only polling, not background sync or WebDAV push | Matches validated scope boundary. |
| Poll interval | Use named conservative code constant, initially 30 seconds unless implementer finds a compile/runtime reason to adjust | Brief proposes 30 seconds and no user-facing setting. |
| Stop behavior | Cancel scheduled future ticks, do not force-cancel an in-flight WebDAV sync | Avoids partial writes and preserves existing synchronous sync safety. |
| Manual sync | Keep explicit feedback and route through shared guard | Manual sync remains available and avoids overlap. |
| Automatic `UP_TO_DATE` | Silent | Avoids toast spam per brief. |
| Automatic errors | Suppress or rate-limit repeated errors | Required to avoid repeated foreground polling noise. |
| Conflict handling | Reuse existing `SyncConflictDialogFragment` and avoid duplicate dialogs | Preserves current conflict UX and diagnostics. |

## Assumptions

- “Main list UI is visible” means `MainActivity` is resumed and the visible container fragment is an attached `AbstractListFragment`.
- A sync started while foreground may finish after `onPause()`; this is not considered background scheduling because no new run is started after stop.
- No user-facing auto-sync preference is required in this iteration.
- Source-level story8 assertions plus QA/manual testing are acceptable because the brief keeps tests JDK-only where possible.
- Localized documentation updates are expected where existing localized manuals mention manual sync.
