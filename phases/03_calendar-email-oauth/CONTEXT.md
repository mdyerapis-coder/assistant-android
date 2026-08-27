# Phase 03 — Google OAuth connect flow

**Reads:** `docs/adr/007-google-oauth-backend-anchored.md` (important: this is NOT phone-side PKCE — the backend holds the confidential client and does the token exchange, this app only launches and catches a redirect).

**Does:** add a "Connect Google" action that launches a Custom Tab (never a WebView) at the backend's `GET /oauth/google/start`, and a deep-link receiver for `assistantapp://oauth-complete` (already reserved in the `AndroidManifest.xml` intent-filter from phase 00) that closes the Custom Tab and shows "Connected." No token handling on this side at all — the backend does the whole exchange and stores tokens itself.

**Writes:** the Custom Tab launch code, the deep-link intent-filter handling in `MainActivity.kt`, a "Connected to Google" row somewhere in settings/onboarding UI.

**Human check:** tap "Connect Google," complete the Google consent flow in the Custom Tab, confirm it redirects back into the app and shows connected — then ask the assistant something calendar/email-related in chat and confirm it actually has access (this part depends on the backend's `calendar.py`/`gmail.py` tools existing, which is backend Phase 03, not this repo — coordinate timing).

**Depends on:** Mason's manual GCP console step (extending the OAuth consent screen for Calendar/Gmail scopes, confirming the `https://assistant.llmclouds.au/oauth/google/callback` redirect URI is registered) — flagged as still open in `assistant-backend/phases/00_backend-skeleton/REPORT.md` as of this repo's scaffolding. Check that's actually done before starting this phase, not just assumed.

**When done:** write `REPORT.md`.
