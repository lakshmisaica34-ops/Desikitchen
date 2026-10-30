# DesiKitchen recipe scraper (web, Instagram, TikTok, YouTube, Pinterest, Facebook, camera)

## How to import
- **Share → DesiKitchen** from Chrome (or any app) with a recipe URL. The app opens *Import from web* and starts fetching right away.
- **＋ → Import from web**: paste or type a URL and tap *Import recipe*. A missing `https://` is added, and `http://` is upgraded.
- The parsed draft opens in the review editor. Nothing is saved until you confirm.
- **Instagram posts and reels** (share a reel → DesiKitchen, or paste the link on Home / Import from link) go through the RAC pipeline below.
- **Paste import** is the fallback for anything the scraper can't read: Cloudflare-blocked sites, private posts, Facebook/YouTube captions, WhatsApp forwards. Copy the text and paste it. `RecipeTextParser` splits the title, ingredients and steps. When a fetch fails, the error screen offers Paste import pre-filled with whatever caption text was seen, plus any honest partial draft (title + photo only).

## 2026-10-29: AI caption split when RAC is not confident (reel DfEAPhVzLrs)
Before: stage 1 / 2 stopped as soon as `CaptionRecipeParser.hasRecipe` (≥ 2 ingredients, or a heading + ≥ 1 step); no AI ever re-checked a caption RAC.
Now a caption RAC wins with no AI only when `isConfident` = both buckets filled (≥ 2 ingredients, ≥ 1 step) and nothing was re-bucketed
(method lines under "Ingredients:", chatter dropped, or sanitizer removals). Otherwise ONE cheap text-model call
(`TextRecipeExtractor.CAPTION_SPLIT_SYSTEM`) splits that caption (stage `1b_caption_ai_split` / `2b_caption_ai_split`); its output is grounded
against the caption (`RecipeGrounding`, `RecipeSanitizer.groundedSteps`) and sanitized. Both buckets filled → win (PARTIAL). Otherwise the best
clean caption draft is kept as a fallback and the cascade continues (SC → comments → stills/video); the fallback is returned only if nothing later wins.
Builds with no AI configured keep the old behaviour. `Trace.captionSplitCalls` counts the split calls. Tests: `CaptionAiSplitTest`; live: `RacSplitReelLiveCheck`.

## 1.1.3: Sai's exact 4-step Instagram cascade (keyframes + audio, measured)
Stops at the first stage with a recipe. Every stage is timed in `InstagramImporter.lastTrace` (stage seconds, winner, SC calls, OpenRouter calls, notes).
1. **Public HTML/embed (free):** caption → rule parse. RAC → FULL/PARTIAL, stop.
2. **ONE Scrape Creators `/instagram/post` (1 credit):** video_url, has_audio, longer caption, **top comments** (`CreatorsApiClient.topComments`: creator's own first, then by likes, ≤ 15). Free caption re-parse. Never an SC transcript.
3. **Cheapest text model on comments + caption** (`TextRecipeExtractor.cheapFromBuildConfig()`, default `mistralai/mistral-nemo`, `COMMENTS_SYSTEM` prompt). `RecipeGrounding` drops any ingredient whose words aren't in the text. ≥ 3 grounded → PARTIAL, stop.
4. **Keyframes + audio → Gemini** (`StillsRecipeExtractor`, default `google/gemini-2.5-flash-lite`): video → temp file (≤ 60 MB, streamed) → `MediaMetadataRetriever` CLOSEST_SYNC keyframes (≤ 12, 512 px) + AAC audio copied to m4a (no re-encode) → one strict-JSON call with a streamed chunked body → temp dir deleted.
   - **Thin result (< 4 ingredients) only:** one full-video re-read via the remote URL (`VideoRecipeExtractor.extractRemoteOnly`), logged as `4d_full_video_reread`. The full video is never sent first.
   - Photo posts (no video): the still image goes to `extractStill`.
Measured on Sai's reel (`tools/scraper_harness_report.md`): **13.0 s, 1 SC credit, 0 transcript, 2 OpenRouter calls, $0.001106**. Winner: stage 4, with 15 ingredients and 11 steps. Control DF6dMW5vwgw: stage 1 FULL in 0.3 s, $0.
Build config: `OPENROUTER_TEXT_MODEL`, `OPENROUTER_STILLS_MODEL` (local.properties or env; defaults above). Harness: `OPENROUTER_TEXT_MODEL` / `OPENROUTER_STILLS_MODEL` env, `HARNESS_NO_REREAD=1`.

## 1.1.2 (superseded for Instagram by 1.1.3): cost-escalation cascade (Sai's design, no Scrape Creators transcripts)
Sai's degrade order, quoted from his desktop pipeline: **"caption → comments → stills → video"**. Never invent anything, log the cost of every paid call, and never store a video.
Sai's rule: *"we dont need transcript from scrape creators (sc for short)"*. **No code path calls any Scrape Creators transcript endpoint** for Instagram, TikTok, YouTube or Facebook. `fetchTranscript` / `parseTranscript` / `transcriptRequest` were deleted from `CreatorsApiClient` and `SocialCreatorsClient`, and `ImportCrashGuardTest.noCreatorsTranscriptCodePath` fails the build if one comes back.

Instagram (`InstagramImporter`). It stops at the first stage that yields ingredients:
1. **Caption (FREE):** public embed → OpenGraph/meta caption → `CaptionRecipeParser`.
2. **SC `/instagram/post` only (1 credit):** video_url, full caption, thumbnail, author → caption parse again.
3. **Comments (FREE, same response):** the creator's *own* comments (`edge_media_to_parent_comment` / `edge_media_preview_comment`) are parsed because the recipe is often pinned there. Strangers' comments are ignored (never invent). Adds the warning "Recipe taken from a comment on the reel."
4. **Text AI** (OpenRouter) only when the caption *looks* like a recipe (`looksLikeRecipeText`: ≥ 80 chars and ≥ 3 quantity tokens). Chit-chat captions skip this call.
5. **Stills → video:** photo/carousel posts (no video) send the still (`image_url`) to OpenRouter. Reels send the **remote video URL** (`video_url` part) to OpenRouter Gemini, which sees and hears it. Only if OpenRouter *refuses the URL* (throws) is the ≤ 8 MB streamed temp-file base64 download used (last resort). A successful URL call that finds nothing does **not** trigger a second, paid download.
   - Why reels skip stills: Sai's stills = sampled keyframes + audio, which needs a download plus ffmpeg (not on the phone). The cheap variant, the cover still alone, was measured on Sai's reel: 0 ingredients, +1 OpenRouter call and +8 s before escalating to video anyway (see `tools/scraper_harness_report.md`). So in the app, stills are used only where they *are* the content (photo posts).
6. Title: the AI's plain-English dish name ("Bread Pockets") wins over Hindi caption chit-chat.
7. **AI results are always PARTIAL** with a check-quantities warning. FULL comes only from a rule-based parse with **both** the ingredient and step arrays non-empty.

TikTok / YouTube / Facebook (`SocialRecipeImporter`): oEmbed/page caption → SC post endpoint only → text AI only if the caption looks like a recipe → OpenRouter video URL (TikTok). No transcripts.

1.1.1 crash/OOM guards are kept: every AI call is wrapped in a `Throwable` catch (OOM included) and the base64 fallback streams from a temp file.

**Harness** (`tools/scraper_harness.py`) mirrors the cascade and logs, per URL, every stage plus `seconds`, `sc_post_credits`, `sc_transcript_calls` (must be 0) and `openrouter_calls`. Flags: `HARNESS_JSONL=<file>` appends per URL and is resumable (it skips URLs that already have a RESULT); `HARNESS_FRESH=1` re-runs them; `HARNESS_TRY_STILLS=1` tries the cover still before the video (experiment); `HARNESS_DUMP_KEYS=1` logs SC response keys (never values). Keys are scrubbed from all output.

## 1.1.0 additions
- **TikTok / YouTube / Pinterest / Facebook** (`util/SocialRecipeImporter.kt`, `util/SocialCreatorsClient.kt`), routed by `WebRecipeFetcher`. Cheapest and most trustworthy first:
  1. Public oEmbed / OpenGraph page: caption or description, author, thumbnail.
  2. Caption parse (`CaptionRecipeParser`). Pinterest pins follow their outbound recipe link to the normal web parser.
  3. Scrape Creators public-post endpoints (`/v2/tiktok/video`, `/v1/youtube/video`, `/v1/facebook/post`, `/v1/pinterest/pin`) when the `CREATORS_API_*` keys are set (1.1.2: post endpoints only, no transcripts).
  4. OpenRouter: `TextRecipeExtractor` reads a recipe-like caption; `VideoRecipeExtractor` reads the video. Results are always PARTIAL with a "check it" warning and never invent content.
  5. Honest failure: partial draft + Paste prefilled.
- **Camera / gallery scan** (`ui/importer/ScanImportScreen.kt`, `util/OcrEngine.kt`, `util/OcrTextAssembler.kt`): on-device ML Kit Latin text recognition, up to 5 pages, 2-column reading order, OCR clean-up (hyphenation, bullets, `l tsp` → `1 tsp`), then `ImportParsing`.
- **Share sheet** (`util/SharedPayload.kt`): text, links, several items, .txt/.md files (Markdown flattened) and images (→ scanner).
- **Browse:** Import from web → "Search the web for …" opens the browser; share the recipe page back.
- **Ask DesiKitchen AI** reuses the same `OPENROUTER_API_KEY` / `OPENROUTER_MODEL` (`util/OpenRouterClient.kt`, `util/RecipeAiAssistant.kt`).
- Tests: `SocialRecipeImporterTest` (9, fixtures, no network) and `FeatureLogicTest` (OCR / share / AI with a fake transport).

## Instagram: recipe-as-caption (RAC) pipeline
`util/InstagramImporter.kt`, called by `WebRecipeFetcher` for `instagram.com/p|reel|reels|tv/<code>` links. Cheapest and most trustworthy first; it stops at the first step that yields a recipe.
1. **Public embed** `instagram.com/<kind>/<code>/embed/captioned/` → caption, author, thumbnail, video URL when present. If no caption, the **post page's OpenGraph / meta description** is tried.
2. **Caption parse** (`util/CaptionRecipeParser.kt`): finds an ingredient list and steps in the caption (headings like *Ingredients / Method / Recipe*, bullet/emoji/numbered lines, "Name – qty" lines), strips hashtags, mentions and CTA lines, cleans the title. A recipe counts only if it has ingredients; result is FULL with steps, PARTIAL without.
3. **No RAC in the caption:**
   a. **Scrape Creators API** (`util/CreatorsApiClient.kt`, wired in 1.0.2) – `GET /instagram/post` returns the reel's video URL, caption, thumbnail and owner (including a caption the embed didn't show). If that caption contains a recipe, step 2 runs on it.
   b. **Comments** (1.1.2, free): the creator's own comments from the same SC response are parsed (pinned recipes).
   c. **Video reading** (`util/VideoRecipeExtractor.kt`, optional): (1.1.2) **no Scrape Creators transcript.** Text AI runs only on a recipe-like caption. Then the video **URL** goes to OpenRouter (Gemini watches and listens), with no download. Only if that URL is refused does it stream a ≤ 8 MB download from a temp file as base64 in a chunked body, so the video is never held in memory (the 1.1.0 in-memory 20 MB path ran out of memory and closed the app). Photo posts send the still image instead. Default model `google/gemini-2.5-flash`, strict-JSON prompt that forbids guessing. Output is always PARTIAL with a "check every quantity against the reel" warning.
4. **Nothing found → honest failure**: a clear reason (private / blocked / caption has no recipe / video reading not set up), a PARTIAL draft with only the real title and photo (no ingredients or steps), and Paste import pre-filled with the caption.

**Never invent RAC:** no step fills in ingredients, quantities or steps that the caption, creators API caption or video did not state. Empty stays empty and is flagged.

## Configuration (build time; never hard-coded)
Values are read by `secret()` in `app/build.gradle.kts` from, in order: `local.properties` (git-ignored) → Gradle `-P` property → environment variable. Blank = feature off. They end up as `BuildConfig` fields.

| Key | Used by | Meaning |
|---|---|---|
| `CREATORS_API_URL` | `CreatorsApiClient` | Scrape Creators base URL, `https://api.scrapecreators.com/v1` (must start with `https://`). Blank → step 3a skipped. |
| `CREATORS_API_KEY` | `CreatorsApiClient` | Scrape Creators API key; sent as `x-api-key` (required by Scrape Creators) and also as `Authorization: Bearer …`. |
| `OPENROUTER_API_KEY` | `VideoRecipeExtractor`, `TextRecipeExtractor`, Ask DesiKitchen AI | OpenRouter key. Blank → step 3c skipped. |
| `OPENROUTER_MODEL` | `VideoRecipeExtractor` | Optional, default `google/gemini-2.5-flash` (any video-capable OpenRouter model). |

Example `local.properties`:
```
CREATORS_API_URL=https://api.scrapecreators.com/v1
CREATORS_API_KEY=...
OPENROUTER_API_KEY=sk-or-...
```
or `CREATORS_API_URL=… OPENROUTER_API_KEY=… ./gradlew :app:assembleRelease`.

**Wired provider: Scrape Creators** (`CreatorsApiClient.request()` / `fetchMedia()` / `parse()`):
```
GET {CREATORS_API_URL}/instagram/post?url=<url-encoded Instagram post/reel URL>
x-api-key: <CREATORS_API_KEY>
200 → { "data": { "xdt_shortcode_media": {
          "video_url": "https://…mp4",
          "display_url": "https://…jpg", "thumbnail_src": "https://…jpg",
          "owner": { "username": "…" },
          "edge_media_to_caption": { "edges": [ { "node": { "text": "caption…" } } ] } } } }
```
Field mapping in `parse()`: video ← `video_url` (else `videoUrl` / `video` / `video_versions[0].url`); caption ← `edge_media_to_caption.edges[0].node.text` (else `caption.text` / `caption` / `caption_text` / `description`); author ← `owner.username` (else `author` / `username` / string `owner`); thumbnail ← `thumbnail_src` (else `display_url` / `thumbnail_url` / `image`).
The media object is `data.xdt_shortcode_media`, falling back to `data`, `result`, or the root, so flat `{"video_url","caption",…}` responses from other providers still parse. Unit tests: `InstagramImporterTest.creatorsApiScrapeCreatorsShape` (docs-shaped fixture, no real keys) and `creatorsApiShapes`.

**Keys:** stay only in the git-ignored `local.properties` (never in source, tests, docs or commits). `python3 check_keys.py` (after a build) prints `present` / `absent` for each baked-in key without printing values. The **1.0.2 personal build includes the Scrape Creators and OpenRouter keys**, so steps 3a and 3b are on in that APK.

**Security:** anything compiled into an APK can be extracted (e.g. with apktool / jadx). Treat the 1.0.2 APK as a **personal test build only: do not share it**; anyone with the file can pull the keys and spend your quota. Rotate the keys if it leaks. Keys in `local.properties` are fine for a personal build; for sharing, run the creators API and the OpenRouter call on your own server and return the transcript in the creators response.

**Not implemented / stubbed:** a direct Gemini API path (`GEMINI_API_KEY`, generativelanguage.googleapis.com) is a `TODO(direct Gemini)` in `VideoRecipeExtractor`; OpenRouter covers Gemini today. (History: 1.0.1 shipped with a placeholder creators contract and no keys, so steps 3a/3b were off; 1.0.2 wires Scrape Creators and ships with keys for personal testing.)

## Code
| File | Role |
|---|---|
| `app/src/main/java/com/desikitchen/app/util/WebRecipeFetcher.kt` | Network: redirects, http→https, gzip, charset, 5 MB cap, honest UA. Reports blocked/404/social-login errors. `parseHtml()` is network-free (used by the tests). |
| `util/RecipeHtmlParser.kt` | HTML → `ImportDraft` via the strategies below, plus text clean-up (entities, mojibake, step normalisation, ISO-8601 durations, yields). |
| `util/LenientJson.kt` | Repairs real-world JSON-LD: raw newlines/control chars inside strings, `<!-- -->`/CDATA wrappers, trailing commas. |
| `util/InstagramImporter.kt` | Instagram RAC pipeline (embed → OG tags → caption → creators API → video AI → honest partial). |
| `util/CaptionRecipeParser.kt` | Caption → recipe (ingredients/steps detection, hashtag/CTA clean-up). Never invents. |
| `util/CreatorsApiClient.kt` | Scrape Creators client (`/instagram/post`, `x-api-key`; parses `xdt_shortcode_media`). |
| `util/VideoRecipeExtractor.kt` | Optional OpenRouter (Gemini) video → recipe JSON; off without a key. |
| `util/ImportImageStore.kt` | Saves the post thumbnail locally (Instagram CDN links expire). |
| `util/RecipeTextParser.kt` | Plain-text fallback, plus `parseIngredient()`: units/aliases, `200g`, `1½`, `1 1/2`, ranges `7-8`, `Onion – 1 cup`, `(15 ml)` notes. |

## Strategy: cheapest and most trustworthy first
1. **JSON-LD** schema.org `Recipe`. Handles `@graph`, `mainEntity`, array `@type`, `HowToSection`/`HowToStep`, `recipeYield` variants and ISO durations.
2. **Microdata** (`itemprop="recipeIngredient"` …).
3. **Recipe-card plugins** without JSON-LD: WPRM, Tasty, Mediavine Create, WPURP, EasyRecipe, Cooked.
4. **Heading heuristics**: an "Ingredients" or "Method/Instructions/Steps" heading followed by lists or paragraphs. Step lines that repeat the ingredient list, or look like ingredient lines (`2 tbsp ghee`, `Salt – to taste`), are dropped.
5. **Page-text fallback** (`RecipeTextParser`), marked PARTIAL.

Title, image and description fall back to OpenGraph or Twitter meta tags.

**Merge rule:** ingredients and steps are chosen independently. A structured source is kept unless it is nearly empty (<3 items). Then a later source wins only if it has at least 3 more items. Between two structured sources, the clearly richer one wins (≥2× and +3). Heading heuristics never override a structured list with ≥3 items.

**Completeness and warnings:** `Result.Success.completeness` is `FULL` only when both ingredients and steps are non-empty. Otherwise it's `PARTIAL`, and `warnings` say what is missing or which fields came from layout heuristics, so the user double-checks them.

## Tests
Run these from the project root with `JAVA_HOME=/opt/tools/jdk17`.
- **Offline fixtures** (CI-safe): `./gradlew :app:testDebugUnitTest`
  - `app/src/test/java/com/desikitchen/app/scraper/ScraperFixtureTest.kt` runs against 17 real pages saved as `app/src/test/resources/scraper/*.html.gz`, plus a Cloudflare challenge page. Indian sites: hebbarskitchen, indianhealthyrecipes, cookwithmanali, ministryofcurry, tarladalal, ranveerbrar, sanjeevkapoor, chefkunalkapur, sharmispassions, pipingpotcurry, teaforturmeric, myfoodstory, madhurasrecipe. Global: allrecipes, bbcgoodfood, simplyrecipes, bonappetit.
  - It also covers synthetic WPRM-only, microdata and heading-layout pages, "never invent missing fields", JSON repair, durations, ingredient lines and mojibake.
  - To refresh a fixture: `curl -sL <url> | gzip -9 > app/src/test/resources/scraper/<name>.html.gz`
- **Live check** (opt-in, uses the network):
  `SCRAPER_LIVE=1 ./gradlew :app:testDebugUnitTest --tests '*ScraperLiveCheck*' -i`
  - URLs come from `app/src/test/resources/scraper/live-urls.txt`, or from `SCRAPER_URLS` (comma-separated).
  - Results are appended as JSONL to `app/build/scraper-live.jsonl`. URLs already in the file are skipped, so a run can be resumed. `SCRAPER_FRESH=1` starts over.

## Known limitations
- **Bot-protected sites** (Cloudflare "Just a moment…" / 403) can't be fetched without browser impersonation, which we deliberately don't do. From a server IP this included vegrecipesofindia, whiskaffair, spiceupthecurry, budgetbytes, foodnetwork and food.ndtv. On a phone's home or mobile IP some of these may work. When they don't, the user gets a clear "use Paste import" message.
- **JS-rendered pages** that build the recipe client-side with no HTML, JSON-LD or card markup get only the OG title and image plus a PARTIAL warning.
- **Social/login sites** other than Instagram (Facebook, TikTok, Threads, X) are refused up front with a Paste-import hint. Instagram works for public posts via the embed; private posts and network blocks fall back to Paste import.
- **Bad source data is kept** if it can't be detected: e.g. a site whose `recipeYield` or times are wrong, or a redirect to a different recipe (tarladalal's old ID 1622 now serves a mushroom pasta).
- Ingredient parsing is rule-based. Rare formats keep the whole line as the name, with no quantity. Nothing is guessed.
- No OCR. Video reading only when an OpenRouter key is configured at build time.

## Design principles (adapted from the Instagram recipe pipeline)
These come from Sai's existing Instagram scraping pipeline, applied here to public web pages only:
- **Never invent.** Ingredients, quantities and steps come only from the page. If a list is empty, the result is demoted from FULL to PARTIAL with a warning. It is never filled in.
- **Graceful degradation, cheapest first:** structured data → plugin markup → heading heuristics → plain text → (future, optional) LLM.
- **Normalise, then prefer the richer merge by score.** Steps are de-numbered, deduped, split if delivered as one blob, and capped in length. Ingredients are trimmed and deduped. Each field takes the richer candidate under the merge rule above.
- **Resumable, append-only results with dedupe by id (URL)** in the live harness (`scraper-live.jsonl`).
- **Not carried over:** private APIs, cookie or session impersonation, and UA spoofing. DesiKitchen sends an honest UA (`Mozilla/5.0 (Linux; Android 14; Mobile) DesiKitchen/<version> (recipe import)`; the Mozilla token is the standard compatibility prefix, it does not claim to be Chrome), carries no cookies, and stops at bot walls and login pages.

### Optional future LLM step for web pages (not implemented; the Instagram video step above is separate)
- **When it would run:** only when the result is PARTIAL after all the rules above, or on pasted free-text captions, and only with user opt-in.
- **Input:** the cleaned page text, trimmed to about 6–8k characters. Never the raw HTML.
- **Output:** strict JSON with `{title, ingredients[], steps[]}`. Every ingredient and step must appear verbatim (or near-verbatim) in the input, and anything else is rejected, keeping the "never invent" rule.
- **Cost and limits:** about 2–3k input tokens and 0.5–1k output tokens per recipe, which is a fraction of a US cent on a small hosted model. It needs network access and an API key, which must not ship inside the APK, so it would have to go through a backend proxy. That adds latency (about 2–5 s) and privacy considerations (page text leaves the device). An on-device model would avoid the cost but adds hundreds of MB and is slow on low-end phones.

## Crash guard (1.1.1)
`WebRecipeFetcher.fetch` never throws. Any Throwable, OutOfMemoryError included, becomes `Result.Failure`, and the import UI also wraps the call. Scrape Creators error bodies (`{"success":false,"error":"not_found",...}`) become clean messages via `CreatorsApiClient.errorOf`. Pre-flight harness: `python3 tools/scraper_harness.py [--no-video] URL...` (`HARNESS_FORCE_ALL=1` runs every paid fallback). See `tools/scraper_harness_report.md`.
