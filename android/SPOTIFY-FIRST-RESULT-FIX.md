# Spotify: selection blocked by an active editor (code-only fix)

The user screenshot reports a timeout before result #1 is clicked. The current
result selector called spotifyOwnsInputMethod() and returned WAIT whenever a
Spotify text input session existed. Android InputMethod.getCurrentInputStarted()
reports an editor session, not whether its keyboard is shown. Search may leave
that session alive after IME dismissal; all semantic/coordinate selection paths
were then unreachable until the command timeout. This is a confirmed code defect,
not proof of the physical device's exact state (no device dump was supplied).

The selector now checks interactive TYPE_INPUT_METHOD windows. After submission,
a visible keyboard is dismissed once, only with Spotify foreground confirmed;
unknown window state waits without tapping. Selection is retried every two seconds.
A hidden keyboard permits selection even when the editor stays active. Window and
foreground checks run again before gestures. A disappearing root no longer causes
a null dereference in the coordinate fallback. Existing title/row selection and
playback checks are retained. No arbitrary extra screen coordinates were added.

Tests: SpotifyTrackSearchRulesTest covers hidden, shown, unknown IME windows,
return after dismissal, different foreground app, and refusal to send Back twice.
CI must compile Java and run contracts only; no APK/version/release changes.

Physical QA (pending): search for «мама», «Обійми», «Пачка сигарет», «Yesterday»;
repeat with keyboard hidden and visible; check first row selected once, then
«Інша пісня»; switch apps during wait and verify no unintended Back/tap. Check a
hardware keyboard, unavailable Accessibility windows and delayed Spotify results.
No device playback success is claimed.

Android references:
https://developer.android.com/reference/android/accessibilityservice/InputMethod#getCurrentInputStarted()
https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#getWindows()
