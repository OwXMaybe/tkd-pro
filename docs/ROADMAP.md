# Development roadmap

Status as of version 1.0.0.

| Phase | Scope | Status |
|---|---|---|
| 1 — Core foundation | Project setup, design system, TR/EN localization, navigation, owner profile, Room, home dashboard | Done |
| 2 — Taekwondo core | Training management, technical library, combination builder, match intelligence, timeline | Done |
| 3 — Running | GPS tracking, foreground service, route maps, splits, summaries, analytics | Done |
| 4 — Performance lab | Reaction tests, performance tests, recovery tracking, charts | Done |
| 5 — AI & computer vision | AI Coach (backend proxy), on-device + server MediaPipe analysis, reports | Done |
| 6 — Quality & release | Error handling, tests, privacy review, optimisation, release prep | In progress — see RELEASE_CHECKLIST.md |
| 7 — Computer & phone | Desktop server (TKD-PRO.exe), web app/PWA, device pairing, two-way sync | Done (1.1.0) |

## Verified so far

- 21 domain unit tests (GPS filter, route metrics, splits, best efforts, PRs, reaction stats, training load, date ranges, motion analyzer, comparison).
- 4 app unit tests + 4 Robolectric Room tests (aggregations, duplicates, cascades, unique GPS points, one check-in per day).
- 23 backend tests (auth, AI availability, sync idempotency/last-writer-wins, change feed order/pagination,
  cookie login/logout, pairing codes, device revocation, rate limiting, local login, motion analyzer parity).
- 8 sync engine tests (parents-first apply, pending local edits win, cascading deletes, orphan rows skipped,
  same-day recovery check-ins, route restore, full upload, allowed HTTP addresses).
- Web app checked in a browser against the desktop server with sample data: sign-in with pairing code, home,
  insights charts, run map, creating a session; desktop and phone-width layouts, light and dark theme.
- Debug APK built and launched on an Android 16 (API 37) emulator: onboarding, Turkish/English switching, dark theme, all tabs, and creating a training session checked manually.

## Next

1. Field-test GPS on a real phone (open sky, trees, city canyon, screen off, process kill + recovery).
2. Field-test Motion Lab with real kick videos; tune `riseThreshold` per technique if needed.
3. Instrumented UI tests (Compose) for the main flows.
4. ~~Restore from backend backup~~ — done: a reinstalled phone paired again pulls everything (routes included).
4b. Web app: reaction tests and GPS recording stay phone-only; live technique counters and video playback with
    skeleton overlay are not in the web app yet (analysis results are).
5. Health Connect import for heart rate (currently manual entry only).
6. Poomsae module (data model is category-based and can be extended).
7. Room migrations policy once the schema changes after release.
