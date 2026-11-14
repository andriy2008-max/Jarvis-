# Watch7 / Pixel reliability fixes — 2026-11-14

Changes after ac766a510051e6209ad0163ff690582c752c9513:

- Watch replies honor voiceEnabled in manual Phone mode. Recognition is retired before TTS; stale callbacks cannot restart capture. Continuous listening resumes through the existing loop after speech ends.
- Successful screen, Spotify, enter/exit and cancellation replies can be spoken, as well as errors.
- Phone-owned voice macros run through both Wear dispatch routes. Results carry actual completion status; duplicate recognition gets an explicit response. Remote macros stop after 120 seconds, before the 150-second watch timeout. MacroDroid broadcast delivery is not reported as completed execution.
- Spotify cancellation uses a new correlated request/reply. Sending a message alone is not success.
- Screen-command timeout is 150 seconds, exceeding six 18-second sequence steps. Heartbeat/cancellation remain bounded to 12 seconds.
- Launching an app waits up to 10 seconds for the target foreground package. This requires the user's Accessibility permission. A locked/noninteractive phone returns an explicit unlock message; no unlock is attempted. Screen workflows stop if the phone locks.
- AI connection check performs an authenticated model request with empty history; it neither clears nor contaminates the conversation. This small request may use API credits.
- Unknown control verbs are not silently treated as conversational AI questions.
- Financial save confirmation is conservative: closing the form is NOT evidence of a saved ledger row. The result says verification is required and instructs the user not to repeat the command before checking history. No second save is attempted.

## Verification

Run `.github/workflows/voice-tests.yml`: Java contracts, mocked AI worker tests, Python contracts and Android Java compilation. Protocol regressions cover the six-step timing budget, remote macro deadline and cancellation timeout.

Compilation and contract tests do not prove real Wear Data Layer, ASR, TTS, locked-phone behavior, or third-party API authorization. Physical Watch7/Pixel tests remain necessary. No new APK is built by these checks; stable signing/package/version are unchanged.

## Remaining device-dependent limits

Money Manager has no implemented ledger readback API. Actual record existence and uniqueness require checking history manually. Generic UI heuristics must not be presented as proof of saving. Real-time API tests need the settings already stored on the user's Pixel. Android background restrictions are reported rather than bypassed.
