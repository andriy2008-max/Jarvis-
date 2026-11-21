# Дімон 0.1.45 — Spotify first-result repair (SOURCE ONLY; not an APK)

## What the user's log actually says
- COMMAND_TYPE 1 denotes Spotify.
- COMMAND_BEGIN / COMMAND_END 269 ms apart is the MainActivity command dispatcher finishing; before this fix it did not wait for SpotifyPlaybackController, which runs asynchronously.
- PAUSE/STOP after launching Spotify is expected for MainActivity and does NOT establish an Android process crash.
- No Spotify stage was exposed in the previous journal, so the exact failed tap cannot be reconstructed solely from that report.

## First-result regression: mixed search
- [ ] Say «Увімкни пісню романтична» on Pixel and confirm the Search results render.
- [ ] The first visible result is «Сексуальный плейлист» (playlist). Tap THIS first row rather than a later exact-title track «Романтичная» (Syava).
- [ ] If Spotify publishes `Title • Плейліст` as one accessibility node, extract both title and category and tap its bounds.
- [ ] If Spotify hides the type metadata, choose the first visible CONTENT title in top-to-bottom order, excluding search chips and mini-player.
- [ ] If a row opens a playlist despite unknown metadata, detect its confirmed full playlist-detail page and press the real large Play on that page. Verify Pause.
- [ ] If a row is an actual track, verify the new mini-player title; do not press playlist Play.
- [ ] Say «Не та пісня» and verify the next mixed search result opens; another attempt advances again.
- [ ] If a title and metadata are entirely unavailable, fallback may use a confirmed portrait/Spotify/search-result coordinate only, never tap another app or keyboard.

## Journal after patch
- [ ] SPOTIFY_STAGE uses enum-order numeric IDs: WAIT_APP=1, OPEN_SEARCH_TAB=8, FOCUS_SEARCH=9, TYPE_SEARCH=11, SUBMIT_SEARCH=12, COORD_SELECT_FIRST=13, VERIFY_TRACK_PLAY=14, WAIT_MIXED_PLAYLIST=16, PLAY_MIXED_PLAYLIST=17, VERIFY_MIXED_PLAYLIST=18.
- [ ] LOCAL_DONE or LOCAL_ERROR shows the asynchronous Spotify operation finished.
- [ ] COMMAND_END must not appear while SpotifyPlaybackController is still running. This is telemetry; it does not prove sound playback.
- [ ] Do not post raw Spotify screen dumps containing private text or account details.

## Other modules unchanged
- [ ] Library-based «Увімкни плейлист …» still works.
- [ ] Spotify on Watch7, Google Maps walking, Bolt, and JARVIS Finance still work as before.

## Important
Automated Java tests and Gradle compilation verify source contracts only. This is NOT a confirmation of playback on the user's Pixel. No new APK is authorized by this debugging request.
