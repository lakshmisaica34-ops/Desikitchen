# DesiKitchen: build status (done)

## 1.2.0 (29 Oct 2026, IST): versionCode 10, rebuild: background import, soft UI, no demo content, small APK
- **Background import:** `util/BackgroundImporter` (app-scoped, `StateFlow<ImportJob>`: Idle/Running/Ready/Failed/Duplicate). The paste screen and share sheet start the job and return right away. A bottom status pill in `DesiKitchenRoot` shows the stage with ✕ cancel, then "Recipe ready · Review", which opens the review screen. Failures show the message and "Open draft" when there's a partial result. Duplicates show "Already saved · Open". Cancelling ignores any late result.
- **Less text:** the paste screen is just the link field, Import and the quota line ("n of 5 free imports left this week"). Filler copy is trimmed in Search, Saved, Collections, Plan, Coco and More, and the "Not included" card is gone.
- **No demo content:** the seed data, the sample recipe and the default name are removed. There are no popular recipes or ratings.
- **Simpler review:** one line per ingredient (qty · unit · name, no aisle or note in review), numbered steps with no timers, and airy rounded cards. The Note overlap in edit mode is fixed: there's no fixed row height, and note/aisle sit on their own row.
- **Soft UI:** new `ui/components/Soft.kt` (SoftCard, SoftField, PillButton, NumberBadge). The background is a warm canvas, with larger radii, soft shadows and borderless fields. Mustard #F6A823 stays the accent.
- **APK size:** the ML Kit text-recognition dependency and the dead `OcrEngine`/`ScanImportScreen` code are removed. `abiFilters` is arm64-v8a only. 1.1.5 was ~64 MB; 1.2.0 is **19.8 MB** (19,848,962 bytes, `DesiKitchen-rebuild.apk`). YT/IG import code is unchanged.
- **Tests:** `testDebugUnitTest` 103 tests, 0 failures (3 skipped). New `BackgroundImporterTest` (4 tests). `assembleDebug` ✅. The keys are compiled in, so don't share the APK.

## 1.1.5 (29 Oct 2026, IST): versionCode 9, fix for "Import failed (ExceptionInInitializerError)"
- **Root cause:** `CaptionRecipeParser.stepNumber` used `[:.)\-]?|…[.)\-:]`. Android's regex engine is ICU, which reads a class starting with `[:` as a POSIX `[:prop:]` and throws `PatternSyntaxException` (U_REGEX_PROPERTY_SYNTAX, offset 44) when a `:]` follows. The JDK accepts it, so JVM tests passed. On a phone the object's static init failed, so every Instagram/social import failed, and Paste/Scan through `ImportParsing` did too. The bug has been latent since 1.1.0.
- **Fix:** escaped `:` in every regex class (`[\\:…]`). Added `WebRecipeFetcher.validate()`, which runs offline in the Web import screen and in `fetchUnsafe` before any importer. `ImportErrors` turns LinkageError/OOM/etc. into readable text and logs the real cause. `ImportParsing` falls back to the plain parser if the caption parser throws, and the Paste/Scan call sites are guarded.
- **Tests:** `IcuRegexCompatTest` lints every Regex literal and every static importer pattern for ICU pitfalls, checks stepNumber behaviour, and maps static-init failures to readable messages. `UrlValidationTest` covers validation. For an exact ICU check, `python3 tools/icu_regex_check.py` compiles every literal with the system libicu (old code: 1 failure; now: 0).
- **Verified before build:** `tools/icu_regex_check.py` 0/106 ICU failures; `testDebugUnitTest` 79 tests, 0 failures (3 skipped). Live harness (`tools/harness_run_115.jsonl`): DeH0TduS8lG FULL 5/4 from the free stage, 0 credits; Ddk4k1PoM-k PARTIAL (stage 4), no crash, 1 SC post credit, 0 transcripts, 2 OpenRouter calls. The app's own Kotlin importer, run live on the JVM (`tools/kotlin_live_115.jsonl`), also gave DeH0TduS8lG FULL 5/4.
- **APK:** `DesiKitchen-1.1.5.apk` (+ `.sha256`), sha256 `5cff561dd753f6d742fded4897534c1427fcd8114beea93534592c87e761b930`. Same debug cert (`8fcc6700…812c`), so it installs over 1.1.4. The dex contains the escaped pattern and not the old one. The keys are compiled in, so don't share it. Drive (My Drive root, private): https://drive.google.com/file/d/1LwqgEqIfyV8KQYJy6yLkfW0tmTI0LvbT/view

## 1.1.4 (29 Oct 2026, IST): versionCode 8, caption step-heading fix
- `CaptionRecipeParser`: up to 2 qualifier words are now allowed before a step heading ("Quick Steps:", "Easy Method", "Simple Steps", …).
- 69 unit tests pass, 0 failures (3 opt-in live checks skipped).
- Harness (`tools/harness_run_114.jsonl`): DeH0TduS8lG FULL from the stage 1 caption, 5 ingredients / 4 steps, 0.4 s, $0. Sai's reel Ddk4k1PoM-k PARTIAL from stage 4 (keyframes + audio), 15 ingredients / 11 steps, 13.0 s, 1 SC credit, 2 OpenRouter calls, $0.001106.
- **APK:** `DesiKitchen-1.1.4.apk` (+ `.sha256`), same debug cert (`8fcc6700…812c`), installs over 1.1.3. The keys are compiled in, so don't share it. Drive (My Drive root, private): https://drive.google.com/file/d/1FtKUc-FwCF-QdUFwVKEAfvQRhdBupbzB/view

## 1.1.3 (29 Oct 2026, IST): versionCode 7, Sai's exact 4-step cascade
- Instagram: public HTML → 1 SC `/instagram/post` → mistral-nemo on top comments (grounded) → keyframes + audio → gemini-2.5-flash-lite (full-video re-read only if < 4 ingredients). See SCRAPER.md "1.1.3".
- Harness on Sai's reel: 13.0 s, $0.001106, 1 SC credit, 0 transcript, 2 OpenRouter calls → 15 ingredients / 11 steps (`tools/scraper_harness_report.md`).
- 67 unit tests pass (3 opt-in live checks skipped). New `SaiCascadeTest`. **APK:** `DesiKitchen-1.1.3.apk` (+ `.sha256`), same debug cert (`8fcc6700…812c`), installs over 1.1.2. The keys are compiled in, so don't share it. Drive (My Drive root, private): https://drive.google.com/file/d/1SykR8pp-Wnmfm-RB1vedfXOIueDK4Bth/view

## 1.1.2 (29 Oct 2026, IST): versionCode 6, cost cascade, no SC transcripts
- Scraper follows Sai's degrade order **caption → comments → stills → video** (see SCRAPER.md, "1.1.2").
- Every Scrape Creators transcript call was removed (IG + TikTok/YouTube/Facebook). SC `/instagram/post` only, 1 credit. A guard test is in `ImportCrashGuardTest`.
- Free creator-comments parse, text AI only for recipe-like captions, OpenRouter video URL first, ≤ 8 MB download only if the URL is refused, still image for photo posts, AI plain-English title.
- Sai's reel: PARTIAL "Bread Pockets", 1 SC + 1 OpenRouter, 22–25 s (1.1.1 used 2 SC + 2 OR, 35 s). Caption reels and Hebbars: FULL, 0 paid calls. See `tools/scraper_harness_report.md`.
- 62 unit tests pass. **APK:** `DesiKitchen-1.1.2.apk` (+ `.sha256`), same debug cert (`8fcc6700…812c`), installs over 1.1.1. The keys are compiled in, so don't share it.

## 1.1.1 (29 Oct 2026, IST): versionCode 5, crash fix
Reported: "Scraper not working, app getting closed." The scraper was tested first with a standalone harness, then the app was fixed.
- **Harness** (`tools/scraper_harness.py`, report `tools/scraper_harness_report.md`; keys read from local.properties and never logged):
  - Hebbar's masala dosa: FULL (JSON-LD 22/30).
  - IG DF6dMW5vwgw: FULL (8/4). IG CcOkDyrpHkD: FULL (11/6). Every paid fallback also passes when forced.
  - Sai's reel Ddk4k1PoM-k: the caption has no recipe, and the video is 18.5 MB. The IG transcript plus OpenRouter text gives 15 ingredients. Sending the remote video URL to OpenRouter gives 14/9.
  - IG DYWUyTmJsw3: PARTIAL (12/0).
  - Dead IG post: honest 404 failure.
  - YouTube short ETGWelKhgfc: FULL, via the recipe link in its description.
- **Crash cause:** 1.1.0 sent a reel's video (≤ 20 MB) to the AI as an in-memory base64 JSON string. For Sai's 18.5 MB reel that needs several copies of the video in memory at once, which caused an OutOfMemoryError. That is an Error, not an Exception, so nothing caught it. It escaped the import coroutine, and the app closed.
- **Fixes:**
  - `WebRecipeFetcher.fetch` catches every Throwable, OOM included (only cancellation is rethrown), and always returns `Result.Failure`.
  - The WebImportScreen coroutine has try/catch/finally, so the error shows and the spinner always stops.
  - The share-sheet intent parsing, the share navigation, the Home paste-URL navigation and the scan photo import are all guarded.
  - `CreatorsApiClient`:
    - `fetch()` returns an `Outcome`, and `errorOf()` turns a 404 or `success:false`/`not_found` body into "post not found (deleted/private)".
    - `parse()` never throws.
    - New `fetchTranscript()` (`/v2/instagram/media/transcript`).
  - New Instagram order: embed → caption → Scrape Creators post → **transcript + caption → OpenRouter text** → **video AI, sending the remote URL first (no download on the phone)** → as a last resort, a ≤ 8 MB video streamed from a temp file into a chunked request (never held in memory).
  - YouTube/TikTok/Facebook: when the description only links to the creator's recipe page, that page is imported (FULL).
- **Tests:** 58 run, 56 pass, 0 failures, 2 opt-in skipped. New `scraper/ImportCrashGuardTest` (7) covers:
  - IG or social path throws, or throws OOM: `fetch` returns a Failure.
  - Scrape Creators 404 / `success:false` / HTML / garbage bodies.
  - Transcript parsing.
  - Description-link picking.
  
  New opt-in `SaiReelLiveCheck` (`SCRAPER_LIVE=1`): Sai's reel through the real 1.1.1 code gives PARTIAL with 15 ingredients / 10 steps, and the video path alone gives 16/14 with no exception.
- **APK:** `DesiKitchen-1.1.1.apk` (+ `.sha256`), signed with the same box debug key (cert SHA-256 `8fcc6700…812c`), so it installs over 1.1.0. The keys are compiled in, so don't share the APK.

## 1.1.0 (29 Oct 2026, IST) — versionCode 4
Full public-ReciMe feature pass under the locked v1 UI (tabs and drawer unchanged). See **FEATURE_PARITY.md** (34 items: 33 Done, 1 Stub = Order Online).
- **Import:**
  - Camera/gallery OCR scan (ML Kit, multi-photo, 2-column order).
  - Share sheet for images, multiple items and .txt/.md files.
  - TikTok / YouTube / Pinterest / Facebook importer (public oEmbed/OG → caption → Scrape Creators post + transcript → OpenRouter text/video AI; never invents content).
  - "Browse / search the web" button.
- **Recipe:**
  - PDF export/share and Print.
  - Persisted Mark cooked (count + last date) with confetti.
  - Photo from gallery/camera (detail + editor).
  - Metric | Imperial toggle.
  - Ask DesiKitchen AI via OpenRouter (scoped, with history, servings and step; offline fallback).
- **Cook mode:** "Mark cooked & finish"; Ask is aware of the current step.
- **Meal plan:**
  - Weeks −1 … +8.
  - Long-press drag and drop between slots/meals; Move to…, Move up/down, per-meal servings.
  - Multi-select remove; any start day.
  - Auto grocery sync.
- **Groceries:**
  - Recently added sort.
  - Source filters.
  - Have-at-home / Deselect pantry staples.
  - Recipe photo carousel.
  - Add a recipe's ingredients.
  - Share list.
  - Order Online stub sheet (copy list + open store).
- **Search:** ingredients, tags and all-word matching on Home / Recipes; search inside cookbooks; multi-select add to cookbook.
- **Profile:**
  - Auto-sync switch.
  - Cooked stats.
  - AI status (On / Offline, never the key).
  - "Not included" card.
- **DB v2** with a real `MIGRATION_1_2` (no data loss).
- **Tests:** 51 run, 49 pass, 0 failures, 2 opt-in skipped (live scraper check, screenshot tour). New: `feature/FeatureLogicTest` (11) and `feature/DatabaseAndPdfTest` (2, Robolectric: real v1 DB → Room v2 validation, PDF pagination). Robolectric has no native PdfDocument, so PDF drawing is device-only.
- **APK:** `DesiKitchen-1.1.0.apk` (+ `.sha256`). Signed with the same box debug key as 1.0.2 (cert SHA-256 `8fcc6700…812c`), so it installs over it. Personal build: the Scrape Creators and OpenRouter keys from local.properties are compiled in (extractable), so don't share the APK.

## 1.0.2 (29 Oct 2026, IST) — versionCode 3
- Creators step wired to **Scrape Creators**: `GET {CREATORS_API_URL}/instagram/post?url=…` (was the placeholder `/instagram/media`), `x-api-key` header (Bearer kept). `parse()` reads `data.xdt_shortcode_media` (video_url, thumbnail_src/display_url, owner.username, edge_media_to_caption.edges[0].node.text) with the old flat / `data` / `result` / `video_versions` fallbacks.
- OpenRouter video path unchanged.
- Personal build: Scrape Creators + OpenRouter keys come from git-ignored `local.properties` and **are compiled into this APK** (extractable; personal testing only, don't share). `check_keys.py` reports present/absent without values.
- New unit test `creatorsApiScrapeCreatorsShape`; 29 tests, 0 failures (2 opt-in skipped).
- APK: `DesiKitchen-1.0.2.apk` (+ `.sha256`), signed with the same box debug key as 1.0.1, so it installs over 1.0.1. Copy in Google Drive (My Drive root, private).

## 1.0.1 (29 Oct 2026, IST)
- UI rebuilt to the locked `mocks/v1/` design: Home / Favorites / Grocery / Profile tabs, ☰ drawer, v1 recipe page (pantry ticks, timer + temperature chips, active-step card, green grocery CTA), v1 cook mode. Mustard #F6A823; sage #47B977 for grocery and timers.
- Instagram recipe-as-caption import (embed → OG tags → caption parse → creators API placeholder → OpenRouter video, off without keys → honest partial + Paste). Secrets only from local.properties / -P / env; the shipped APK has none. See SCRAPER.md.
- Visual check: `UiTourScreenshots` (Robolectric 4.13 + Roborazzi) renders 12 screens to `app/build/screens/`; skipped unless `-Pscreens=1`.
- Fixed: system Back now closes the drawer.
- Deviations from the mock: no ratings/authors in data (Diet card instead of Rating; byline from description/cuisine), no ingredient drag handle, bell opens Meal Plan, cook-mode photo is the recipe photo.


Completed 28 Oct 2026 (IST). `./gradlew :app:assembleDebug` succeeds; `:app:lintDebug` has 0 errors; `:app:testDebugUnitTest` passes.

**Debug APK:** `app/build/outputs/apk/debug/app-debug.apk` (~18 MB)

## Toolchain (on the box, outside the project)
- JDK 17: `/opt/tools/jdk17` · Android SDK: `/opt/tools/android-sdk` · Gradle 8.9 wrapper
- AGP 8.5.2, Kotlin 2.0.21, KSP, Compose BOM 2024.09.03, Navigation 2.8.2, Room 2.6.1, Lifecycle 2.8.6, Coil 2.7.0
- compileSdk/targetSdk 34, minSdk 26

## Source files (package `com.desikitchen.app`)
### App, DI, data (pre-existing; small extensions marked *)
- DesiKitchenApp.kt, AppContainer.kt, MainActivity.kt* (share/VIEW intent handling, singleTop)
- data/model/Entities.kt* (added `MealSlot.SNACK`), data/db/{Converters,Daos,DesiKitchenDatabase}.kt
- data/repo/Repositories.kt, data/repo/SettingsRepository.kt* (added `weekStart`)
- data/seed/SeedData.kt (12 desi recipes, 3 sample cookbooks)

### Utilities
- util/Units.kt, util/RecipeTextParser.kt (pre-existing)
- util/ImportDraft.kt: hand-off of parsed drafts from import flows to the review editor
- util/WebRecipeFetcher.kt, util/RecipeHtmlParser.kt, util/LenientJson.kt: web recipe import (JSON-LD → microdata → recipe cards → headings → text). See SCRAPER.md
- util/RecipeAssistant.kt: offline "Ask DesiKitchen" (Substitute / Healthier / Simpler / Understand + free text)
- util/NutritionEstimator.kt: rough per-serving macros from a built-in ingredient table

### UI
- ui/AppViewModelProvider.kt*, ui/theme/Theme.kt, ui/components/Components.kt
- ui/components/Extras.kt: DkTopBar, KeepScreenOn
- ui/navigation/DesiKitchenRoot.kt: NavHost, bottom tabs (Home · Favorites · Grocery · Profile), ☰ drawer, ＋ sheet
- ui/home/{HomeScreen, FavoritesScreen}.kt: v1 home and favourites
- ui/detail/KitchenTimers.kt: per-step timers, temperature chip
- ui/cookbooks/{CookbooksViewModel, CookbooksScreen, CookbookDetailScreen}.kt
- ui/home/{HomeViewModel, RecipesPane}.kt: recipes grid, tag filters, pinned row, All recipes screen
- ui/detail/{RecipeDetailViewModel, RecipeDetailScreen, StepText, AssistantSheets}.kt
- ui/cooking/{CookingViewModel, CookingScreen}.kt
- ui/edit/{AddEditRecipeViewModel, AddEditRecipeScreen}.kt (also the import review screen)
- ui/importer/ImportScreens.kt: Paste text, Web import (+ browse/search the web); ui/importer/ScanImportScreen.kt: camera/gallery OCR
- ui/plan/{PlanViewModel, PlanScreen}.kt
- ui/grocery/{GroceryViewModel, GroceryScreen}.kt
- ui/more/{MoreViewModel, MoreScreen}.kt

### Tests
- app/src/test/.../CoreLogicTest.kt: parser, units, URL extraction, step highlighting, assistant, nutrition
- app/src/test/.../scraper/{ScraperFixtureTest, InstagramImporterTest, ScraperLiveCheck}.kt
- app/src/test/.../scraper/SocialRecipeImporterTest.kt: TikTok/YouTube/Pinterest/Facebook importer (no network)
- app/src/test/.../feature/FeatureLogicTest.kt: OCR assembly, share payloads/Markdown, plan→grocery sync, pantry staples, grocery list builder (sorts/filters/have-at-home/share), PDF blocks/pagination, ingredient search, OpenRouter AI prompt/answer/fallback (fake transport)
- app/src/test/.../feature/DatabaseAndPdfTest.kt: DB v1→v2 migration on a real v1 schema, PDF pagination
- app/src/test/.../ui/UiTourScreenshots.kt: screenshot tour (-Pscreens=1)

## Known gaps / TODO
- (1.1.0) Camera OCR, AI chat, photo picker and persisted cooked stats are done. Order Online is an honest stub; cloud sync / collaborative cookbooks / web app are out of scope (FEATURE_PARITY.md).
- Nutrition is an estimate from a built-in table, not a nutrition database.
- Web import reads public pages only; sites without schema.org data fall back to text parsing (review needed).
- Not run on a device/emulator (box user lacks /dev/kvm permission); verified via compile, lint, unit tests and the Robolectric screenshot tour.
