# ADR-012 step 1 — Chaquopy wheel-availability spike

**Verdict: no-go for Option A** (embed the existing FastAPI backend via
Chaquopy official wheels).

This spike packages an isolated application module (`:spikes:chaquopy-wheels`)
that is **not** a product dependency of `:app`. It does not implement
loopback uvicorn, OAuth, FCM, WorkManager, or SMS. Wheels only.

Source ADR:
[012-embedded-backend-in-apk.md](https://github.com/mdyerapis-coder/assistant-backend/blob/master/docs/adr/012-embedded-backend-in-apk.md)
(backend repo). Paper decisions in ADR-013 (OAuth relay, WorkManager, in-process
SMS) are out of scope.

## How this was run

| Check | Command | What it proves |
|---|---|---|
| Per-package resolve | `python3 spikes/chaquopy-wheels/scripts/probe_wheels.py` | pip can/cannot download an `cp312` / `android_24_arm64_v8a` wheel from PyPI + [Chaquopy pypi-13.1](https://chaquo.com/pypi-13.1/) |
| Chaquopy pip (resolved set) | `./gradlew :spikes:chaquopy-wheels:assembleResolvedDebug` | **SUCCESS** — plugin installed the packages that resolved |
| Chaquopy pip (intended set) | `./gradlew :spikes:chaquopy-wheels:assembleFullDebug` | **FAILED** — `cryptography>=43` sdist (`maturin` missing); after pin, `orjson>=3.12.0` sdist |
| On-device import | install the `resolved` APK, read logcat `ChaquopySpike` | **not run** (no emulator/device in this environment) |

Machine used for the resolve probe: CPython 3.12.3 on Linux x86_64. Chaquopy
**16.1.0**, app Python **3.12**, ABIs `arm64-v8a` + `x86_64`, AGP 8.7.3.

Raw matrix: [`chaquopy-wheel-matrix.json`](./chaquopy-wheel-matrix.json).

## Hard checklist (ADR-012)

Official index = PyPI simple + Chaquopy `pypi-13.1` find-links. "Import" here
means "would be packaged"; on-device import is a separate column filled when
the `resolved` APK has been launched.

| Package | Backend constraint | Official Android wheel | Notes |
|---|---|---|---|
| fastapi | `>=0.115` | **ok** (`fastapi-0.141.1-py3-none-any.whl`) | Pure Python. **Cannot import** without `pydantic-core>=2`. |
| pydantic | (via fastapi `>=2.9`) | **ok** (`pydantic-2.13.5-py3-none-any.whl`) | Pure Python wrapper. Requires `pydantic-core==2.46.5`. |
| pydantic-core | `>=2` (exact pin from pydantic 2.13.5: `==2.46.5`) | **fail** | No Android wheel on PyPI or Chaquopy. Unpinned `pydantic-core` resolves to a **0.0.1 stub** `py3-none-any` wheel — treat as fail. |
| cryptography | `>=43` | **fail** | Chaquopy's newest wheel is **42.0.8**. `>=43` does not resolve. Flavor pin `cryptography==42.0.8` **ok**. |
| orjson | `>=3.12.0` | **fail** | Not in Chaquopy's native repo; no `android_*` wheel on PyPI. |
| aiohttp | `>=3.9` | **ok** | `aiohttp-3.10.10-0-cp312-cp312-android_24_arm64_v8a.whl` from Chaquopy. |
| aiosqlite | `>=0.20` | **ok** | `aiosqlite-0.22.1-py3-none-any.whl`. Uses stdlib `sqlite3` (supported by Chaquopy). |

**Hard-checklist result: 3/6 blocking packages fail to package as the backend
is specified** (`pydantic-core`, `cryptography>=43`, `orjson`). FastAPI/pydantic
wheels exist but are runtime-dead without pydantic-core. That is a failed gate.

## Optional / related (non-blocking if fail)

| Package | Backend constraint | Official Android wheel | Notes |
|---|---|---|---|
| uvicorn (plain) | `uvicorn[standard]>=0.32` → drop extras | **ok** | `uvicorn-0.53.0-py3-none-any.whl` |
| uvloop / httptools / watchfiles | via `uvicorn[standard]` | **fail / stub** | Confirms the ADR substitution. Do not install. |
| openai | `>=1.50` | wheel **ok** (pure Python) | Runtime-blocked: `jiter` has **no** official Android wheel. Even `openai==1.50.0` requires `jiter>=0.4`. |
| jiter | openai dep | **fail** | Rust extension; not on Chaquopy/PyPI Android. |
| httpx | (openai / optional) | **ok** | `httpx-0.28.1-py3-none-any.whl` |
| google-auth | `>=2.57.0` | **ok** | `google_auth-2.58.0-py3-none-any.whl` |
| google-auth-oauthlib | `>=1.2` | **ok** | `google_auth_oauthlib-1.4.1-py3-none-any.whl` |
| requests | `>=2.34.2` | **ok** | `requests-2.34.2-py3-none-any.whl` |
| pyyaml | `>=6.0.3` | **ok** | `pyyaml-6.0.3-0-cp312-cp312-android_24_arm64_v8a.whl` from Chaquopy |
| croniter | `>=1.0` | **ok** | `croniter-6.2.4-py3-none-any.whl` |
| cffi | cryptography dep | **ok** | `cffi-1.17.1-1-cp312-cp312-android_24_arm64_v8a.whl` |

## Policy skips (not installed)

| Package | Status | Reason |
|---|---|---|
| firebase-admin | skipped | ADR-012: drop; FCM redesign (ADR-013 / later). |
| mcp | skipped | Wheel would resolve (`mcp-2.2.0-py3-none-any.whl`) but stdio servers cannot spawn on Android. HTTP-only MCP is a later product decision. |

## Unofficial workaround index (not the go/no-go)

[pypi.flet.dev](https://pypi.flet.dev) publishes additional `android_24_*`
wheels. They are **not** Chaquopy-built; loading them in Chaquopy's CPython is
unproven (this spike did not import them on-device). Even as a resolve-only
exercise they do not rescue the current pins:

| Package | Flet resolve (`>=` constraint) | Why it still doesn't save Option A as specified |
|---|---|---|
| pydantic-core | **ok** `2.47.0` (`pydantic_core-2.47.0-10-cp312-cp312-android_24_arm64_v8a.whl`) | Current pydantic 2.13.5 requires **exactly** `pydantic-core==2.46.5`, which Flet does not publish. |
| cryptography | **ok** `43.0.1` | Satisfies `>=43`, but this is not a Chaquopy-built wheel. Unproven inside Chaquopy's CPython. |
| orjson | **fail** (`>=3.12.0`); 3.11.9 exists | Backend wants `>=3.12.0`. |
| jiter | **ok** `0.15.0` against `>=0.4` | Current openai 3.x wants `jiter>=0.16`. 0.15.0 only pairs with an older openai pin. |

Using Flet (or home-built cibuildwheel Android wheels) would be a **different
project**: extra indexes, ABI/16 KB-page risk, and a pin list that drifts from
`assistant-backend`. It does not green-light "keep this codebase, days-to-weeks."

## Recommended Android-flavor pin list (if Option A is forced anyway)

These are the pins that match **official** Chaquopy 16.1 / cp312 / android-24
wheels. They are **not** sufficient to run the current backend.

```
uvicorn>=0.32          # not uvicorn[standard]
cryptography==42.0.8   # backend is >=43; this is a downgrade
aiohttp>=3.9,<=3.10.10
pyyaml>=6.0.3
aiosqlite>=0.20
google-auth>=2.57.0
google-auth-oauthlib>=1.2
requests>=2.34.2
croniter>=1.0
httpx>=0.27
# DROP: firebase-admin, mcp (stdio), uvloop, httptools, watchfiles
# BLOCKED: fastapi/pydantic (need pydantic-core>=2), orjson>=3.12, openai (need jiter)
```

## APK size delta

Measured from this spike module (Chaquopy 16.1, Python 3.12, **two** ABIs
`arm64-v8a` + `x86_64`, debug, unsigned). Not the product `:app` APK
(that build needs `google-services.json`, which is not in this repo).

| Variant | Gradle task | APK | Delta vs empty |
|---|---|---|---|
| empty (interpreter + stdlib only) | `:spikes:chaquopy-wheels:assembleEmptyDebug` | **37.17 MiB** (38,978,700 bytes) | — |
| resolved wheels | `:spikes:chaquopy-wheels:assembleResolvedDebug` | **44.59 MiB** (46,754,704 bytes) | **+7.42 MiB** |
| full intended set | `:spikes:chaquopy-wheels:assembleFullDebug` | did not package | pip failed (see below) |

Inside the resolved APK, Chaquopy packed native requirements as
`assets/chaquopy/requirements-{arm64-v8a,common,x86_64}.imy` (~7.4 MiB
uncompressed), which matches the empty→resolved delta. The interpreter
itself is the expensive part: `libpython3.12.so` is ~6.4 MiB **per ABI**,
plus OpenSSL/SQLite stubs. An arm64-only production flavor would drop
roughly one ABI's native payload (~12 MiB uncompressed `.so`s plus ~1.5 MiB
x86_64 requirement/stdlib imys) — still tens of MB, in the same band as
ADR-012's "40–80 MB extra" once you add the product app's existing 125 MB.

## Chaquopy Gradle evidence

**Resolved set: BUILD SUCCESSFUL** (`assembleResolvedDebug`, 2m 11s).
Chaquopy pip used `https://pypi.org/simple` + `https://chaquo.com/pypi-13.1`
and installed for both ABIs. Native wheels that actually came from Chaquopy:

- `cryptography==42.0.8` (`android_24_arm64_v8a` / `android_24_x86_64`)
- `aiohttp==3.10.10`
- `cffi==1.17.1`, `chaquopy-libffi`, `chaquopy-libyaml`
- `pyyaml==6.0.3`
- `frozenlist==1.4.0`, `multidict==6.0.4`

Pure-Python (PyPI `py3-none-any`) that rode along: uvicorn 0.53.0,
aiosqlite 0.22.1, google-auth 2.58.0, google-auth-oauthlib 1.4.1,
requests 2.34.2, httpx 0.28.1, croniter 6.2.4, plus their pure-Python deps.

**Intended set: BUILD FAILED** (`assembleFullDebug`). Log:
[`chaquopy-full-pip.txt`](./chaquopy-full-pip.txt).

1. `cryptography>=43` → pip selected **cryptography-50.0.1.tar.gz** (no
   Android wheel) → `FileNotFoundError: maturin`.
2. After a one-shot pin to `cryptography==42.0.8` (file restored; not
   committed) → `orjson>=3.12.0` selected **orjson-3.12.0.tar.gz** → same
   maturin failure.

Chaquopy 16.1 does **not** use `--only-binary`, so missing native wheels
become failed source builds rather than a clean "no matching distribution".
Either way the intended stack does not install.

**On-device import:** not run. This environment has no Android emulator or
arm64 device. The `resolved` APK *packages* the native `.so`s (cryptography,
aiohttp, cffi, pyyaml, …) and the activity will execute
`wheel_import_check.run()` on launch (logcat tag `ChaquopySpike`). Until that
APK is opened on hardware, do not claim those native modules import; only
that Chaquopy resolved and packaged them for `android_24_arm64_v8a`.
Hard-checklist packages that were **not** packaged (`fastapi`/`pydantic` via
pydantic-core, `orjson`) would `ModuleNotFoundError` in that same check.

## Go / no-go

**No-go for Option A** as ADR-012 framed it: "Chaquopy ships prebuilt Android
wheels for pydantic-core, cryptography, orjson, aiohttp" and the existing
`pyproject.toml` rides along after three substitutions.

What the evidence actually shows:

1. **pydantic-core ≥2 has no official Chaquopy/PyPI Android wheel.** FastAPI 0.115
   and pydantic v2 therefore cannot run. This is the hard kill.
2. **orjson ≥3.12 has no official Android wheel.** Replaceable with stdlib json
   at a behavior/perf cost; not a solo kill, but it is on the ADR checklist.
3. **cryptography ≥43 has no official Android wheel** (42.0.8 does). That is a
   forced downgrade, not a drop-in.
4. **openai ≥1.50 needs jiter**, which also has no official Android wheel.
   Cloud-provider chat from an embedded backend is blocked on the same class
   of native-wheel gap.
5. aiohttp, aiosqlite, plain uvicorn, pyyaml, google-auth, requests, croniter
   **do** resolve. Those successes are not enough to embed *this* backend.

Fallback per ADR-012: Option C (deepen on-device mode) if the goal is "works
without the server"; Option B (Kotlin/Ktor rewrite) if the embed ever needs
to be the primary product. Revisit Option A only with a funded Android-wheel
pipeline (cibuildwheel / Chaquopy recipes) and a pin freeze — that is not
step 1 passing.

## What this repo contains

- `:spikes:chaquopy-wheels` — Chaquopy 16.1 application, three flavors
  (`empty` / `resolved` / `full`)
- `spikes/chaquopy-wheels/scripts/probe_wheels.py` — rerunnable matrix
- This report + `docs/spikes/chaquopy-wheel-matrix.json`
