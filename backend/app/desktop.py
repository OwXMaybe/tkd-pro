"""Desktop launcher: runs the TKD PRO server on this computer and opens the app window.

    python -m app.desktop            # or TKD-PRO.exe, built with PyInstaller (see desktop/build-exe.ps1)
    python -m app.desktop --port 8000 --no-window

- Data lives in %LOCALAPPDATA%\\TKD-PRO (SQLite database, settings, recovery token), outside OneDrive.
- The server listens on the local network so the phone app can sync over the same Wi-Fi.
- The window signs in automatically with a one-time code that only works from this computer.
"""

import argparse
import os
import shutil
import socket
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.request
import webbrowser
from pathlib import Path


def data_dir() -> Path:
    base = os.environ.get("LOCALAPPDATA") or str(Path.home() / ".local" / "share")
    path = Path(base) / "TKD-PRO"
    path.mkdir(parents=True, exist_ok=True)
    return path


def read_env(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    if path.exists():
        for line in path.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                key, value = line.split("=", 1)
                values[key.strip()] = value.strip()
    return values


ENV_TEMPLATE = """# TKD PRO desktop server settings. Restart TKD PRO after editing.
# SHA-256 of the owner (recovery) token. The token itself is in owner-token.txt.
OWNER_TOKEN_SHA256={digest}
# Optional: OpenAI key for the AI Coach. Leave empty to keep AI off.
OPENAI_API_KEY=
OPENAI_MODEL=gpt-4.1-mini
PORT=8000
"""


def prepare(directory: Path) -> dict[str, str]:
    """Creates the settings file and the owner token on first run; returns the settings."""
    from .security import new_token

    env_path = directory / "server.env"
    if not env_path.exists():
        token, digest = new_token()
        env_path.write_text(ENV_TEMPLATE.format(digest=digest), encoding="utf-8")
        (directory / "owner-token.txt").write_text(
            "TKD PRO owner (recovery) token. Anyone with this token can access your data: keep it private.\n"
            "Normally you do not need it: pair devices with the 6-digit code shown in the app.\n\n"
            f"{token}\n",
            encoding="utf-8",
        )
    return read_env(env_path)


def port_in_use(port: int) -> bool:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.settimeout(0.5)
        return s.connect_ex(("127.0.0.1", port)) == 0


def wait_until_up(port: int, timeout_s: float = 30) -> bool:
    deadline = time.time() + timeout_s
    while time.time() < deadline:
        try:
            urllib.request.urlopen(f"http://127.0.0.1:{port}/manifest.webmanifest", timeout=1)
            return True
        except (urllib.error.URLError, OSError):
            time.sleep(0.3)
    return False


def open_window(url: str) -> None:
    """Opens the app in its own window (Edge or Chrome app mode), falling back to the default browser."""
    candidates = [
        shutil.which("msedge"),
        r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
        r"C:\Program Files\Microsoft\Edge\Application\msedge.exe",
        shutil.which("chrome"),
        r"C:\Program Files\Google\Chrome\Application\chrome.exe",
        os.path.expandvars(r"%LOCALAPPDATA%\Google\Chrome\Application\chrome.exe"),
    ]
    for exe in candidates:
        if exe and os.path.exists(exe):
            subprocess.Popen([exe, f"--app={url}", "--window-size=1280,860"])
            return
    webbrowser.open(url)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="TKD PRO desktop server")
    parser.add_argument("--port", type=int, default=None)
    parser.add_argument("--no-window", action="store_true", help="do not open the app window")
    args = parser.parse_args(argv)

    directory = data_dir()
    env = prepare(directory)
    port = args.port or int(env.get("PORT", "8000") or 8000)

    if port_in_use(port):
        # Already running (e.g. started earlier): just open another window. The browser keeps its sign-in.
        print(f"TKD PRO is already running on port {port}.")
        if not args.no_window:
            open_window(f"http://localhost:{port}/")
        return 0

    os.environ["OWNER_TOKEN_SHA256"] = env.get("OWNER_TOKEN_SHA256", "")
    os.environ["OPENAI_API_KEY"] = env.get("OPENAI_API_KEY", "")
    os.environ["OPENAI_MODEL"] = env.get("OPENAI_MODEL", "gpt-4.1-mini")
    os.environ["DATABASE_URL"] = f"sqlite+aiosqlite:///{(directory / 'tkdpro.db').as_posix()}"
    os.environ.setdefault("POSE_MODEL_PATH", str(directory / "pose_landmarker_full.task"))

    import uvicorn

    from .main import app
    from .routers.auth import lan_addresses
    from .security import pairing

    server = uvicorn.Server(uvicorn.Config(app, host="0.0.0.0", port=port, log_level="warning", access_log=False))
    thread = threading.Thread(target=server.run, daemon=True)
    thread.start()
    if not wait_until_up(port):
        print("The server did not start. Is another program using port", port, "?")
        return 1

    print("=" * 64)
    print(" TKD PRO is running on this computer.")
    print(f"   This computer : http://localhost:{port}")
    for ip in lan_addresses():
        print(f"   Phone (Wi-Fi) : http://{ip}:{port}")
    print(f" Data folder: {directory}")
    print(" Keep this window open while you use the app; close it to stop TKD PRO.")
    print("=" * 64)

    if not args.no_window:
        open_window(f"http://localhost:{port}/auth/local?code={pairing.new_local_code()}")
    try:
        while thread.is_alive():
            thread.join(0.5)
    except KeyboardInterrupt:
        server.should_exit = True
        thread.join(5)
    return 0


if __name__ == "__main__":
    sys.exit(main())
