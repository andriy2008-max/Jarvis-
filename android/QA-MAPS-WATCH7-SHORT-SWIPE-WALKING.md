# Watch7 — Google Maps walking-route selection (source only)

**Command:** «Знайди дорогу до [адреса]».

## Expected order

- [ ] Google Maps launches on **Galaxy Watch7**, not Pixel.
- [ ] Jarvis opens search, types the destination, submits and selects its matching result.
- [ ] It waits for the travel-mode cards (no gesture while Maps is loading, while a keyboard is open, or on Maps home).
- [ ] When the route modes are visible, it performs exactly **one gentle UP swipe** (from 73% to 56% of the watch height) to reveal the walking row.
- [ ] After the swipe, the screen is inspected again. The walking card is the **middle row between transit and bicycle** in the user's screenshot, with **4 год 36 хв · 21 км**.
- [ ] It taps the walking row using a visible walking label/icon or its distance label. If those are unavailable, it uses the final visible duration card or the safe Watch7-relative middle-row point **only after the route screen was confirmed**.
- [ ] It does **not** tap the bottom bicycle card, which would have been the previous 84%-height coordinate.
- [ ] It does not perform a second swipe if the first completed, and doesn't repeat the walking tap after a completed gesture.
- [ ] If the route cards cannot be recognized in time, it attempts Google's documented walking navigation intent `google.navigation:q=...&mode=w` without claiming that a tap succeeded.
- [ ] «Де я зараз» and Spotify/Finance routes remain unchanged.

## Regression

- [ ] Check with slow cellular data: wait for route cards before swipe.
- [ ] Check with an active input method: do not swipe or tap into the keyboard.
- [ ] Check route list with two durations (transit, walking) and bicycle with no duration.
- [ ] Check route list with three durations, where walking is the last *duration*, not necessarily the last visible card.
- [ ] The Jarvis interface stays available after the navigation action.

**Note:** Automated unit tests and Gradle compilation cannot verify that Google Maps on an actual Watch7 registers the physical tap. Do not publish an APK without a user instruction.
