# EDA V5 — Phone Harness-Inspired Screen Control Audit

Date: 2026-10-24  
Branch: `eda-fix-long-prompt-crash`  
Scope: the Android app under `android-app/`. This is an implementation audit, not a claim of physical-device certification.

## Implemented in the main EDA Android module

- A single registered Android `AccessibilityService` in `com.deniz.eda.phone.PhoneControlService`. Duplicate service implementations and duplicate manifest declarations were removed.
- Accessibility-tree text reading from the active window.
- Android 11+ screenshot capture through `AccessibilityService.takeScreenshot`, enabled in the service metadata.
- Bundled ML Kit Latin text recognition for local/offline OCR; the result includes text and screen coordinates.
- Tap by accessible text and OCR-matched text (gesture dispatch), with checks against visible labels commonly associated with sending, deleting, buying, paying, confirming, or transferring money.
- Type only into the currently focused editable node; refuse password fields. Typing does not click Send/Submit/Confirm.
- Scroll up/down and global Back, Home, and Notifications actions.
- Eight screen-action tool routes wired through `SystemTools` and the main `AiRouter` allowlist, plus the OCR read and visual-tap routes.
- The agent prompt instructs a read → explicit user-requested action → read-back pattern. The existing bounded tool loop limits an interaction sequence rather than allowing an unbounded action loop.
- Screen-control tools are restricted to the local Ollama provider to avoid sending visible screen content such as private notifications to cloud providers.
- The main dashboard's Phone Harness panel now offers accessibility text read and visual OCR read/tap. OCR work is sent to a background dispatcher so it does not intentionally block Compose's UI thread.

## Security and user control

The accessibility service must be explicitly enabled by the phone owner. The agent is not allowed to run arbitrary shell commands, open arbitrary packages, bypass authentication, or complete sensitive final actions. Password entry is blocked. OCR labels are not a universal semantic understanding of every icon or control; the app deliberately refuses sensitive labels and leaves consequential final steps to the user.

## Still not certified / known limitations

1. **Build and device verification are separate.** A successful GitHub Actions APK build verifies packaging/compilation only. It does not verify accessibility permissions, Samsung One UI behavior, touch coordinates, or voice-driven operation on the user's Galaxy A71. The latest commit's Actions run must finish green before this branch is called build-verified.
2. **Secure screens can refuse screenshots.** Android reports a secure-window screenshot failure for protected content; banking/DRM apps may show no OCR result. The tool reports a failure instead of trying to bypass it.
3. **OCR is Latin-script only.** It should be suited to Turkish and Azerbaijani Latin-script UI labels, but visual OCR does not currently read Persian/Arabic script. Accessibility-provided text may still be readable when the app exposes it.
4. **Not a full host-side Phone Harness clone.** EDA is operating locally through Android accessibility; it does not use host ADB, browser-host automation, arbitrary coordinates without a matched label, image-based icon classification, multi-finger gestures, or an unrestricted autonomous planner.
5. **Text-label safety filters are best-effort.** Different apps and languages can label consequential controls in unexpected ways. Sensitive transactions and messages must remain user-controlled; never rely on label filtering as the sole safety boundary.
6. **The app has not been physically tested as part of this code change.** Manual acceptance should cover enabling/disabling the service, accessible text read, OCR read, OCR-label tap and re-read, typing into a normal field, refusal to type into a password field, scrolling, Back/Home/Notifications, secure screens, UI responsiveness, and the existing EDA wake/sleep and SMS behavior.

## Acceptance checklist

- [ ] Latest branch commit passes the Android debug build in GitHub Actions.
- [ ] Install and launch on Samsung Galaxy A71 / Android 13.
- [ ] EDA screen service appears in Accessibility settings and connects only after the owner enables it.
- [ ] Text read, visual OCR read, accessibility tap, OCR tap, type, scroll, Back, Home, and Notifications each return truthful results.
- [ ] OCR tap is followed by a fresh screen read, and no sensitive final action is performed.
- [ ] Password fields remain untouched.
- [ ] No screen text is sent to Pollinations/Groq; screen tools work only via local Ollama.
- [ ] The existing wake/sleep/rest logic, Turkish TTS, deep chat, and incoming SMS translation still behave as before.
- [ ] Test with secure/banking/DRM screens and confirm failure is graceful.
- [ ] Record runtime observations before calling the feature production-ready.

## Verdict

The screen-control code paths are now integrated into the main Android app: accessibility text, local visual OCR, guarded text taps, and bounded agent tool routing. This is broader than the earlier manual-only control panel, but it is **not yet certified as a complete autonomous phone operator**. CI and physical-device acceptance are required before calling the feature fully verified.
