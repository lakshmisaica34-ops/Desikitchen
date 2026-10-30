# DesiKitchen — Phase 1 progress (2026-10-29)

Scope: Phase 1 **features only**, per `MASTER_PRODUCT_SPEC.md`, `SPEC_GAP_REPORT.md` and Sai's locks of 2026-10-29.
Theme is **out of scope and untouched** (mustard/green ReciMe v1).

## Theme assertion
- `ui/theme/Theme.kt` is byte-identical to the pre-Phase-1 baseline, and so are `res/values/colors.xml` and `res/values/themes.xml`. The dark-mode policy and launcher are unchanged.
- `Theme.kt:23` → `val Mustard = Color(0xFFF6A823)` ✅
- No Espresso / Ember / Porcelain / Nunito / Inter anywhere in `ui/theme/` or `res/values*`. Checked with grep; 0 hits. ✅
- `res/font/plus_jakarta_sans.ttf` (+ `assets/PlusJakartaSans-OFL.txt`) was **restored** from the pre-fix backup. `Theme.kt` already referenced it, and the build failed without it. This restores a missing v1 asset; it is not a theme change.
- The Search screen, the extracting card and the duplicate dialog (second batch) also use existing tokens only; theme files are still untouched.
- New screens use existing tokens only: Mustard, MustardDeep, MustardCream, MustardPale, Ink and Hairline.

## What landed
| Lock | Status |
|---|---|
| 1. Tabs: Collections · Discover · **Assistant** (centre, mustard ring + Coco mark) · Saved · Profile | ✅ `ui/navigation/DesiKitchenRoot.kt` rewritten. The drawer and "Add recipe" sheet are removed. Start tab is Discover. |
| 2. 5 free imports / week | ✅ `util/ImportQuota.kt` (rolling 7-day window), stored in `SettingsRepository.importTimes`. A **new imported recipe save** counts as one import. The paste-link screen shows "N of 5 free imports left this week" and blocks with a Plus-coming-soon note when none are left. |
| 3. YouTube + Instagram only | ✅ `ImportUrlValidator.validateSupported` / `isSupportedHost` is used by the paste-link screen and the share sheet. Website/TikTok/Pinterest/Facebook links get `UNSUPPORTED`. The paste-text, camera/OCR and web-browse entry points are gone from the UI, and the image share intent-filter is removed. The parser code (`RecipeHtmlParser`, `SocialRecipeImporter`, `PasteImportScreen`) stayed for tests and later use; in 1.2.0 `PasteImportScreen`, `ScanImportScreen`, `OcrEngine` and ML Kit were deleted. The generic `validate` is unchanged. |
| 4. Clean recipe detail | ✅ `RecipeDetailScreen.kt` rewritten. It shows a hero, the title, a creator credit ("By @handle · Instagram"), the description, collection membership, an **Open original** button (from `sourceUrl`), ingredients and numbered steps. The stats row, checkboxes, timers, nutrition sheet, "I cooked this", confetti and grocery sticky bar are removed. Edit, favourite, collection, share and delete are kept. The overflow menu holds Cook mode, Ask Coco about this recipe (per-recipe sheet), Add to meal plan, Add to grocery list, Change photo, Export PDF and Delete. |
| 5. Grocery secondary; meal plan inside Saved | ✅ Saved has a **Meal plan** section (the next 7 days, with "Open planner") and a **Grocery list** entry row. Plan and Grocery are secondary routes, not tabs. |
| 6. Coco avatar | ✅ `res/drawable-nodpi/coco_mark.png` is a 256px square crop of `mocks/icon/desikitchen-mark-final.png`. It is used in the Assistant header, the chat bubbles and the centre tab. The brand name stays DesiKitchen. |
| 7. Wipe demo seed | ✅ There is no seed on a fresh install (`AppContainer`). `SeedData` remains for tests only. `displayName` defaults to "". |
| 8. Local-first | ✅ No auth or backend was added. |
| 9. Add → paste-link; share sheet for YT/IG | ✅ The Discover import card and the share sheet start `BackgroundImporter` directly (via `ImportStatusViewModel`); the Saved/Search "Paste a link" entries open the `import/web` field, which also hands off to the background importer and closes at once. A floating status pill shows the stage + cancel, then Review / Open draft / "Already saved … Open it". A shared link that isn't YT/IG shows a snackbar with the unsupported message. |
| 10. Provenance | ✅ `ImportDraft` → save writes `sourceUrl`, `sourcePlatform`, `sourceVideoId` and `creatorHandle`. Nothing is stuffed into the description. Open original uses `sourceUrl`. Share/PDF include "Original: url". DB is v3; migration test updated to 1→2→3. |
| 11. Meta-safe thumbnails | ✅ `ImportImageStore.persistEnabled = false` by default, so remote thumbnails are hotlinked. If one fails, it falls back to the existing emoji/gradient art. |
| 12. Discover home | ✅ `ui/discover/DiscoverScreen.kt` shows, in order: "What's the plan, {first name}?" (falls back to "chef"), the **paste-link card above Recently saved**, horizontal Recently saved cards (newest 12) and a locked curated collections row. Popular Recipes and emoji category bubbles are removed (`HomeScreen.kt` deleted). |
| 13. Saved | ✅ `ui/saved/SavedScreen.kt` + `SavedViewModel` replace Favorites, All recipes and Cookbooks. Saved holds the Meal plan, a Grocery entry, text-first **collections with counts** (new/rename/delete; tap opens the collection detail) and the library. The library has search (dish or ingredient) and an All/Favourites filter with a favourite toggle. There are empty states for each. `FavoritesScreen.kt` is deleted. |
| 14. Collections tab | ✅ `ui/collections/CollectionsScreen.kt` lists 8 curated catalogue **names only**, each marked with a 🔒 Plus badge. Tapping one shows a "Plus coming soon" dialog. |
| 15. Assistant tab | ✅ `ui/assistant/AssistantScreen.kt` + `AssistantViewModel` + `util/LibraryAssistant.kt` give a text-first Coco chat over the saved library, with no limit. It uses OpenRouter when a key is configured at build time (BuildConfig, from the gitignored `local.properties`). Otherwise it uses an offline matcher that only quotes saved titles and ingredients. Answers show tappable chips for the matched recipes, and there are starter prompts and "New chat". The per-recipe "Ask Coco" sheet is kept on the detail page. |
| 16. Never invent / no keys | ✅ The system prompt forbids inventing ingredients, quantities or recipes, and the offline path only echoes stored data. No keys are in source, docs or chat. |

Profile (MoreScreen): the name is editable ("Add your name" when blank). The "No subscriptions, no paywalls" copy is replaced with "Free plan … Plus is coming soon", the feature list is updated to Phase 1, "Cookbooks"/"All recipes" now lead to Saved, and the AI row is renamed to Coco.

## Phase 1 gaps closed (2026-10-29, second batch)
| Gap | Status |
|---|---|
| Duplicate detection | ✅ `util/RecipeDedup.kt` normalises links. It drops `www.`/`m.`, fragments, trailing slashes, `utm_*`, and the `igsh`/`si`/`feature`/`fbclid`/`gclid`/`ref`/`share` params. YouTube keeps only `v=` and Instagram drops the whole query. It keys on platform + IG shortcode / YT video id, so `youtu.be/X`, `watch?v=X` and `/shorts/X` match, and so do `reel/`, `reels/` and `/{user}/p/`. New DAO queries are `RecipeDao.findBySourceVideo` and `RecipeDao.sourceLinks` (no schema change; DB stays v3). `RecipeRepository.findDuplicate(url)` uses them. `BackgroundImporter` checks **before** the import (before the quota, so a duplicate never uses one of the 5 weekly imports). It checks **again after the fetch** against the canonical `sourceUrl`. A match shows "Already saved: “Title” is already in your library. Open it?", and **Open it** goes to that recipe, replacing the import screen. |
| Import extracting UI | ✅ `util/ImportProgress.kt`: the cascade reports **real** stages as it enters them. They are Reading link, Reading the caption, Fetching post details, Opening the linked recipe page, Reading top comments, Reading video frames, Reading the video, Reading the photo and Almost done. They're reported through a thread-local listener from `InstagramImporter`, `SocialRecipeImporter` and `WebRecipeFetcher.fetch(url, onStage)`, with nothing timed or faked. The extracting card is MustardCream, with a tick for each finished stage and a MustardDeep spinner on the current one. **Cancel** (and top-bar back) cancels the coroutine. The UI resets at once, and the blocking cascade stops at the next stage boundary (`ensureActive`). A cancelled run can't overwrite a newer one, because a generation guard prevents it. |
| Search Results screen | ✅ `ui/search/SearchScreen.kt` + `SearchViewModel` (route `search?q=`), opened from a new **"Search your recipes"** row on Discover under the greeting. It has five states: an initial hint, loading (debounce and first Room emission), results, no results (with "Import a recipe") and error (with "Try again"). The logic is the existing `RecipeSearch` over the saved library plus ingredients, wrapped in pure `util/SearchResults.compute`. Results show "Has …" for ingredient matches, and otherwise the creator credit. There is no emoji in the screen's UI. |
| Dead-UI cleanup | ⏸ Deliberately left alone (low value, some risk). The unreachable screens listed below still compile. |

New tests: `feature/Phase1GapsTest` (9 tests: YT/IG/web dedup keys, the Room `findBySourceVideo`/`sourceLinks`/`findDuplicate` test, real IG caption stages from a fixture, stage forwarding through `fetch`, cancel stopping the cascade, and the Search state logic).

## Build & tests
- `./gradlew :app:compileDebugKotlin` ✅ and `:app:assembleDebug` ✅ (`app/build/outputs/apk/debug/app-debug.apk`).
- `./gradlew :app:testDebugUnitTest` ✅: **99 tests, 0 failures**, 3 skipped (opt-in live/screenshot checks); 90 before the gaps batch + 9 in `Phase1GapsTest`. All scraper suites are green: UrlValidation, ScraperFixture, InstagramImporter, SocialRecipeImporter, SaiCascade, ImportCrashGuard, SafeRegex, IcuRegexCompat.
- New `feature/Phase1LogicTest` (8 tests) covers: the quota (5/rolling week, encode/decode); the YT/IG gate (websites, TikTok, Pinterest and Facebook rejected; non-post IG profile still gets `NOT_AN_IG_POST`); share routing; the greeting fallback; IG thumbnail persistence being off; the provenance credit; Coco matching, its offline answer and the no-invention prompt.
- `DatabaseAndPdfTest.migrate1To2…` was already failing because the DB is v3. It is updated to run 1→2→3 and check `origin`/`sourceUrl`.
- `ui/UiTourScreenshots` (opt-in, `-Pscreens=1`) is rewritten for the five-tab shell on an empty install and passes. Screens are in `app/build/screens/`.

## What's left (not in this batch)
- **Play Billing / DesiKitchen Plus**: the quota is a local SharedPreferences stub, and the upsell is a dialog. It needs real entitlements and a server-side quota so it can't be reset by clearing data.
- **Curated collections content**: the catalogue is names only. It needs real recipes, artwork, a content pipeline and the unlock flow.
- **Full meal-plan AI**: Coco can add **one saved recipe at a time** to the plan or grocery list (see "Coco upgrades" below). It can't do multi-recipe "plan my week" yet.
- **Auth / backend / sync**: the app is local-only. Accounts, cloud backup and multi-device sync are still to come.
- **Coco extras**: there's no voice yet. History and streaming are done (see below).
- **Import**: YT/IG extraction quality is limited to public captions and descriptions. Transcript/ASR fallback and a server-side fetcher (for Meta rate limits) are later work. Hidden importers (web/TikTok/Pinterest) can be re-enabled behind a flag. OCR was removed in 1.2.0 to keep the APK small.
- **Clean-up**: `RecipesPane.kt` / `AllRecipesScreen`, `CookbooksScreen.kt` are now unreachable (the Paste/Scan screens were deleted in 1.2.0) but kept so they compile. Delete them once Phase 1 is signed off.
- **Visual polish**: the new screens follow v1 tokens, but there's no pixel pass against the mocks yet.

## 1.2.0 rebuild (29 Oct 2026, IST)
- Background import with a status pill (cancel / Review / Open draft / Already saved), via `BackgroundImporter` + `BackgroundImporterTest`.
- Paste screen cut down to field + Import + quota line. Demo/seed content is removed.
- Review import: one line per ingredient, numbered steps with no timers, soft cards. The Note overlap is fixed.
- Soft UI pass (`Soft.kt`, warm canvas, rounded shadows). ML Kit is removed and the build is arm64-only, so the APK went from 64 MB to 19.8 MB.
- 103 tests, 0 failures, 3 skipped.

## Coco upgrades (29 Oct 2026, IST)

| Item | Status / where |
|---|---|
| Persisted chat history | ✅ New Room entity `CocoTurn` (table `coco_turns`), `CocoChatDao` and `CocoChatRepository`, with `AppContainer.cocoChat`. **DB v3 → v4**: `MIGRATION_3_4` only creates `coco_turns`; existing tables are untouched (`schemas/.../4.json`). The thread is restored when the Assistant opens. **New chat** deletes all turns, and the delete is persisted. There's no limit or quota. `recipeIds` is a plain CSV with no FK, so turns survive a recipe delete, and chips show only recipes that still exist. |
| Streaming replies | ✅ `OpenRouterClient.chatStream` (SSE, `stream:true`, `[DONE]`; mid-stream errors throw) and `LibraryAssistant.askStreaming`. If streaming fails it makes one non-streamed request, then falls back to the offline matcher. The never-invent system prompt is unchanged, with one added line saying Coco can't edit the plan itself. Streaming is cancelled on New chat and when the ViewModel is cleared, and a generation counter guards stale jobs. The offline path is still non-streamed. The key still comes only from BuildConfig. |
| Meal plan / grocery from Coco | ✅ `util/CocoActions.kt` is a deterministic local intent parser with no model in the loop. It needs an add/put/plan verb plus a grocery or plan/day/meal word, and it resolves **only saved recipes** (an exact title wins, otherwise keyword candidates). It asks when the request is ambiguous: which recipe (chips), which day or meal (chips), or a past day. It always asks for **Confirm / Cancel** before writing. `CocoActionExecutor` re-reads the recipe and writes through `MealPlanRepository.add` (using the saved servings) or `GroceryRepository.addRecipe` (using the saved quantities only). The draft is stored on the turn, so a pending question survives a restart. |

Tests: new `feature/CocoUpgradesTest` (8 tests). They cover history persist, reopen and clear; the 3→4 migration; the SSE client contract; streaming partials, fallback and cancellation; non-action questions; library-only proposals; ambiguity follow-ups; and the executor writing only stored data and refusing missing recipes. `DatabaseAndPdfTest` now migrates 1→4. **117 tests, 0 failures, 3 skipped**; `compileDebugKotlin` and `assembleDebug` ✅.
