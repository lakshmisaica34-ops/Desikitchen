# Fix: "Quick Steps:" step headings (29 Oct 2026, ~10:15 IST, still 1.1.3, no APK built)

Reel #3 [DeH0TduS8lG](https://www.instagram.com/reel/DeH0TduS8lG/?srtk=amp0d3ZkZWVudmNr) (Homemade Apple Jam): the RAC parse bug found in the run below is now fixed.

| | Kotlin `CaptionRecipeParser` (ing / steps) | Harness `caption_parse` (ing / steps) | Winning stage | Result | Stage-1 s | Wall s | SC / OpenRouter | USD |
|---|---:|---:|---|---|---:|---:|---|---:|
| **Before** (`harness_run_113b.jsonl`) | 9 / 0 (4 method lines filed as ingredients) | 8 / 0 | 1 public HTML embed | PARTIAL | 0.32 | 0.3 | 0 / 0 | $0 |
| **After** (`harness_run_113c.jsonl`) | **5 / 4** | **5 / 4** | 1 public HTML embed | **FULL** | 0.39 | 0.4 | **0 / 0** | **$0** |

The app now gets 5 ingredients: Chopped Apples (Seb), Fresh Pomegranate (Anaar), Water, Lemon juice, Sugar. It also gets 4 steps: boil → strain and press → double-strain, add sugar and cook till glossy → jar and cool. The outro lines ("👉 Save this reel…", "💬 … comment karke bataiye") are not counted as steps.

What changed:
- `CaptionRecipeParser.kt`: `stepHeader` now accepts up to 2 qualifier words from a fixed list before the heading word: quick, easy, simple, full, basic, detailed, short, cooking, making, recipe, step-by-step, the, my, our. This covers "👩‍🍳 Quick Steps:", "Easy Method", "Simple Steps", "Full Recipe:", "Step-by-step method:" and "Detailed Instructions -".
  - Arbitrary words are not accepted, so a line like "Mushroom rice recipe:" is not treated as a heading.
  - "Full recipe below 👇" is still rejected by `looksLikeHeader`.
  - The ingredient header was not changed.
- `tools/scraper_harness.py`: `STEP_HEAD` uses the same qualifier list. The harness also stops counting at outro lines ("save this…", "follow…", "comment…"), the same way the Kotlin parser does. Before this, its approximation would have counted the "Save this reel" line as a 5th step.
- Tests in `InstagramImporterTest`:
  - `appleJamQuickStepsReel` uses the real embed HTML fixture `app/src/test/resources/scraper/ig_embed_tastiva_applejam.html.gz` and checks FULL, 5 ingredients, 4 steps, and no outro lines in the steps.
  - `qualifiedStepHeadings` covers 7 heading variants and a "Full recipe below 👇" teaser.
  - Full unit suite: 69 tests, 0 failures, 3 skipped (live checks).

---

# Scraper harness report: 1.1.3, Sai's 3 reels (29 Oct 2026, ~10:10 IST)

Run: `HARNESS_FRESH=1 HARNESS_JSONL=tools/harness_run_113b.jsonl python3 tools/scraper_harness.py <3 URLs>` (live; no APK built).
Raw rows: `tools/harness_run_113b.jsonl`. USD = OpenRouter `usage.cost` from the API. SC credits went from 847 to 845.
Stage 1 was also checked against the real Kotlin `CaptionRecipeParser` (temporary JUnit probe, deleted afterwards), because the harness's Python parser is only an approximation.

| # | Reel | Winning stage | Wall s | SC post / transcript | OpenRouter calls · models | OR USD | Title | Ing | Steps | Result | HTML caption had RAC? |
|---|---|---|---:|---|---|---:|---|---:|---:|---|---|
| 1 | [DeyXrWUFYAc](https://www.instagram.com/reel/DeyXrWUFYAc/) | 4 keyframes+audio (5 stills + 83 KB audio, 4.4 MB video) | 20.5 | 1 / **0** | 2 · mistral-nemo (2 grounded, needs ≥3 → continue), gemini-2.5-flash-lite | $0.000475 | Jonna Sangati | 4 | 6 | PARTIAL | **No.** Telugu caption with 3 numbered steps but no ingredient list (Kotlin: hasRecipe=false) |
| 2 | [DbpLisLTDWD](https://www.instagram.com/reel/DbpLisLTDWD/) | 4 keyframes+audio (11 stills + 214 KB audio, 15.1 MB video) | 17.1 | 1 / **0** | 2 · mistral-nemo (0 ingredients), gemini-2.5-flash-lite | $0.000920 | Garlic Chili Rice (vellipaya karam annam) | 6 | 8 | PARTIAL | **No.** One Telugu teaser line + hashtags |
| 3 | [DeH0TduS8lG](https://www.instagram.com/reel/DeH0TduS8lG/) | 1 public HTML embed caption | 0.3 | 0 / **0** | 0 | $0 | App: "Stop buying store-bought jam" (dish = Homemade Apple Jam) | App: 9 (5 real + 4 steps misfiled) | App: 0 | PARTIAL (should be FULL) | **Yes.** "📝 Ingredients:" has 5 items and "👩‍🍳 Quick Steps:" has 4 steps |
| | **Total** | | **37.9** | **2 / 0** | **4** | **$0.001395** | | | | | |

Per-stage seconds:
- #1: html 0.52, SC 5.74, nemo 9.79, download 1.38, ffmpeg 0.43, gemini 2.59.
- #2: html 0.76, SC 5.52, nemo 6.41, download 1.09, ffmpeg 0.36, gemini 2.97.

Temp media was deleted in both stage-4 runs, and the full video was never sent to OpenRouter.

Extracted (stage 4):
- #1 Jonna Sangati: jowar, water, salt, ajwain. Steps: soak overnight → grind → 1:2 water → salt + ajwain in boiling water → cook without lumps → serve.
  - Stage 3 had already grounded jowar + ajwain from the caption, but it needs ≥ 3 grounded ingredients to stop (water and salt were dropped).
- #2 Garlic Chili Rice: garlic cloves, red chili powder, salt, oil, cooked rice, coriander. Steps: 8 (pound ~20 garlic + chili + salt → hot oil, stove off → mix in rice → coriander).

### Finding: #3 RAC misparse (parser bug, not an AI problem)
The real `CaptionRecipeParser` output for #3 was hasRecipe=true, isComplete=false, 9 ingredients, 0 steps.
- **Cause:** `stepHeader` is anchored at `^(instructions?|method|steps|…)`, so "Quick Steps:" is not recognised. That line is skipped as a sub-heading, and the 4 method sentences stay in the ingredient section.
- **Fix:** allow a short qualifier before the step header and add #3's caption as a fixture test. **Done later the same morning; see the "Fix" section at the top of this report (now FULL, 5 / 4).**
- The harness's Python `caption_parse` reports 8 ingredients / 0 steps for the same reason. It is an approximation, and the Kotlin probe is the authoritative result.

### Does HTML RAC need AI to rewrite it for the app? No, by design (confirmed in code)
- `InstagramImporter.import`: stage 1 `fromCaption(...)` returns at the line `s1?.let { return win("1_public_html", …) }`, and stage 2 returns the same way.
- Every AI call comes after those returns:
  - `text.extract(... COMMENTS_SYSTEM)` (stage 3)
  - `stills.extract` (stage 4)
  - `video.extractRemoteOnly` (thin re-read)
- `CaptionRecipeParser` is rule-based. Its doc says "Everything returned is copied from the caption; nothing is guessed or generated".
- There is no "polish/rewrite" AI pass anywhere in `app/src/main`.
- Bad RAC formatting, as in #3, should be fixed in the parser. Adding AI there would cost money and risk hallucination on text that is already correct.

---

# Earlier run: 1.1.3 (29 Oct 2026, ~09:56 IST)

Run: `HARNESS_FRESH=1 HARNESS_JSONL=tools/harness_run_113.jsonl python3 tools/scraper_harness.py <Sai reel> <control>`
Raw rows: `tools/harness_run_113.jsonl`. A single fresh live run used real Scrape Creators and OpenRouter calls. Costs come from OpenRouter `usage.cost`, which the API returns in USD, so they are not estimates.

## Sai's reel: https://www.instagram.com/reel/Ddk4k1PoM-k/?xtok=MWtndzhiaWoxcWhzaQ==

| Stage | What ran | Seconds | Paid calls | USD | Result |
|---|---|---:|---|---:|---|
| 1 public HTML/embed | IG embed caption → rule parse | 0.35 | 0 | 0 | caption is chit-chat, 0 ingredients → continue |
| 2 Scrape Creators | `GET /v1/instagram/post` ×1 | 5.74 | **1 SC credit** (847 left) | n/a (credit) | video_url, has_audio=true, 14 top comments; caption re-parse 0 → continue |
| 3 cheap text on comments | `mistralai/mistral-nemo`, caption + 14 comments | 3.30 | 1 OpenRouter | **$0.00000888** (440 in / 32 out tok) | 0 ingredients (0 grounded) → continue |
| 4a download | CDN video → temp file (streamed) | 0.94 | 0 | 0 | 18.49 MB |
| 4b ffmpeg | `-skip_frame nokey`, scale 512, mono 16 kHz 32 kbps MP3 | 0.33 | 0 | 0 | 14 keyframes → 12 sent (451 KB) + 263 KB audio |
| 4c Gemini | `google/gemini-2.5-flash-lite`, 12 stills + audio, strict JSON | 2.35 | 1 OpenRouter | **$0.0010975** (5,475 in / 298 out tok) | **15 ingredients, 11 steps → WIN** |
| 4d full-video re-read | (only if < 4 ingredients) | n/a | 0 | 0 | not needed |
| **Total** | | **13.0 s wall** | **1 SC post + 0 SC transcript + 2 OpenRouter** | **$0.001106** | **PARTIAL, winner = stage 4 (keyframes + audio)** |

Temp media deleted: yes (`temp_deleted: true`). The full video was never sent to OpenRouter.

**Extracted**
- Title: *Cheesy Vegetable Bread Pockets*
- Ingredients (15): bread, capsicum, carrot, mayonnaise, pizza pasta sauce, oregano, chilli flakes, salt, cheese, butter, garlic powder, peri peri masala, coriander, maida (flour), water
- Steps (11):
  1. Chop capsicum and carrot finely.
  2. In a bowl, mix chopped vegetables with mayonnaise, pizza pasta sauce, oregano, chilli flakes, salt, and cheese.
  3. Mix well to form the stuffing.
  4. In another bowl, mix butter with garlic powder, peri peri masala, and coriander.
  5. Mix well to form garlic butter.
  6. Take bread slices and make a slurry of maida and water.
  7. Dip the bread slices in the slurry.
  8. Fill the bread slices with the prepared stuffing.
  9. Fold the bread slices and seal the edges.
  10. Spread the garlic butter on the bread pockets.
  11. Bake in an air fryer at 160 degrees Celsius for 10 minutes.
- No quantities: the reel doesn't say them. The prompt forbids guessing, so the result is PARTIAL with a "check quantities" warning.

## Control: https://www.instagram.com/reel/DF6dMW5vwgw/

| Stage | Seconds | Paid calls | Result |
|---|---:|---|---|
| 1 public embed caption | 0.27 | 0 | **FULL**: 8 ingredients, 4 steps |
| **Total** | **0.3** | **0 SC, 0 OpenRouter, $0** | winner = stage 1 |

## Models (and why)
- Stage 3: `mistralai/mistral-nemo` is the cheapest OpenRouter text model with JSON mode (~$0.02/M in, ~$0.04/M out; $0.0000089 here). Anti-hallucination: the system prompt says "only what is written". The grounding filter keeps an ingredient only if a content word of it appears in the caption or comment text. A stop requires ≥ 3 grounded ingredients.
- Stage 4: `google/gemini-2.5-flash-lite` (default). On the same reel `google/gemini-2.5-flash` cost $0.0039 (≈3.5× more) for 15 ingredients / 9 steps. Override with `OPENROUTER_STILLS_MODEL`.
- Thin-result re-read: `google/gemini-2.5-flash` with the remote video URL. It runs only when keyframes yield < 4 ingredients, and it is logged as `4d_full_video_reread`.

## Android (1.1.3) equivalence
- `StillsRecipeExtractor` streams the video to a cache temp file (60 MB cap). `MediaMetadataRetriever.OPTION_CLOSEST_SYNC` gives keyframes (the same as `-skip_frame nokey`): ≤ 12 frames, 512 px, JPEG q70, MD5-deduped. The audio track is copied to `.m4a` (AAC passthrough, ≤ 180 s, because Android has no MP3 encoder). There is one Gemini call with a chunked streamed body (base64 read from disk, no OOM). Temp files are deleted in `finally`.
- I checked AAC m4a + 12 stills against OpenRouter flash-lite on this reel: HTTP 200, 15 ingredients, $0.0011, the same as the MP3 path.
- Unit tests (`SaiCascadeTest`, local fake server) assert:
  - 1 SC post call
  - stage 3 runs once and stops the cascade at ≥ 3 grounded ingredients (an invented "ghee" is dropped)
  - stage 4 sends `image_url` + `input_audio` and never `video_url`
  - duplicate frames are dropped
  - the full-video re-read happens only on a thin result
  - temp dir is empty afterwards

## SC transcript audit
`rg -in transcript app/src tools/*.py` finds only comments, the harness counter (0) and guard tests (`noCreatorsTranscriptCodePath`, `creatorsPostOnlyNeverTranscript`). No code calls a transcript endpoint.
