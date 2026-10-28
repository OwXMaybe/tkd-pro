"""Small admin CLI.

    python -m app.cli new-token     # prints a new owner token and its SHA-256 for OWNER_TOKEN_SHA256
"""

import sys

from .security import new_token


def main(argv: list[str]) -> int:
    if len(argv) >= 1 and argv[0] == "new-token":
        token, digest = new_token()
        print("Owner token (enter this in the app, keep it secret):")
        print(f"  {token}")
        print("Server setting (.env):")
        print(f"  OWNER_TOKEN_SHA256={digest}")
        return 0
    print(__doc__)
    return 1


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
