# EDA V5 native offline Gemma migration

## Goal

The shipped Android app must run local Gemma inference inside the app process. Termux, Ollama, and the Ollama HTTP endpoint must not be runtime requirements. The legacy Termux project and the default `main` branch remain untouched while this work is developed.

## Source review confirmed

- `AiRouter.kt` still imports `OllamaLocator` and calls its local OpenAI-compatible endpoint. The current app is therefore still dependent on Ollama for that provider.
- The EDA app uses Android Gradle Plugin 8.5.2, Kotlin 1.9.24, compileSdk 35, and minSdk 26.
- The official upstream `ggml-org/llama.cpp/examples/llama.android` is a real JNI/native inference implementation, but its current Android library declares compileSdk 36, minSdk 33, NDK 29, CMake 3.31.6, AGP 8.13.2, and Kotlin 2.3.0. It cannot safely be dropped into EDA unchanged: it would raise the app's minimum Android version or require a deliberate compatibility adaptation and toolchain update.
- The official sample exposes `AiChat.getInferenceEngine(context)`, `InferenceEngine.loadModel(path)`, `setSystemPrompt`, and token streaming via Kotlin Flow. Its native code builds llama.cpp from source using CMake. This is the strongest upstream integration candidate found so far; it is not a ready-made Maven dependency.
- The Ollama tags `gemma3:1b` and `gemma2:2b` are not guaranteed to be portable GGUF files. The app must import compatible GGUF model files, not Ollama's internal blob paths.
- A green GitHub Actions build proves compilation/package creation, not on-device inference.

## Work currently present on this branch

- `GemmaModelStore.kt` imports GGUF files into app-private storage, checks the GGUF magic header, and calculates SHA-256.
- `NativeGemmaEngine.kt` defines a fail-closed JNI contract. It intentionally does not claim to infer until `libeda_llama.so` and matching JNI functions are actually packaged.
- This work remains on the draft PR branch; `main` and the existing installed app are unchanged.

## Next implementation sequence

1. Pin a specific upstream llama.cpp revision and integrate its native CMake/JNI code. Decide deliberately whether to adapt the upstream Android library for EDA's existing minSdk/toolchain or raise those requirements; do not silently change minimum Android support.
2. Package the `arm64-v8a` native library for the Galaxy A71 and expose safe load/unload/generate/cancel operations through one engine instance.
3. Connect native inference to `AiRouter` without disturbing deterministic command/tool routing. Keep model loading lazy and load only one model at a time: Gemma 3 1B for lightweight intent/short responses and Gemma 2 2B for heavier dialogue when selected.
4. Keep network providers optional and never use them as an implicit substitute for an offline response. Remove the Ollama provider from the native-only route only after native inference is wired and tested.
5. Add automated checks, build the APK, then install on the real Galaxy A71 and verify cold start, offline generation, model switching/unload, cancellation, memory, battery, and thermal behavior.

## Important limitation

This branch is still a development scaffold, not a completed standalone model runtime. Do not merge or label an APK as Ollama-free until native inference is bundled, connected to the router, and tested on-device. The model store only imports and validates GGUF files; it does not perform inference.
