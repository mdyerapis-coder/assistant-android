"""Import-check used by the Chaquopy spike APK and (host-side) docs.

Each entry is tried with a bare `import`. Native wheels that were not packaged
show up as ModuleNotFoundError; ABI-incompatible wheels show as OSError.
"""

from __future__ import annotations

import json
import platform
import sys
import traceback

HARD = [
    "fastapi",
    "pydantic",
    "pydantic_core",
    "cryptography",
    "orjson",
    "aiohttp",
    "aiosqlite",
]

OPTIONAL = [
    "uvicorn",
    "openai",
    "httpx",
    "google.auth",
    "google_auth_oauthlib",
    "requests",
    "yaml",
    "croniter",
]

SKIPPED = {
    "firebase_admin": "dropped per ADR-012 (FCM redesign; not installed)",
    "mcp": "stdio MCP cannot spawn on Android; skipped this spike",
    "uvloop": "dropped with uvicorn[standard] -> plain uvicorn",
    "httptools": "dropped with uvicorn[standard]",
    "watchfiles": "dropped with uvicorn[standard]",
}


def _try_import(name: str) -> dict:
    row = {"package": name, "status": "fail", "version": None, "error": None}
    try:
        module = __import__(name)
        version = getattr(module, "__version__", None)
        if version is None and name == "yaml":
            version = getattr(module, "__version__", None)
        if version is None and name == "google.auth":
            import google.auth as google_auth

            version = getattr(google_auth, "__version__", None)
        row["status"] = "ok"
        row["version"] = str(version) if version is not None else "unknown"
    except Exception as exc:  # noqa: BLE001 — spike wants the real failure
        row["status"] = "fail"
        row["error"] = f"{type(exc).__name__}: {exc}"
        row["traceback"] = traceback.format_exc()
    return row


def run() -> str:
    hard_rows = [_try_import(name) for name in HARD]
    optional_rows = [_try_import(name) for name in OPTIONAL]
    skipped_rows = [
        {"package": name, "status": "skipped", "reason": reason}
        for name, reason in SKIPPED.items()
    ]
    hard_ok = all(row["status"] == "ok" for row in hard_rows)
    report = {
        "ok": hard_ok,
        "python": sys.version,
        "platform": platform.platform(),
        "implementation": sys.implementation.name,
        "hard": hard_rows,
        "optional": optional_rows,
        "skipped": skipped_rows,
    }
    return json.dumps(report, indent=2)


if __name__ == "__main__":
    print(run())
