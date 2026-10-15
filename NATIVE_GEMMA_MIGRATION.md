# EDA V5 native offline Gemma migration

## Goal

The shipped Android app must run local Gemma inference inside the app process. Termux, Ollama, and the Ollama HTTP endpoint must not be runtime requirements. The legacy Termux project and the default `main` branch remain untouched while this work is developed.

## Current facts confirmed from source

- `AiRouter.kt` currently imports `OllamaLocator` and calls its local OpenAI-compatible endpoint. This is still an Ollama dependency.
- `app/build.gradle.kts` does not currently declare an embedded llama.cpp Android runtime.
- The installed Ollama tags are `gemma3:1b` and `gemma2:2b`; Ollama's internal model storage is not itself a portable GGUF file path.
- A green GitHub Actions build proves compilation/package creation, not on-device inference.

## Work started on this branch

- Add app-private GGUF import/storage for two named model slots. The app uses Android's document picker / Storage Access Framework as the intended source of model files; imported files are copied into app-private storage and checked for the GGUF magic header.
- Keep model import separate from inference so the UI cannot claim a model is running before a runtime exists.

## Required implementation gates (not yet complete)

1. Add and pin a maintained Android-compatible llama.cpp JNI/runtime dependency, or build the native runtime from a pinned upstream revision using NDK/CMake.
2. Add a JNI wrapper with explicit load/unload/infer operations and cancellation; verify ABI support for Samsung A71 (arm64-v8a).
3. Route `gemma3:1b` and `gemma2:2b` requests through the native runtime, unload the previous model before loading the other, and keep direct device commands ahead of LLM inference.
4. Remove `OllamaLocator`, loopback port 11434, and local OpenAI-compatible Ollama provider from the native-only path. Online providers may remain optional if the product still needs them, but must not be required for offline operation.
5. Add tests and CI checks, then install and test on the real Galaxy A71 for memory use, cold start, cancellation, thermal behavior, and offline replies.

## Important limitation

This branch is a development scaffold, not a completed standalone model runtime. Do not merge or label an APK as Ollama-free until gates 1–5 pass. The model store only imports and validates GGUF files; it does not perform inference.
