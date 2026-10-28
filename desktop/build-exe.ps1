# Builds TKD-PRO.exe: the TKD PRO server + web app in one file, for Windows.
# Requires Python 3.12. Usage (from the repository root):
#   powershell -ExecutionPolicy Bypass -File desktop\build-exe.ps1 [-Python C:\path\to\python.exe]
param([string]$Python = "python")

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$backend = Join-Path $root "backend"
$work = Join-Path $env:TEMP "tkd-pro-exe-build"

& $Python -m pip install --quiet -r (Join-Path $backend "requirements.txt") aiosqlite==0.20.0 pyinstaller==6.11.1 pillow==11.0.0

# Windows icon from the web app icon.
& $Python -c "from PIL import Image; Image.open(r'$backend\web\icon-512.png').save(r'$work-icon.ico', sizes=[(16,16),(32,32),(48,48),(64,64),(128,128),(256,256)])"

Push-Location $backend
try {
    & $Python -m PyInstaller --noconfirm --clean --onefile --console `
        --name "TKD-PRO" `
        --icon "$work-icon.ico" `
        --add-data "$backend\web;web" `
        --collect-submodules uvicorn `
        --hidden-import aiosqlite `
        --hidden-import sqlalchemy.dialects.sqlite.aiosqlite `
        --exclude-module mediapipe --exclude-module cv2 --exclude-module tkinter `
        --workpath "$work" --specpath "$work" --distpath (Join-Path $root "desktop\dist") `
        desktop_main.py
    if ($LASTEXITCODE -ne 0) { throw "PyInstaller failed" }
} finally {
    Pop-Location
}
Write-Host "Built: $(Join-Path $root 'desktop\dist\TKD-PRO.exe')"
