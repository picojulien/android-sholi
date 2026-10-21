# QA Scope — automatic-sync

**Date:** 2026-06-07
**Status:** VALIDATED

## Quality gates

```bash
# Targeted orchestration/source regression
tests/story8/run.sh

# Full story and docs suite
tests/run-all.sh

# Android debug build
JAVA_HOME=$PWD/env/java11 ANDROID_HOME=$PWD/env/android-sdk GRADLE_USER_HOME=$PWD/.gradle-home GRADLE_OPTS='-Dorg.gradle.native=false' ./env/gradle/bin/gradle :sholi:assembleDebug

# Whitespace/diff hygiene
git diff --check
```

If an adb device is connected, install:

```bash
source env/setup.sh && adb install -r sholi/build/outputs/apk/defaultFlavor/debug/sholi-defaultFlavor-debug.apk
```

## Behaviors to validate

- App opens/resumes and starts foreground automatic sync without blocking the initial list display.
- Periodic foreground sync detects remote changes while the app is open.
- The visible list refreshes after automatic pull/merge/upload without app restart.
- Automatic `UP_TO_DATE` does not show repeated toasts.
- Manual *Sync* still works and provides explicit feedback.
- Manual sync and automatic sync cannot overlap or corrupt state.
- Conflicts detected by automatic sync show the existing conflict dialog.
- Duplicate conflict dialogs are not opened while one is already visible.
- Leaving/backgrounding the app stops future automatic ticks and does not crash when an in-flight sync completes.
- Repeated auth/network/server errors do not toast-spam.
- Docs describe foreground automatic polling, not background sync or push.

## Suggested manual scenarios

1. Configure two devices to the same WebDAV remote. Keep device B open on the list. Change and sync from device A. Wait for B's foreground auto-sync interval and confirm B updates without pressing *Sync*.
2. With B open and no remote changes, wait through multiple ticks and confirm no `UP_TO_DATE` toast spam.
3. Start manual sync while automatic sync is likely running; confirm no crash/overlap and clear feedback.
4. Create a same-item conflict from two devices; keep B open and confirm the existing conflict dialog appears once.
5. Start sync, then background/foreground/rotate if possible; confirm no crash and no duplicate sync loops.
