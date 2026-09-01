# Sable — Positioning, Landing Copy, Tiers

**Name:** Sable — *silent + able*. Warm sand, terracotta ember, quiet but capable. Not a chat app that shouts; a background assistant that remembers, checks, and acts.

**One-line:** Your quiet assistant on Android — private when possible, cloud when useful.

## Positioning

Sable is the phone-side half of a personal assistant that never talks to OpenAI or Google directly from the phone. Every tool (reminders, calendar, email, SMS, device control) runs server-side where SQLite, OAuth refresh tokens, and keys can be held safely. The phone keeps only a bearer token in the Android Keystore, renders an SSE stream, and offers on-device voice (STT/TTS never leaves the device) and an on-device LLM option for offline chats.

Why it exists: ChatGPT/Claude are loud, cloud-only, and forget. Sable is calm, content-first (conversation is the star, chrome recedes), dark-first terracotta identity, with two-tier memory (small `user_facts` injected every turn + searchable history) and a sessions-as-home model where threads sync from the server.

Differentiators:
- **Server-mediated privacy** — phone never holds refresh tokens or API keys.
- **Continuity** — threads persist by bearer token; fresh install or second device resumes same history (server is source of truth, Room is an offline cache).
- **On-device when it matters** — 48dp mic with pulse, TTS toggle, local-model download/install/delete, all via SpeechRecognizer/TextToSpeech on device.
- **Calm surface** — assistant plain text (no bubble), user subtle bubble, neutral surfaces, single terracotta accent, slim header with overflow (⋮), suggestion chips that scroll instead of clipping.

## Landing copy (hero + 3 blocks)

**Hero**
> **Sable. Quietly capable.**
> An Android assistant that remembers what matters, checks your calendar and email, sets reminders, and handles SMS — without sending your keys or tokens to your phone. Dark-first, terracotta-warm, and offline-ready.

CTA: **Install Sable** · **See how it works**

**How it works**
1. **You talk.** Type, dictate (mic), or share text/URLs from any app. Deep links `sableapp://session/{id}` reopen threads.
2. **Sable streams.** The backend runs tools, remembers `user_facts`, and streams `data: {…}\n\n` SSE frames. The phone just renders.
3. **You stay in control.** Retry/re-configure banner when the server is unreachable, clear conversation, toggle spoken replies, or switch to on-device LLM.

**Why Sable, not another wrapper**
- No WebView OAuth — Custom Tab to `https://sable.llmclouds.au/oauth/google/callback`.
- No silent auto-summarization when memory fills — it fails loudly and asks the model to consolidate (ADR-008).
- FCM relay for reminders, SMS, and device control (notifications/media) — same `POST /v1/sms/results` / `POST /v1/device/results` pattern.

## Tiers (v1-ship proposal)

| Tier | Who it’s for | What you get | Price anchor |
|------|--------------|--------------|--------------|
| **Sable Local** | Privacy-first, offline users | On-device LLM (download/delete via `LlmInferenceService`), local chat history, no calendar/email/SMS | Free — APK from GitHub/releases |
| **Sable Cloud (BYO)** | Power users who already pay OpenAI | Everything in Local + `https://sable.llmclouds.au` backend self-hosted or pointed at your VPS, BYO `OPENAI_API_KEY` + Google OAuth (Calendar/Gmail read/send), reminders, SMS relay, device control, FCM push | Free app + you pay your OpenAI + VPS (~$5/mo). No Sable fee in v1 |
| **Sable Hosted** | Non-technical, wants it to just work | Cloud plus Sable runs `sable.llmclouds.au` for you: managed SQLite backup, uptime alerting, Bitwarden-synced `assistant.env`, 7-day calendar `list_upcoming_calendar_events` live-verified, no self-hosting | **$9/mo** early, **$15/mo** after v1 — or **$90/yr** — 14-day trial. One bearer token, one backend, your phone never sees the keys. |

Why no per-message markup: the backend streams OpenAI directly (no proxy tax). Hosted cost is hosting + ops, not inference arbitrage. This keeps the door open for a future “Sable Local Pro” that bundles a pre-downloaded 7B quantized model for fully offline use.

## FAQ (landing footer)

**Does Sable listen all the time?** No wake word, no always-listening. Mic only when you tap (RECORD_AUDIO runtime, pulse animation).

**What if I lose the server?** Banner `Can’t reach your assistant server — Retry / Re-configure` appears; Room keeps last-synced threads for offline read.

**Can I move phones?** Same bearer token → same threads on second device (server-side persistence).

**What’s the app ID?** `com.mdyerapis.sable`, scheme `sableapp://oauth-complete`, base `https://sable.llmclouds.au`.

---
*Terracotta #D97757 / #E08A6B, surfaces #171717/#212121/#F5F4EF. See `DESIGN.md` for tokens.*
