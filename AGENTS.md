# AGENTS.md

## Project Conventions
- Keep persistent application state in SQLite through greenDAO entities and DAO sessions.
- Treat the database as the source of truth; UI fragments/activities should read and mutate through the data layer.
- Wrap multi-row DB mutations in transactions (`DaoSession.runInTx`) to keep operations atomic.
- Prefer explicit, small Java classes over hidden side effects.
- Never store WebDAV password/token material in plaintext logs, exported text, sync JSON, or error messages.

## Build and Test Commands
- Story-level regression tests (JDK-only):
  - `tests/story7/run.sh`
- Android/Gradle commands (when environment permits local sockets):
  - `JAVA_HOME=$PWD/env/java11 ANDROID_HOME=$PWD/env/android-sdk GRADLE_USER_HOME=$PWD/.gradle-home GRADLE_OPTS='-Dorg.gradle.native=false' ./env/gradle/bin/gradle :sholi:assembleDebug`

## Key Architectural Patterns
- Android app module lives in `sholi/`; model generator lives in `sholimodelgenerator/`.
- greenDAO generated entities/DAOs live under `sholi/src-gen/main/java/.../data/model`.
- Runtime DB access entry point is `name.soulayrol.rhaa.sholi.data.Operations`.
- Sync credential access is abstracted behind `CredentialStore` in `name.soulayrol.rhaa.sholi.sync.credentials`.
- Production credential storage must be wired to AndroidX `EncryptedSharedPreferences` through `EncryptedSharedPreferencesCredentialStore`.
- Tests should rely on fake/in-memory stores (`InMemoryCredentialStore`) to keep sync logic decoupled from Android storage APIs.

## Guidelines for Future AI Agents
- Follow strict test-first workflow for changes: write failing tests, then minimum implementation, then rerun all tests.
- Do not edit generated greenDAO files manually in `src-gen`; regenerate via `:sholimodelgenerator:generate` when schema changes are required.
- Keep secrets redacted by default (`WebDavCredentials.toString`, `CredentialSafeLogger`, serializers/formatters).
- Prefer additive changes in isolated packages for new features to limit regressions in legacy UI code.
