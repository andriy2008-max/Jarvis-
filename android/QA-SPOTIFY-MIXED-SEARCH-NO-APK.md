# Spotify: mixed song and playlist results — Pixel QA (unbuilt source)
Source changes only; do not label these as tested on the real Pixel or create an APK without a new explicit user command.

## Test mixed query
1. Open Spotify on Pixel. Say «Увімкни пісню романтична».
2. Spotify Search displays «Плейлісти», «Пісні» and a mixed list. Confirm the first visible result «Сексуальный плейлист» is a *playlist*, not a track.
3. The assistant must tap that first result by its *visible row title*, not the + icon, filters, bottom player or old Y=510. It must then open the exact playlist detail and press its real Play only after confirming the page.
4. Verify playback state; a completed accessibility gesture is not a guarantee that audio is playing.
5. Say «Не та пісня». If still on that playlist detail, press Android Back exactly once, wait for the original search results and select the *second* result «Романтичні пісні», then its Play.
6. Say «Інша пісня» again: return from the second playlist and select the third search result. Repeat for a fourth result; a real track should be tapped once and verified against the mini-player title.
7. If Spotify removes the previous search on Back, restore the stored query and select the same ordinal; do not reset to item 1.
8. Repeat with an exact track-title query; it must continue to play a song, not mistake the top search chips for a track.

## Diagnostics if tapping fails
- Check that Android Accessibility > «Дімон — керування екраном» is enabled and the Pixel is unlocked.
- ADB (Android SDK Platform Tools): while Spotify's result page is open, run `adb shell uiautomator dump /sdcard/spotify-ui.xml` and `adb pull /sdcard/spotify-ui.xml`. Compare exposed text, contentDescription and bounds for the first result's title and «Плейліст»/«Пісня» subtitle.
- On devices where Spotify's Compose UI omits its semantic metadata, report the missing node instead of pretending to have identified an actual playlist.
- Observe logs tagged `JARVIS_SPOTIFY` and `JARVIS_SCREEN` for dispatch/overlay failures; do not upload secrets or private screen content.

## Exit criteria
- A single row tap per selected result; never press Play twice after Pause is visible.
- Search ordinal advances by exactly one on each accepted «Не та пісня».
- No unwanted Back when already on the search page.
- All prior Spotify, Bolt, JARVIS Finance and Watch7 features must remain unchanged.
