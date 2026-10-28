# Architecture

## Modules

```
:core:domain   Pure Kotlin. No Android dependencies. All calculations that turn raw input into numbers.
:app           Android: presentation (Compose screens + ViewModels), data (Room, DataStore, repositories,
               network, workers, services) and DI (Hilt).
backend/       FastAPI server: API + the web app (backend/web, plain ES modules, no build step).
               Runs on the owner's computer (app/desktop.py → TKD-PRO.exe, SQLite) or on a server (Docker, PostgreSQL).
```

### Phone + computer

```
 Android app (Room, source of truth on the phone)          Web app / PWA (browser, in-memory mirror)
        │ push queue ──►  POST /api/v1/sync/push  ◄── writes (negative ids)
        │ pull cursor ◄── GET  /api/v1/sync/changes?after=N ──► polling every 10 s
        └──────────────►  sync_records (newest copy per record + global revision) ◄────────┘
```

- Every accepted write gets the next value of a global `revision` counter (`sync_counter`), so the change
  feed returns each change once, oldest first, deletions included.
- Conflicts: last writer wins by client timestamp, per record. The phone skips incoming rows that still have
  an unsent local change, so a local edit is never overwritten before it reaches the server.
- Ids: the phone keeps Room auto-increment ids (positive); the web app creates negative ids
  (`-(epoch ms × 1000 + random)`), so the two can never collide and Room's sequence is unaffected.
- Pulled changes are applied parents-first (`Sync.ENTITIES` order), one transaction per row, straight
  through `SyncApplyDao` (never back into the queue). Rows that violate a constraint (e.g. a child whose
  parent was deleted on the phone) are skipped and logged.
- Parent-keyed entities carry whole child lists: `run_routes` (GPS points + splits of a finished run, so the
  map shows on the computer; only restored on a phone that has no points for that run) and
  `combination_steps`. Videos and pose frames stay on the phone; only analysis results sync.
- When sync is first enabled or a computer is paired, `SyncEngine.enqueueEverything()` uploads all records.
- Auth: owner token (hash in config) or per-device tokens issued by pairing (`device_tokens`, hash only,
  revocable). Phones use bearer tokens, browsers an HttpOnly SameSite=Strict cookie.

### Layers inside `:app`

| Package | Responsibility |
|---|---|
| `core.database` | Room entities, DAOs, relations (`TkdDatabase`, schema exported to `app/schemas`) |
| `core.datastore` | Preferences (theme, units, dashboard layout, reminders, GPS, AI permissions, backend URL) |
| `core.security` | Android Keystore AES-GCM store for the backend token |
| `core.network` | Retrofit API for the owner backend, error mapping (`BackendError`) |
| `core.designsystem` | Theme (palette, Manrope typography, shapes), reusable components, charts (Vico), route views (MapLibre + offline sketch), video player with pose overlay |
| `core.ui` | Locale/unit-aware `Formatter`, enum → string-resource labels, per-app language |
| `data.repository` | One repository per domain; the only place screens get data from |
| `data.ai` | `CoachContextBuilder`: builds the permission-filtered JSON context and its data sources |
| `data.motion` | `PoseExtractor`: MediaPipe Pose Landmarker (VIDEO mode) over sampled frames |
| `data.sync` | Sync queue tracker + WorkManager worker (batched, exponential backoff) |
| `feature.*` | Screens and ViewModels (StateFlow), one package per area |
| `navigation` | Type-safe routes, bottom navigation (Home · Train · AI Coach · Insights · Profile), nav graph |

## Key flows

**GPS run** — `LiveRunScreen` → `RunTracker.start()` → `RunTrackingService` (foreground, type `location`)
collects `LocationSource` → `GpsPointFilter` accepts/rejects → each accepted point is written to Room
immediately → on finish `RunningRepository.finishRun()` recomputes distance, elevation, splits and best
efforts from the stored points. Unfinished runs are recovered after process death in a paused state.

**Motion analysis** — video copied into private storage → `PoseExtractor` (on device) or backend
`/motion/analyze` → `MotionAnalyzer` (domain; Python port on the backend produces the identical JSON) →
`VideoQualityChecker` blocks unreliable input → result + pose frames saved → report with overlay.

**AI Coach** — `AiRepository.ask()` stores the question → `CoachContextBuilder` includes only permitted
categories (never name, club, coach, photos, videos or routes) → backend `/coach/chat` adds the safety
system prompt and calls OpenAI → answer stored with its data sources.

**Sync** — repositories call `SyncTracker` after writes (only when sync is enabled) → `sync_queue`
(collapses repeated edits) → `SyncWorker` runs `SyncEngine.push()` then `pull()`: on app start, when the app
comes to the foreground, after local changes and every 30 minutes in the background. Local data is never
deleted because of a failed push.

## Security & privacy

- No registration, no multi-user model, no public endpoints or API docs on the backend.
- Owner auth: app lock via BiometricPrompt (biometric or device credential); backend bearer token stored
  encrypted with an Android Keystore key, verified server-side against its SHA-256 with constant-time compare.
- HTTPS required for public addresses (`BackendClient.isAllowedUrl`); plain HTTP only to loopback, private
  LAN ranges, Tailscale (100.64/10) and `*.local`, where the owner's computer runs the server.
- Web app: strict Content-Security-Policy (scripts only from the server; map tiles from OpenFreeMap),
  `X-Frame-Options: DENY`, no-store on API responses, rate-limited login and pairing.
- Videos, pose data and the profile photo live in app-private storage; backups and device transfer are disabled.
- OpenAI keys exist only on the backend.
- Export (JSON via the system file picker) and full deletion (DB, files, token, preferences) in Settings.

## Data model

See [DATA_MODEL.md](DATA_MODEL.md).
