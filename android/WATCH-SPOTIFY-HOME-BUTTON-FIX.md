# Watch Spotify player navigation — code only

User identifies the lower-centre list/queue icon as the home/player entry screen,
including when no song is loaded. Previous detection accepted only selected Home
or home-section labels; this player layout triggered up to three recovery swipes.

Added lower-centre list/queue/browse Accessibility label or view-ID recognition,
independent of track title, artwork and Play/Pause. Search editors remain excluded.
Home is rechecked immediately before a scheduled right swipe. Recognized home
skips that gesture. Navigation reveal now drags DOWN from 12% to 90% of watch
height at the horizontal centre over 700 ms; library downward scrolling uses the
same long gesture. Phone Spotify is unchanged by this patch.

Rules tests cover player with no track, localized labels, view-ID-only icons,
wrong position, unrelated/unknown icon, search editor, downward distance/duration.
Physical Watch7 confirmation remains pending. The photo cannot reveal Android
contentDescription/viewId: if Spotify exposes an entirely unlabelled icon, an
Accessibility hierarchy is needed to add a reliable selector. No image-recognition
capability is claimed, no guesses based on song text or arbitrary taps added.

Manual QA: open Spotify directly on the photographed player with/without a song;
issue a music command; expect zero right swipes and one long downward reveal.
Repeat from another page; once the player appears, no further right swipes.
Check Search and playlist navigation, then return to Dimon for a second command.
No APK, version increment, signing or release is requested by this change.
