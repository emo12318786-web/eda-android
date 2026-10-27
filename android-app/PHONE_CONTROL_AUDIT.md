# EDA V5 — Phone Harness-Inspired Screen Control Audit

Date: 2026-10-27  
Branch: `eda-jarvis-harness-theme-final`  
Scope: the Android app under `android-app/`. This is an implementation audit, not a claim of physical-device certification.

## Implemented in the main EDA Android module

- A single registered Android `AccessibilityService` in `com.deniz.eda.phone.PhoneControlService`. Duplicate service implementations and duplicate manifest declarations were removed.
- Accessibility-tree text reading from the active window.
- Android 11+ screenshot capture through `AccessibilityService.takeScreenshot`, enabled in the service metadata.
- Bundled ML Kit Latin text recognition for local/offline OCR; the result includes text and screen coordinates.
- Tap by accessible text and OCR-matched text (gesture dispatch); OCR text is matched to visible screen coordinates and the panel re-reads the screen after the action.
- Type only into the focused editable field. A separate pure Kotlin `PhoneActionSafety` policy blocks password/PIN/OTP/verification/card fields and blocks taps/typing when a detected financial/checkout screen appears sensitive. Consequential button labels are also blocked in multiple languages.
- Scroll up/down and global Back, Home, Recents, Notifications, Quick Settings, Android power dialog, and the system screenshot action.
- Fourteen screen tool routes are wired through `SystemTools` and the main `AiRouter` allowlist: accessible read, local visual OCR, accessible tap, OCR tap, type, up/down scroll, Back, Home, Notifications, Recents, Quick Settings, power dialog, and screenshot.
- The deterministic `PhoneTaskRouter` is connected at the beginning of `CommandProcessor.isle`, before learned/fuzzy matching or AI fallback. It handles explicit phone-screen commands locally; ordinary conversation falls through to EDA's existing path. Turkish commands and common Azerbaijani Latin alternatives are recognized for screen reading, scroll, notifications, home, recents, back, quick settings, and quoted label tap.
- The main branch's 14 theme profiles, generated wallpaper pipeline, `ThemeActivity`, and theme regression tests are preserved; the integration branch is based on the current themed `main` rather than the older unthemed dashboard branch.
- A dedicated Jarvis task panel is in the main dashboard. A user can type or dictate a task, see its result, and choose to speak the reply. It uses the existing allowlisted `AiRouter` / `SystemTools` tool loop rather than shell execution or arbitrary Android intents.
- Jarvis uses an explicit local-only provider policy: it calls only Termux/Ollama `gemma2:2b` and does not fall back to Pollinations/Groq if local inference is unavailable. This is important because the agent may read private on-screen data.
- The agent is bounded to five tool actions. If a model asks for another tool after reaching the limit, EDA requests a final explanation instead of silently stripping the tool call into an empty answer.
- The panel includes an accessibility-status refresh action. Dashboard provider labels and online indicators were corrected to match the actual provider/status text.
- JVM tests cover consequential labels, financial/checkout context cues, and password/OTP/card-field cues.

## Security and user control

The accessibility service must be explicitly enabled by the phone owner. The agent is not allowed to run arbitrary shell commands, open arbitrary packages, bypass authentication, or complete sensitive final actions. Password entry is blocked. OCR labels are not a universal semantic understanding of every icon or control; the app deliberately refuses sensitive labels and leaves consequential final steps to the user.

## Still not certified / known limitations

1. **Build and device verification are separate.** A successful GitHub Actions APK build verifies compilation, tests, and packaging only. It does not verify accessibility permissions, Samsung One UI behavior, touch coordinates, or voice-driven operation on a Galaxy A71. The latest commit's Actions run must finish green before this branch is called build-verified.
2. **Secure screens can refuse screenshots.** Android reports a secure-window screenshot failure for protected content; banking/DRM apps may show no OCR result. The tool reports a failure instead of trying to bypass it.
3. **OCR is Latin-script only.** It should be suited to Turkish and Azerbaijani Latin-script UI labels, but visual OCR does not currently read Persian/Arabic script. Accessibility-provided text may still be readable when the app exposes it. Direct Azerbaijani voice aliases are best-effort and depend on what the installed speech recognizer transcribes.
4. **Not a full host-side Phone Harness clone.** EDA is operating locally through Android accessibility; it does not use host ADB, browser-host automation, arbitrary coordinates without a matched label, image-based icon classification, multi-finger gestures, or an unrestricted autonomous planner.
5. **Text-label safety filters are best-effort.** Third-party apps use inconsistent labels and some sensitive screens may not expose useful text. EDA deliberately blocks detected financial screens and password/OTP/verification fields, but this is not a formal security boundary. Sensitive transactions and messages must remain user-controlled.
6. **The app has not been physically tested as part of this code change.** Manual acceptance should cover enabling/disabling the service, direct voice routing without breaking ordinary chat, accessible text read, OCR read, OCR-label tap and re-read, typing into a normal field, refusal to type into a password field, scrolling, Back/Home/Recents/Notifications/Quick Settings, the power and screenshot system actions, secure screens, UI responsiveness, the 14 theme picker, and existing EDA wake/sleep/rest, deep chat, and SMS translation.

## Acceptance checklist

- [ ] Latest branch commit passes `testDebugUnitTest assembleDebug` in GitHub Actions.
- [ ] Install and launch on Samsung Galaxy A71 / Android 13.
- [ ] EDA screen service appears in Accessibility settings and connects only after the owner enables it.
- [ ] Text read, visual OCR read, accessibility tap, OCR tap, type, scroll, Back, Home, Recents, Notifications, Quick Settings, power dialog, and screenshot actions each return truthful results.
- [ ] OCR tap is followed by a fresh screen read, and no sensitive final action is performed.
- [ ] Password fields remain untouched.
- [ ] No screen text is sent to Pollinations/Groq; all `SCREEN_*` agent tools, including screenshot/system-navigation tools, remain restricted to local Ollama.
- [ ] The existing wake/sleep/rest logic, Turkish TTS, deep chat, and incoming SMS translation still behave as before.
- [ ] Test with secure/banking/DRM screens and confirm failure is graceful.
- [ ] Record runtime observations before calling the feature production-ready.

## Verdict

The current main-based integration keeps the themed EDA dashboard and adds the deterministic voice route plus 14 allowlisted screen tools for the bounded Jarvis path. Screen-reading and screen-control tasks are pinned to local Ollama/Gemma 2:2B, without a cloud fallback. This is materially more useful than a manual-only panel, but it is **not a full host-side Phone Harness clone and is not certified as a fully autonomous phone operator**. CI verifies source/build/tests; physical Samsung Galaxy A71 acceptance is still required before declaring the feature fully verified.
