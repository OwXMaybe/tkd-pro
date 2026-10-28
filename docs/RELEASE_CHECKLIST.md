# Release checklist

## Build
- [ ] Bump `versionCode` / `versionName` in `app/build.gradle.kts`.
- [ ] Create an upload keystore (never commit it) and a `release` signing config read from `secrets.properties` / environment.
- [ ] `./gradlew :core:domain:test :app:testDebugUnitTest` green.
- [ ] `./gradlew :app:bundleRelease` (R8 enabled) and smoke-test the release build: MediaPipe, MapLibre, Room, Retrofit serialization still work after shrinking.
- [ ] Commit the Room schema JSON in `app/schemas/`; add a migration for any schema change.

## Functional
- [ ] Onboarding, language switch (TR/EN), theme switch, app lock lock/unlock and relock after 60 s in background.
- [ ] GPS run on a device: start, screen off, pause/resume, auto-pause, finish, discard, kill app mid-run → recovered run.
- [ ] Location permission denied / notification permission denied paths.
- [ ] Motion Lab: good video, bad video (quality refusal with reasons), >30 s video, compare two analyses.
- [ ] Reaction tests: false start, no response, all invalid.
- [ ] Offline: every screen usable without network; AI and server analysis show "unavailable", not errors.
- [ ] Backend: wrong token → clear message; sync queue drains after reconnect; nothing lost when offline.
- [ ] Export JSON opens and contains all records; "Delete all data" leaves an empty app with the technique library.

## Privacy & compliance
- [ ] Play Console data-safety form: location (run recording, not shared), photos/videos (on device), health & fitness (on device; optional backup to owner's server), no ads, no third-party sharing except AI requests to the owner's backend.
- [ ] Foreground service type `location` declaration and in-app disclosure text match actual use.
- [ ] Privacy policy URL (single-owner app, local storage, optional self-hosted backend, AI opt-in per category).
- [ ] If the owner is a minor: parental consent obtained before enabling AI or backend sync.
- [ ] Backend behind HTTPS; `OWNER_TOKEN_SHA256` set; PostgreSQL password strong; database backups configured.

## Store assets
- [ ] Launcher icon review, screenshots (TR and EN, light and dark), short/long description in both languages.
