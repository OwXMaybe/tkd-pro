# Setup

## 1. Android app

Requirements: Android Studio (Ladybug or newer) with its bundled JDK 17+, Android SDK 35.

1. Open the repository root in Android Studio and let Gradle sync.
2. Run the `app` configuration on a device or emulator (Android 10 / API 29 or newer).
3. The MediaPipe pose model (`app/src/main/assets/pose_landmarker_full.task`, Apache-2.0) is bundled.
   To re-download it:
   `https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_full/float16/latest/pose_landmarker_full.task`

Command line:

```bash
./gradlew :app:assembleDebug
./gradlew :core:domain:test :app:testDebugUnitTest
```

> Windows note: very long project paths can break the Android build. Clone into a short path
> (e.g. `C:\dev\tkd-pro`) or map one with `subst T: <path>`.

Maps use OpenFreeMap vector tiles (no API key). Without network the run screens fall back to an offline
route sketch drawn from the stored GPS points.

## 2. Computer app (Windows) — recommended

`desktop/dist/TKD-PRO.exe` contains the server and the web app. Double-click it:

- Data: `%LOCALAPPDATA%\TKD-PRO` (`tkdpro.db` SQLite, `server.env`, `owner-token.txt`). Created on first run,
  including a random owner token whose SHA-256 goes into `server.env`.
- Listens on `0.0.0.0:8000` so the phone can reach it over Wi-Fi; allow it for **private** networks when
  Windows Firewall asks. `PORT` in `server.env` changes the port.
- Opens the app in an Edge/Chrome app window, signed in with a one-time code that is accepted only from
  this computer (`/auth/local`). The console window is the server; closing it stops TKD PRO.
- AI: put `OPENAI_API_KEY` in `server.env` and restart.

Rebuild the exe (Python 3.12):

```bash
powershell -ExecutionPolicy Bypass -File desktop/build-exe.ps1
```

Run from source instead: `cd backend && pip install -r requirements-dev.txt && python -m app.desktop`.

### Pairing devices

Web app → Settings → Connect a device shows a 6-digit code (one-time, 5 minutes, max 5 wrong guesses,
login attempts rate-limited) and the computer's LAN addresses.

- **Phone app:** Settings → Computer & phone → address + code → Pair. The phone receives its own device
  token (stored in the Android Keystore), uploads all existing records once, then syncs both ways.
  Plain `http://` is accepted only for loopback, private LAN ranges, `100.64/10` (Tailscale) and `*.local`;
  any other address must use HTTPS.
- **Another browser:** open the address, enter the code. The browser gets a revocable device token in an
  HttpOnly, SameSite=Strict cookie.

Devices are listed (and can be signed out) under Settings → Signed-in devices.

## 3. Backend on a server (alternative)

The same backend can run on a VPS or home server with PostgreSQL. It also serves the web app at `/`.

### Generate the owner token

```bash
cd backend
python -m venv .venv && . .venv/bin/activate        # Windows: .venv\Scripts\activate
pip install -r requirements.txt
python -m app.cli new-token
```

Put the printed hash into `.env` as `OWNER_TOKEN_SHA256` (copy `.env.example` first). Keep the token itself
secret; you will enter it in the app.

### Run with Docker (recommended)

```bash
cp .env.example .env            # fill OWNER_TOKEN_SHA256 and, optionally, OPENAI_API_KEY
export POSTGRES_PASSWORD=<strong password>
docker compose up -d --build
```

The API listens on `127.0.0.1:8000`. Put an HTTPS reverse proxy in front of it before exposing it, e.g. Caddy:

```
tkd.example.com {
    reverse_proxy 127.0.0.1:8000
}
```

The Android app requires HTTPS for public addresses (plain HTTP only inside the home network or Tailscale).

### Run locally without Docker

```bash
pip install -r requirements-dev.txt          # add requirements-motion.txt for server video analysis
export DATABASE_URL=postgresql+asyncpg://tkdpro:pw@localhost:5432/tkdpro
uvicorn app.main:app --reload
pytest
```

### Connect the app

Settings → Owner backend & sync:
1. Backend URL, e.g. `https://tkd.example.com` (emulator → local machine: `http://10.0.2.2:8000`).
2. Owner access token.
3. Test connection: shows whether AI and server analysis are configured.
4. Optional: enable backup sync and/or switch the video analysis engine to "My backend".

Settings → AI data permissions: enable the AI Coach and choose the categories it may use.

## 4. Environment variables (backend)

| Variable | Required | Description |
|---|---|---|
| `OWNER_TOKEN_SHA256` | yes | SHA-256 of the owner token. Without it every request is refused (503). |
| `DATABASE_URL` | yes | `postgresql+asyncpg://…` or `sqlite+aiosqlite:///path/tkdpro.db` (the desktop app sets this) |
| `OPENAI_API_KEY` | no | Enables AI endpoints; otherwise they return 503 and the app shows "AI unavailable". |
| `OPENAI_MODEL` | no | Default `gpt-4.1-mini`. |
| `POSE_MODEL_PATH` | no | MediaPipe model for server analysis (downloaded automatically in Docker). |
| `MAX_UPLOAD_MB` | no | Upload limit for analysis videos (default 200). |
