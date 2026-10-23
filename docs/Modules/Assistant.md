# 🤖 Assistant

> Status: **Planned** (Stage 1A, v0.3.1)

---

← **[Back to Modules](README.md)**

---

## Purpose

The Assistant module gives family members an AI chat inside AMBiFy. Requests go through `llm_gateway`, a service on the home server that runs the official Claude, GPT (Codex), Gemini and Copilot CLIs on our own subscriptions instead of a paid API.

Other modules (Calendar, School, Dashboard) and scripts such as vulcan_digest can use the same gateway; each client sends the data it needs with the request, and the gateway itself knows nothing about school or calendars.

## Gateway

- One HTTP API: create a job (text + file attachments), read its result, list available models and their state.
- Model choice: `auto` picks a model by task type (rules in `routing.yaml`), `manual` forces the chosen one.
- Fallback: when a subscription limit is hit the gateway moves to the next model; the paid API is used only when the client allows it.
- Conversation history is kept on the server, so the model can be changed in the middle of a chat.
- Answers are streamed to the app.

## Security

- The app never talks to the gateway directly: `:server` checks the account and forwards the request (children's limits come with Stage 3).
- The gateway listens on the home server only and requires a token.
- CLIs run as plain models without tools, each job in its own empty working directory (school messages are external, untrusted text).
- Attachments have a size limit and an allowed-type list (pdf, png, jpg, txt, csv, docx).

## App

- Chat screen with a model picker (Auto / Claude / GPT / Gemini) and attachments.
- Layout is approved by the owner before implementation.

## Later (optional)

- Voice conversation: speech-to-text on the phone → gateway (streamed answer) → text-to-speech; a few seconds of delay, stop button.
- True live mode (continuous audio) is not possible through the CLIs; it would use the Gemini Live API with a short-lived token issued by `:server`.
- Local Bielik model (Ollama) for short offline or private tasks.
