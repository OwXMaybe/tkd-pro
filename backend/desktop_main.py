"""Entry point for the packaged Windows app (TKD-PRO.exe). See app/desktop.py."""

import sys

from app.desktop import main

if __name__ == "__main__":
    sys.exit(main())
