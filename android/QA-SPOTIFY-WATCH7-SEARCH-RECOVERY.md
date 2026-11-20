# JARVIS Watch7 Spotify — recovery + mixed result Play + follow-up (source QA)

Source-only patch. Do not build or publish APK without a new explicit user instruction.

## Navigation for "Увімкни пісню <назва>" on Watch7
- [ ] Open Spotify on the watch (not Pixel); confirm Spotify owns the foreground before touch.
- [ ] **Initial action: one LONG UP swipe** to reveal Search. No preliminary right swipes from the Home screen.
- [ ] Search icon exists after UP: tap it, open editable field, enter requested title and submit it. **No extra recovery swipes.**
- [ ] Search missing after UP: swipe RIGHT once and DOWN once; check Search icon again.
- [ ] Search still missing: swipe RIGHT twice, then UP once; check Search icon a third time.
- [ ] If Search is still missing, stop and show diagnostic error, rather than looping or tapping blindly.
- [ ] If IME is open or Spotify is no longer foreground, suppress recovery swipes.

## Mixed Search results
- [ ] Select FIRST visible result (song OR playlist), ignoring filter chips, home icons and mini-player.
- [ ] If result is a song, tap its real title only once, then return to foreground Jarvis microphone; user says «Не та пісня».
- [ ] If result is a playlist, wait for its own detail page, press Play, verify Pause, swipe RIGHT to leave the playlist, and bring Jarvis to foreground.
- [ ] After returning to Jarvis, mic starts only while MainActivity is foreground, once the previous command is finished.
- [ ] «Не та пісня» / «Інша пісня» selects NEXT search result on this watch. Preserve original query and ordinal; no Pixel request, no accidental return to result 1.
- [ ] If next result is also a playlist, press its Play and swipe RIGHT again. If it is a song, do not press a playlist Play.
- [ ] If selected result is beyond the currently visible results, do not guess or tap a hidden item; request a new screen/UI diagnostic.

## Regression
- [ ] «Увімкни плейлист <назва>» still finds named playlist in My Library, not in generic Search.
- [ ] Music continues playing when Jarvis comes foreground.
- [ ] Watch Jarvis remains alive for a second and third command; microphone does not start while Spotify is foreground.
- [ ] Pixel Spotify, Bolt, Maps walking route, and JARVIS Finance are not altered.
- [ ] On real Watch7, test on Home, resumed playlist page, and a slow-loading Spotify window.

## Diagnostics
Speak «Діагностика Spotify» on Watch7 to get last action stage. For deeper logs, use Android Platform Tools / ADB paired with Wear OS to view `DIMON_WATCH_SPOTIFY` and `DIMON_WATCH_CONTROL` logs. Do not share authentication data.

Automatic Java/Android tests verify contracts and compilation only, not physical Spotify UI success.
