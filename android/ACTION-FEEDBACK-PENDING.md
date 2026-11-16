# Pending changes after JARVIS 0.1.33

Base: `fix/bolt-0133` (includes the Finance release and service display fix).
No version increment, APK assembly, signing or release is part of this change.

## Behavior

- Phone, floating overlay and watch-triggered commands show a replaceable, non-interactive bottom status. The accessibility overlay is used when available; otherwise Android Toast is the fallback.
- Missing controls are checked again after 4 seconds and then every 2 seconds. Spotify and individual UI/sequence waits continue until the control appears, cancellation, screen lock or loss of control permission. Accepted text entry and search submission are not repeated.
- Spotify playlist and rap commands always press My Library, even if selected, and wait for the library page. Playlist lookup happens there. Rap opens Liked Songs and selects Rap. Playback is requested by clicking a song row, not green Play.
- Song search presses the bottom Search tab once. If entering from another tab and the placeholder is still absent 2 seconds later, it presses Search once more. It then clicks the visible search field, waits for its editor, enters the query, submits Search/Enter, and selects the first identified song row.
- “не та пісня”, “інша”, “інша пісня” repeat the last song query and choose an unselected song result. Context is shared across phone/watch/overlay controllers in the same phone process, and is cleared by a new song or collection request. If the process restarted, the user is asked for a song name.
- Missing Bolt controls are read repeatedly instead of tapping fixed coordinates on a loading screen. The existing display-resolution fix is retained. The final ride confirmation is never pressed.
- Cancellation: “скасуй Spotify”, “скасуй таксі”, “скасуй послідовність”, or “скасуй виконання” for a pending individual screen action. New individual screen actions retire the previous pending action.

## Verification

- Java command/policy regression suite, including late button/input arrival, 4s/2s retry timing, cancellation and exactly-once successful steps.
- `python tools/tests/spotify_flow_test.py`: runs the actual Spotify controller against deterministic Android/UI fakes. Checks selected/unselected Search, late placeholder, first/next/third results, library gating, rap filter, cancellation and control-lease cleanup.
- Android compilation only in CI; this does not package an APK.

## Pixel / Watch7 acceptance checklist (requires a later requested build)

- [ ] Every command shows its current step and final result at the bottom of Pixel, including commands spoken on Watch7 and muted replies.
- [ ] On slow loading, missing button waits continue; cancel stops all later taps.
- [ ] From Spotify Home, search opens the intended placeholder. From Search, no redundant second tab press.
- [ ] A song command clicks the first song result, excluding artist/album/playlist cards and the mini-player.
- [ ] “не та пісня” selects the second result; “інша” selects the third. Repeat with command entry alternating between phone and watch.
- [ ] Rap selects a song after applying the Rap filter, even if the existing player was already playing something else.
- [ ] A playlist command returns to My Library even from an already-selected library subpage, finds the named playlist, and clicks a song.
- [ ] Bolt opens rides, waits for destination, enters the address and submits search; cancellation also works from Watch7.

Live Spotify/Bolt node structure and audible playback cannot be verified by JVM fakes or compilation. Success text reports an accepted song-row click, not unverified playback. An inaccessible/unidentified row stays waiting rather than using an arbitrary coordinate.
