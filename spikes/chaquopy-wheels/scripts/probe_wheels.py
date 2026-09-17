#!/usr/bin/env python3
"""Per-package Android wheel probe for the ADR-012 Chaquopy spike.

Uses stock pip with the Android wheel tags Chaquopy 16.x installs
(`cp312` / `android_24_arm64_v8a`) plus Chaquopy's official find-links
index. A second pass adds Flet's unofficial mobile index as a workaround
column — not as evidence that those wheels load inside Chaquopy's CPython.

This is a resolve/download probe, not an on-device import. Pair it with
`:spikes:chaquopy-wheels:assembleResolvedDebug` / `assembleFullDebug`.
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Iterable

PYTHON_VERSION = "312"
PLATFORM = "android_24_arm64_v8a"
CHAQUOPY_INDEX = "https://chaquo.com/pypi-13.1"
FLET_INDEX = "https://pypi.flet.dev"

# Native packages that live on Chaquopy's wheel repo (directory name == PyPI name).
CHAQUOPY_NATIVE = {
    "aiohttp",
    "cffi",
    "cryptography",
    "frozenlist",
    "multidict",
    "pyyaml",
    "yarl",
}

# Known placeholder wheels that pip will happily "resolve" but are not the real package.
STUB_VERSIONS = {"0.0.1", "0.0.0a1", "0.0.0"}

HARD = [
    ("fastapi", "fastapi>=0.115", False),
    ("pydantic", "pydantic>=2.9", False),
    ("pydantic-core", "pydantic-core>=2", True),
    ("cryptography", "cryptography>=43", True),
    ("cryptography (flavor pin 42.0.8)", "cryptography==42.0.8", True),
    ("orjson", "orjson>=3.12.0", True),
    ("aiohttp", "aiohttp>=3.9", True),
    ("aiosqlite", "aiosqlite>=0.20", False),
]

OPTIONAL = [
    ("uvicorn", "uvicorn>=0.32", False),
    ("openai", "openai>=1.50", False),
    ("jiter (openai dep)", "jiter>=0.4.0", True),
    ("httpx", "httpx>=0.27", False),
    ("google-auth", "google-auth>=2.57.0", False),
    ("google-auth-oauthlib", "google-auth-oauthlib>=1.2", False),
    ("requests", "requests>=2.34.2", False),
    ("pyyaml", "pyyaml>=6.0.3", True),
    ("croniter", "croniter>=1.0", False),
]

POLICY_SKIP = [
    ("firebase-admin", "dropped per ADR-012 (FCM redesign)"),
    ("mcp", "stdio MCP cannot spawn on Android; skipped this spike"),
    ("uvloop", "dropped with uvicorn[standard]"),
    ("httptools", "dropped with uvicorn[standard]"),
    ("watchfiles", "dropped with uvicorn[standard]"),
]


@dataclass
class ProbeResult:
    name: str
    requirement: str
    blocking: bool
    status: str
    wheel: str | None
    error: str | None
    source: str
    notes: str | None = None


def _pip_download(
    requirement: str,
    dest: Path,
    *,
    abi: str,
    find_links: Iterable[str] = (),
) -> subprocess.CompletedProcess[str]:
    dest.mkdir(parents=True, exist_ok=True)
    cmd = [
        sys.executable,
        "-m",
        "pip",
        "download",
        "--dest",
        str(dest),
        "--no-deps",
        "--python-version",
        PYTHON_VERSION,
        "--platform",
        PLATFORM,
        "--implementation",
        "cp",
        "--abi",
        abi,
        "--only-binary",
        ":all:",
        "--disable-pip-version-check",
        "--no-cache-dir",
    ]
    for link in find_links:
        cmd.extend(["--find-links", link])
    cmd.append(requirement)
    return subprocess.run(cmd, check=False, capture_output=True, text=True)


def _wheel_in(dest: Path) -> Path | None:
    wheels = sorted(dest.glob("*.whl"))
    return wheels[0] if wheels else None


def _version_from_wheel(path: Path) -> str | None:
    # {name}-{version}(-{build})?-{python}-{abi}-{platform}.whl
    match = re.match(r"^(.+)-([0-9][^-]*)-", path.name)
    if not match:
        return None
    return match.group(2)


def _find_links_for(req: str, extra_indexes: Iterable[str]) -> list[str]:
    name = re.split(r"[<>=!~\[]", req, maxsplit=1)[0].strip().lower()
    links: list[str] = []
    if name in CHAQUOPY_NATIVE:
        links.append(f"{CHAQUOPY_INDEX}/{name}/")
    for index in extra_indexes:
        links.append(f"{index.rstrip('/')}/{name}")
    return links


def probe_one(
    name: str,
    requirement: str,
    *,
    native: bool,
    dest_root: Path,
    extra_indexes: Iterable[str],
    source: str,
) -> ProbeResult:
    slug = re.sub(r"[^a-zA-Z0-9._-]+", "_", name)
    dest = dest_root / source / slug
    if dest.exists():
        for leftover in dest.glob("*"):
            leftover.unlink()
    find_links = _find_links_for(requirement, extra_indexes)
    abis = ["cp312", "none"] if native else ["none", "cp312"]
    last_error = None
    for abi in abis:
        proc = _pip_download(requirement, dest, abi=abi, find_links=find_links)
        if proc.returncode == 0:
            wheel = _wheel_in(dest)
            if wheel is None:
                last_error = "pip reported success but wrote no wheel"
                continue
            version = _version_from_wheel(wheel)
            if version in STUB_VERSIONS:
                return ProbeResult(
                    name=name,
                    requirement=requirement,
                    blocking=native,
                    status="fail",
                    wheel=wheel.name,
                    error=f"stub/placeholder wheel {version} does not satisfy a real Android build",
                    source=source,
                    notes=proc.stderr.strip()[-400:] or None,
                )
            return ProbeResult(
                name=name,
                requirement=requirement,
                blocking=native,
                status="ok",
                wheel=wheel.name,
                error=None,
                source=source,
                notes=f"abi={abi}",
            )
        last_error = (proc.stderr or proc.stdout).strip().splitlines()[-1] if (proc.stderr or proc.stdout) else f"exit {proc.returncode}"
    return ProbeResult(
        name=name,
        requirement=requirement,
        blocking=native,
        status="fail",
        wheel=None,
        error=last_error,
        source=source,
    )


def run(dest_root: Path) -> dict:
    dest_root.mkdir(parents=True, exist_ok=True)
    official: list[ProbeResult] = []
    flet: list[ProbeResult] = []
    for name, req, native in HARD + OPTIONAL:
        official.append(
            probe_one(
                name,
                req,
                native=native,
                dest_root=dest_root,
                extra_indexes=(),
                source="chaquopy+pypi",
            )
        )
        flet.append(
            probe_one(
                name,
                req,
                native=native,
                dest_root=dest_root,
                extra_indexes=(FLET_INDEX,),
                source="chaquopy+pypi+flet",
            )
        )
    skipped = [
        {
            "name": name,
            "status": "skipped",
            "reason": reason,
        }
        for name, reason in POLICY_SKIP
    ]
    hard_names = {row[0] for row in HARD}
    official_hard = [row for row in official if row.name in hard_names]
    go = all(row.status == "ok" for row in official_hard if "flavor pin" not in row.name)
    # cryptography>=43 is the backend constraint; flavor pin is the substitution.
    report = {
        "python": sys.version,
        "target": {
            "python": PYTHON_VERSION,
            "platform": PLATFORM,
            "implementation": "cp",
        },
        "indexes": {
            "official": ["https://pypi.org/simple", CHAQUOPY_INDEX],
            "workaround": [FLET_INDEX],
        },
        "official": [asdict(row) for row in official],
        "flet_workaround": [asdict(row) for row in flet],
        "skipped": skipped,
        "option_a_official_wheels": "go" if go else "no-go",
    }
    return report


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--dest",
        type=Path,
        default=Path(__file__).resolve().parent.parent / "build" / "wheel-probe",
    )
    parser.add_argument(
        "--json-out",
        type=Path,
        default=None,
        help="Write the matrix JSON here (in addition to stdout).",
    )
    args = parser.parse_args()
    report = run(args.dest)
    text = json.dumps(report, indent=2)
    print(text)
    json_out = args.json_out
    if json_out is None:
        json_out = Path(__file__).resolve().parents[3] / "docs" / "spikes" / "chaquopy-wheel-matrix.json"
    json_out.parent.mkdir(parents=True, exist_ok=True)
    json_out.write_text(text + "\n")
    print(f"\nWrote {json_out}", file=sys.stderr)
    return 0 if report["option_a_official_wheels"] == "go" else 1


if __name__ == "__main__":
    sys.exit(main())
