# EDA V5 — Architecture Audit and Rebuild Plan

Date: 2026-10-15
Target branch: `eda-v5-native-gemma-runtime`
Scope: audit only; this document does not claim the app is production-ready.

## Current verified build
- GitHub Actions run for commit `7949fa0149b4ed00d4c6992d050d485a63ffa39d` completed successfully:
  https://github.com/emo12318786-web/eda-android/actions/runs/38767784126
- This confirms compilation/package workflow success, not runtime correctness on a Samsung Galaxy A71.

## Findings from source review

### P0 — Intent recognition is too eager
- `CommandProcessor.kt` routes many intents through fuzzy matching (`FuzzyMatcher.herhangiBiri`).
- Speech recognition can confuse Turkish words such as `konuş` (talk) and `konum` (location), causing unrelated location responses.
- Fix: deterministic command intents first, phrase-level matching for high-impact commands, then conversation fallback. Add regression tests for conversational phrases.

### P0 — Native Gemma runtime is not yet integrated
- `NativeGemmaEngine.kt`, `GemmaSessionManager.kt`, and `GemmaModelStore.kt` exist, but the Android JNI/llama.cpp implementation and packaged native library are absent.
- `AiRouter.kt` still builds an Ollama OpenAI-compatible provider using `OllamaLocator`, followed by Pollinations and Groq.
- Therefore the current APK is not independent of Termux/Ollama, and cloud fallback may be used when local inference is unavailable.

### P1 — Conversation and command handling are coupled
- The large `CommandProcessor.kt` contains command handling, fuzzy intent decisions, canned response hooks, and AI routing in one central path.
- This makes it easy for ordinary conversation to be mistaken for a command.
- Proposed split: `CommandIntentClassifier`, `CommandExecutor`, `ConversationRouter`, and `ResponsePresenter`, with explicit result types and tested precedence.

### P1 — Dashboard state needs an authoritative source
- `DashboardViewModel.kt` assembles UI status separately from foreground service state.
- User reports stale control-panel status and “closed” display while service is active.
- Proposed fix: expose service state via a single observable state source (Room/StateFlow/broadcast bridge), poll only OS values that cannot be observed, and refresh on resume plus service events.

### P1 — Service lifecycle and battery use need dedicated tests
- `EdaForegroundService.kt` owns SpeechRecognizer, TTS, wake/sleep mode, retry behavior, reminders, and panel lifecycle in a large class.
- This is a high regression-risk area. Do not rewrite it wholesale before extracting testable components and adding lifecycle/state-machine tests.

### P2 — Repository cleanup and repeatable tests
- The source tree contains many `.bak_*` files under `src/main`; Android build tools can package or expose unintended resources, and backups confuse maintenance.
- Remove backups from production source after preserving history, not before.
- Add JVM tests for command-vs-conversation classification, provider fallback policy, and state transitions.

## Rebuild architecture (incremental, branch-isolated)

1. **Intent layer:** exact/phrase-based high-impact commands; no location/phone-control action from fuzzy single-token matches; conversational utterances go to AI.
2. **Execution layer:** deterministic tool handlers with explicit success/failure results. No claim that an action succeeded unless the tool confirms it.
3. **AI provider layer:** provider interface with explicit local-only vs online-allowed policy. Offline mode must never silently send conversation to cloud.
4. **Native runtime:** pin a compatible llama.cpp revision, build arm64-v8a JNI with Android NDK/CMake, implement cancellation/unload, package the library, and test with compatible GGUF models. Do not claim Gemma is local until a real model generates tokens on device.
5. **Service state:** a single state machine for OFF / SLEEP / ACTIVE / ERROR, with observable state shared by the dashboard.
6. **Verification:** CI unit tests + APK build, then device acceptance tests for voice, battery, permission denial, reboot, model load/unload, offline inference, and conversation-vs-command cases.

## Acceptance cases before replacing the current install
- “Eda, benimle konuş” must not trigger location.
- “Neredeyim?” triggers location only when permission exists; otherwise ask for permission without misreporting location.
- “Saat kaç?” gives device time.
- “Bugün çok yoruldum, benimle konuş” goes to the selected conversation provider, not a canned template or location command.
- In local-only mode, unavailable native inference gives a clear error; it must not silently use a cloud provider.
- Dashboard ACTIVE/SLEEP/OFF state matches the actual foreground service.
- Existing install remains available until the rebuilt APK passes these checks.
