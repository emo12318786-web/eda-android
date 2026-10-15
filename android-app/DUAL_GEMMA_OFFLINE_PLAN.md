# EDA V5 — dual offline Gemma integration plan

## Goal
Run local language models inside the existing Android APK, without requiring Termux or an Ollama server at runtime. Keep the Termux EDA untouched.

## Intended routing
- Deterministic CommandProcessor / SystemTools first for explicit phone actions. Local models must never execute arbitrary shell commands or bypass the existing allowlist.
- Gemma 3:1B as the lightweight intent/command-understanding model.
- Gemma 2:2B as the on-demand conversation/reasoning model.
- Only one model loaded at a time on the Galaxy A71; unload it after idle/use and fall back safely if memory is low.
- Preserve the existing online provider fallback and all existing EDA features.

## Implementation stages
1. Add a native llama.cpp Android runtime for arm64-v8a and a Kotlin inference adapter.
2. Add a Storage Access Framework model picker/import flow. Keep large GGUF files outside the APK, copy them into app-private storage, and persist paths. Never assume Ollama's SHA256 blob is a standalone GGUF until its format is verified.
3. Add role-based routing and model lifecycle management (load one model at a time, unload, errors, cancellation).
4. Add tests for routing/fallback and build the APK in CI.
5. Test both models on a physical Samsung Galaxy A71 before calling the feature device-verified.

## Current repository facts
- Existing AI routing is in android-app/app/src/main/java/com/deniz/eda/ai/AiRouter.kt.
- Persistent preferences are in android-app/app/src/main/java/com/deniz/eda/core/Settings.kt.
- The Android app currently uses an Ollama-compatible HTTP endpoint for the Gemma provider; it does not yet embed a native inference engine.
- The official llama.cpp Android example builds native code from the llama.cpp source tree. Its library cannot simply be added as an ordinary Maven dependency.

## Safety / compatibility constraints
- Do not edit the separate Termux EDA or its Ollama models.
- Do not bundle multi-gigabyte models into the APK.
- Do not remove existing tools, dashboard, memory, diary, reminders, voice functions, or online provider fallback.
- A successful CI build is not proof of real inference; physical-device testing is still required.
