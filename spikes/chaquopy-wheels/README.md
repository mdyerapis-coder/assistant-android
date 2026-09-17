# Chaquopy wheel-availability spike (ADR-012 step 1)

Isolated Android **application** module. It is **not** a dependency of `:app`
and is not shipped in the product APK.

## What this is

Prove or kill Option A (embed the existing FastAPI backend via Chaquopy) at
the wheel layer only. No loopback server, OAuth, FCM, or foreground service.

## Commands

Per-package resolve (no Android SDK required beyond `python3` + `pip`):

```bash
python3 spikes/chaquopy-wheels/scripts/probe_wheels.py
```

Chaquopy pip, resolved set (packages that actually have Android wheels):

```bash
./gradlew :spikes:chaquopy-wheels:assembleResolvedDebug
```

Chaquopy pip, intended Android-flavored backend set (expected to fail):

```bash
./gradlew :spikes:chaquopy-wheels:assembleFullDebug
```

Interpreter-only APK (size baseline):

```bash
./gradlew :spikes:chaquopy-wheels:assembleEmptyDebug
```

On a device or emulator, open the `resolved` APK. The activity writes
`chaquopy-wheel-import-report.json` under the app files dir and to logcat
tag `ChaquopySpike`.

## Layout

| Path | Role |
|---|---|
| `requirements-android.txt` | Android flavor of backend deps (uvicorn plain; no firebase-admin; no mcp) |
| `requirements-android-resolved.txt` | Subset that resolved on the official Chaquopy index |
| `src/main/python/wheel_import_check.py` | Import matrix invoked from Kotlin |
| `scripts/probe_wheels.py` | Reproducible pip download probe |

Report: [`docs/spikes/chaquopy-wheels.md`](../../docs/spikes/chaquopy-wheels.md).
