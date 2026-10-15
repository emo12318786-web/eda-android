# EDA V5 — Feature and Readiness Audit

Branch: `eda-v5-redesign`  
Purpose: document what has been inspected and what still requires verification. Source presence is not proof of runtime correctness.

## Current readiness

| Area | Evidence found | Agent tool access | Device tested in this audit |
|---|---|---|---|
| Main dashboard | Compose dashboard in `ui/dashboard/DashboardActivity.kt`; shows battery, internet, memory, reminders, provider status, logs | Not itself an Agent tool | No |
| Start / sleep / stop | Dashboard sends service actions; existing `MainActivity.kt` also controls the foreground service | Actions are direct UI/service actions, not general Agent tools | No |
| AI providers | Dashboard can select Groq, Pollinations, Ollama; view model checks their status | Read/check tools exist; provider selection is UI-driven | No |
| Memory / diary / reminders / learning | Local stores and counters are referenced in source; SystemTools exposes read tools | Read-only retrieval tools in current allowlist | No |
| Voice and wake word | Main activity requests microphone permission; service owns background behavior | No proof that all voice actions are Agent tools | No |
| Security / car mode | Settings flags exist; dashboard currently explains these as voice-command features rather than exposing full controls | Not verified as Agent tools | No |
| Settings | Existing SettingsActivity retains user name and provider keys; more settings live elsewhere in project | Not verified as Agent tools | No |
| Natural language | SystemTools prompt has example phrases and tool tags; AiRouter uses a bounded tool loop | Only the currently allowlisted tools are callable | No |
| Logo and copy | Dashboard branding and Turkish labels refined in this branch | Not applicable | Visual/device review still needed |

## Important implementation caveats

1. The current Agent is not a universal autonomous phone operator. The inspected `AiRouter.kt` tool allowlist is mostly read-only: connectivity/provider checks, battery, mode, system summary, memory/diary/reminders/learned items, time and date.
2. `SystemTools.kt` has helpers for data retrieval. A helper existing in source does not mean the Agent can call it; the router must explicitly parse and allow the tool.
3. Dashboard actions and voice-command actions are separate paths. Do not advertise an Agent action as complete unless its route is connected and verified.
4. The redesigned dashboard refreshes its data while it remains in composition. This is a UI refresh, not a guarantee that every background service or provider is healthy.
5. Provider status strings are best-effort network checks; they do not prove that a complete user prompt can be answered successfully.
6. No physical-device test is claimed here. Microphone permissions, wake/sleep power use, Android background restrictions, TTS/STT language availability, and actions on the user's Samsung device must still be tested.
7. The build workflow must pass for the latest commit after these changes. Even a green build verifies compilation/package creation, not real-device behavior.

## Required acceptance checklist

- [ ] Latest branch commit builds successfully in CI.
- [ ] Install and launch on Samsung Galaxy A71 / Android 13.
- [ ] Confirm logo and layout at actual device font/display scaling.
- [ ] Verify start, sleep, stop, and actual status after each action.
- [ ] Verify microphone permission denial and re-grant paths.
- [ ] Test wake phrase and Turkish STT/TTS with the language packages available on device.
- [ ] Check battery use during active listening and sleep over a measured interval.
- [ ] Test each original command category: flashlight, time/date, prices, location, reminders, memory, diary, learning, car/security, SMS, and settings.
- [ ] Confirm every Agent tool is allowlisted, parsed, executed and truthfully reported; require confirmation for consequential actions.
- [ ] Verify original settings and control panel remain reachable from the app.
- [ ] Review and merge PR only after these checks and owner approval.

## Current conclusion

This branch contains a dashboard redesign and copy/logo refinements. It is **not yet certified as a complete, fully Agent-enabled, device-tested EDA V5 release**. Keep the redesign in its pull request until the acceptance checklist is completed.
