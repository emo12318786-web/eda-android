# EDA V5 — Screen Reader / Phone Harness Gap Audit

Branch: `eda-phone-harness-complete`  
Scope: add local, opt-in screen reading to the existing Android application while preserving the current foreground service, wake/sleep flow and Ollama deep-chat path.

## Implemented in this branch

- Registers an optional Android AccessibilityService that the user must enable manually in Android Accessibility settings.
- Reads visible, non-password text exposed by the accessibility tree.
- When the tree exposes no text, attempts an Android 11+ screenshot and runs bundled ML Kit text recognition on-device.
- Adds a Turkish dashboard section to enable the service in Settings and manually request a screen read.
- Exposes `READ_SCREEN` as an Agent tool, only for a clear explicit screen-reading request.
- Filters screen-reading requests to the local Ollama provider. If local Ollama fails, the request does not fall through to Pollinations/Groq. Ordinary conversations keep their existing provider routing.
- Adds tests for recognizing explicit screen-reading requests in Turkish, English and Persian, and for rejecting unrelated commands.
- CI runs `testDebugUnitTest` and `assembleDebug` before publishing the debug APK artifact.

## Privacy and interaction boundaries

- The service is read-only. This branch does not add generic automatic screen tapping, typing, message sending, purchasing, deletion or confirmation. Those actions remain manual.
- Password fields are skipped. Android secure/DRM surfaces and apps that hide their accessibility tree or block screenshots may return no text.
- ML Kit's bundled recognizer targets Latin script. Persian/Arabic-script text may still be read if an app exposes it through accessibility nodes, but screenshot-only OCR for those scripts is not covered by this dependency.
- Screen captures and recognized text are processed in memory, not stored in app files, and are not deliberately transmitted to online AI providers. Use the local-only path for screen reading.
- Accessibility is a powerful Android permission. The user must enable the service knowingly and can disable it in Android settings.

## Verification still required

- GitHub Actions must pass for the latest branch commit, including unit tests and APK assembly.
- Install the artifact on the Samsung Galaxy A71 / Android 13 and check enabling/disabling the service, accessibility-tree reading, OCR fallback, secure screens, voice requests, TTS and battery impact.
- This source audit does not count as physical-device validation. Do not describe the build as device-tested until those checks are performed.

## Compatibility constraints preserved

This branch does not modify `EdaForegroundService.kt`, sleep/rest scheduling, wake-word handling, SMS translation, or the existing Termux/Ollama model configuration for deep chat.
