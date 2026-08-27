# Phase 02.5 — reminder push delivery

**Reads:** `docs/plan.md` §3 (phase table — this is explicitly separately shippable from reminder *creation*, which is backend-only and doesn't touch this repo), `docs/adr/006-reminder-creation-vs-delivery.md`.

**Does:** register for FCM, send the device token to the backend (new backend endpoint — coordinate with `assistant-backend`, this is the one phase where the two repos need to agree on something new beyond `docs/CONTRACT.md`), handle incoming push notifications (tap → open the app to the relevant conversation). Requires a Firebase project + service account — Mason sets this up manually first (new external dependency, not yet done as of this repo's scaffolding).

**Writes:** FCM registration code (likely in `core:network` or a new small module), notification handling in `app/`.

**Human check:** create a reminder via chat with a near-future `due_at`, confirm a push notification actually arrives on the phone when it fires (not just that the backend logged firing it).

**When done:** write `REPORT.md`, including the Firebase project id used (not the service account key itself).
