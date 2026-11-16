# Bolt preparation exception fix

Based on release/jarvis-0.1.33-finance. Report: app opens and stops at TAP_RUSHAYMO_PREP (UnsupportedOperationException).

The calibrated gesture path called Service.getDisplay() before the null fallback. Android explicitly throws UnsupportedOperationException for a non-display-associated service context. Resolve DEFAULT_DISPLAY through DisplayManager instead, with a guarded WindowManager fallback. All eight equivalent lookups in ScreenControlAccessibilityService use the helper; if neither service supplies a display, existing callers return WAIT/BLOCKED rather than tapping guessed geometry.

Existing user-calibrated coordinates, 4-second startup grace, foreground checks, overlay hiding, focused-editor checks and no automatic ride booking remain unchanged. This fixes the identified exception; a changed Bolt layout or non-matching coordinates can still require calibration.

Validation: helper compiled against Android 15 API; regression tests cover available primary, null primary, UnsupportedOperationException, both providers failing, and neither returning a display. Existing Bolt label/geometry tests and taxi command tests also run. No physical Pixel/Bolt session tested; no APK built.

Device acceptance after packaging:
- Run “таксі Борислав дім” with phone unlocked and Bolt signed in.
- Confirm opening, the first tap after four seconds, then destination tap.
- Confirm destination alias resolves to the intended address and Enter submits search once.
- Switch away from Bolt during preparation: no tap into another app.
- Check that no ride is confirmed or paid automatically.

Alternatives if Bolt changes layout: labelled screen-control steps where labels are exposed; recalibrated coordinates for the current device; a user-enabled root gesture backend with explicit foreground and editor guards. These are fallback approaches, not claimed device-tested integrations.
