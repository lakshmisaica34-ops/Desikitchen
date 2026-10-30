# DesiKitchen avatar — Grok-style procedural rig

Pure flat vector. No video model, no fabric puff, no soft blur. Every frame is a
generated SVG path rasterised crisply (resvg) or drawn live in the browser.

## Files
| File | What |
|---|---|
| `desikitchen-avatar.svg` | Clean trace of `desikitchen-mark-final.png`. Layers: `#body` › `#hat` (one filled path, house roof + chimney is a real cut-out, no mask), `#groove-l` / `#groove-r` (pleat strokes), `#eye-l` / `#eye-r` (window eyes, separate paths). IoU against the locked PNG: **0.976** (the gap is anti-aliasing plus the PNG's slightly lumpy AI edges). |
| `desikitchen-avatar-core.js` | The rig: `hatPath()` rebuilds the hat from 3 circles + band + roof/chimney params every frame; `eyePath()` makes the arched window and can blink, squint, or curve into a `^` eye; `Rig` holds the springs and state machine. Runs in the browser and in Node. |
| `desikitchen-avatar.html` | Playground. Opens straight from disk. |
| `desikitchen-avatar-{idle,wink,thinking}.gif / .webm / .mp4` | Seamless loops, 512 px. GIF is 25 fps with a 32-colour undithered palette (sharp edges); WebM/MP4 are 50 fps. Idle 6.4 s, wink 3.2 s, thinking 9.6 s. |
| `desikitchen-mark-vector.png` | 1024×1024 still of the vector mark at rest, on black. For adaptive icon work later. |
| `tools/` | Export scripts (`export.js` for clips, `static.js` for the SVG/PNG). Run them from `/workspace/dk-anim`, which has `@resvg/resvg-js` installed. |

## How it matches Grok
- **Same build method.** Grok draws its body as a procedural filled path and puts two independent white eyes on top, all driven by code. This rig does the same: the hat silhouette is regenerated every frame from parameters, and the eyes are their own paths.
- **Eyes are white shapes on black and animate on their own.** The house windows are the eyes. Each has its own lid opening, smile bend, and position.
- **Blink takes 0.26 s.** It closes fast with an ease-in (90 ms), holds for 30 ms, then opens with an ease-out (140 ms). About 1 in 5 blinks is a double. Blinks come every 2.4–5.6 s at random.
- **Gaze drift.** The eyes jump between small targets every 1.1–3.3 s, with an underdamped spring (k=120, c=16) that slightly overshoots and settles. They smear a little along fast horizontal moves, and the far eye narrows slightly to fake depth.
- **The body breathes.** The three puffs swell out of phase (3.2 s cycle, about ±1.6 px), which is the hat's version of Grok's slow blob morph.
- **States use spring/ease-out moves.**
  - *idle:* breathing, gaze drift, blinks.
  - *wink:* the right window snaps into a `^` happy-closed eye and the left eye squints. The body tilts -5° on an underdamped spring and pops with a squash/stretch and a small hop. It returns to idle after 0.9 s.
  - *thinking:* eyes look up-right and narrow, windows tilt 8°, and the gaze flicks between two spots on the "ceiling". The puffs ripple left→centre→right like thought bubbles (1.2 s cycle), with a slight body sway.
  - *happy* (extra): both eyes go `^` and the body does two squash-and-stretch hops.
- Optional **Grok 26° eye tilt** toggle in the playground, so you can see the Grok eye angle on the windows.

## What can't match (and why)
- **No blob morph table.** Grok's body is one smooth closed curve that morphs between shapes with matching point counts. Our body is a chef hat with a sharp-cornered house cut out of it. Morphing the outline freely would warp the roof, chimney, and straight band, and the brand reading (hat + house) depends on those staying geometric. So the "morph" here is limited to puff radii and positions, squash/stretch, and tilt. The house cut-out always stays rigid.
- **The eyes aren't on the body.** Grok's eyes sit inside the blob. Ours are windows in the black negative space under the hat. They move with the body transform, but they can't drift across the white body the way Grok's eyes cross the blob.
- **Eyes aren't tilted capsules by default.** Locked-mark fidelity wins: they stay upright arched windows. The 26° tilt is opt-in (playground toggle, `grokTilt` option).
- **Eye shape.** An arched window collapses into a flat bar (or a `^` for wink/happy) instead of a symmetric capsule shrink. The lid drops toward the sill, which reads like a closing eyelid on this shape.
- **Gaze range is small** (about ±13 px x, ±14 px y on a 600 px canvas). Any more and the windows run into the roof legs or chimney and stop reading as a house.
- **Sharp roof corners make the silhouette stiffer** than Grok's always-round outline. That's deliberate: it's brand geometry.

## Notes for the adaptive icon later
- The SVG is one filled path + 2 strokes + 2 filled paths, with no masks or filters, so it converts cleanly to an Android VectorDrawable (strokes are allowed there).
- Mark bbox is x 512–936, y 298–731 in source pixel space. The `viewBox` 600×600 gives about 14 % padding. For an adaptive foreground (66 % safe zone), scale it down to about 0.8×.
