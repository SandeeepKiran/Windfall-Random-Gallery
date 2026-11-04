# Windfall Gallery — brag plan

## Answers
- **What is it:** an Android gallery that deals your own photos and videos at random instead of newest-first.
- **Who for / what it does:** people with big camera rolls; it brings back the photos they forgot about.
- **What sets it apart:** every swipe deals a new random page; hearted photos come back more often; a six-video wall; no internet permission at all.
- **Best claim:** "it cannot go online, and your photos never leave your phone."
- **Visual hook:** the name. A windfall is something that falls to you by luck, so photo tiles fall into frame like leaves.
- **Real UI shown:** the store screenshots (made on the emulator with generated demo media): Gallery grid, Favourites grid, Multi-Video wall. Grid tiles are cropped from the real Gallery screenshot and re-dealt.
- **Tone:** `default`, leaning calm-polished (a personal, privacy-first app; no hype).
- **Share caption:** see `share-copy.txt`.

## Angle
Forgotten photos literally fall back into view. Then the app explains itself in its own store copy.

## Visual identity
Store-listing look: teal-to-navy gradient (#0f766e → #134e4a → #0c1626), Segoe UI (same as the store images), sand accent #E8C877 (the app's shuffle button), heart #F38282 (`FavouriteHeart`).

## Storyboard (1920×1080, 30 fps, 23.5 s)
| # | Time | Scene | Text on screen | Sound |
|---|---|---|---|---|
| 1 | 0.0–3.2 | Hook: 15 real tiles tumble down like leaves and settle in a loose pile | "Forgotten photos," / "falling back into view." | soft rising pad, airy whooshes |
| 2 | 3.2–7.0 | Reveal: pile sweeps into the phone; icon + title left, Gallery screen rises right | "Windfall Gallery" / "Your photos, **shuffled.**" | beat enters, chime on title |
| 3 | 7.0–11.0 | Highlight 1: finger swipes the grid twice; tiles re-deal in a staggered flip | "Every swipe deals a new random page." | soft card-flip ticks under the music |
| 4 | 11.0–14.2 | Highlight 2: double-tap ripple on a tile, heart pops, cut to Favourites tab | "Double-tap to heart." / "Hearted photos come back more often." | two soft taps, bell |
| 5 | 14.2–17.4 | Highlight 3: Multi-Video wall tiles slot in one by one, slow drift | "Up to **six videos** at once." | four plucks |
| 6 | 17.4–20.2 | Privacy: big statement | "No internet permission." / "Your photos never leave your phone." | music drops to pad |
| 7 | 20.2–23.5 | Outro: icon, name, badge, link | "Windfall Gallery" / "Free & open source · Android 11+" / "New in 1.5.0: tablets, Chromebooks, keyboard & mouse" / repo URL | resolve chord |

## Music
Generated in Python (numpy): D major, 100 BPM, soft pad + plucked arpeggio + light kick/shaker; effects tuned to D and mixed under the music.
