# DesiKitchen: gap report against the master spec

Snapshot: 29 Oct 2026, IST. Codebase is 1.1.5 (versionCode 9). Spec: `docs/MASTER_PRODUCT_SPEC.md` (binding summary; the full text is in chat turn t99u).
Source root used for paths below: `app/src/main/java/com/desikitchen/app/` (written as `…/`).
This is an inventory only. No code was changed.

## 0. What the app is today (map)
- **Architecture:** single-activity Compose app, manual DI (`…/AppContainer.kt`), Room v2 (`…/data/db/DesiKitchenDatabase.kt`, `Daos.kt`), SharedPreferences settings (`…/data/repo/SettingsRepository.kt`). There is no backend, no account, and everything is on the device.
- **Nav** (`…/ui/navigation/DesiKitchenRoot.kt`): 4 tabs, **Home · Favorites · Grocery · Profile(MoreScreen)**, plus a hamburger drawer (Add a recipe, Cookbooks, Meal plan, All recipes, Settings) and an `AddRecipeSheet`. Routes include `recipe/{id}`, `cooking/{id}`, `edit?id&draft`, `plan`, `grocery`, `cookbooks`, `cookbook/{id}`, `recipes`, `import/paste`, `import/camera`, `import/web?url`.
- **Screens:** `ui/home/HomeScreen.kt` (greeting, search bar, "Popular Recipes", emoji categories), `ui/home/FavoritesScreen.kt`, `ui/home/RecipesPane.kt` (AllRecipes), `ui/cookbooks/*` (emoji grid of cookbooks), `ui/detail/RecipeDetailScreen.kt` (863 lines: stats row, checkboxes, timers, cook mode, PDF), `ui/detail/AssistantSheets.kt` (per-recipe "Ask DesiKitchen" sheet + NutritionSheet), `ui/cooking/CookingScreen.kt`, `ui/edit/AddEditRecipeScreen.kt` (review/edit), `ui/importer/ImportScreens.kt` (Paste + Web), `ui/importer/ScanImportScreen.kt` (OCR), `ui/plan/PlanScreen.kt`, `ui/grocery/GroceryScreen.kt`, `ui/more/MoreScreen.kt`.
- **Data model** (`…/data/model/Entities.kt`): `Recipe` (title, description, cuisine, mealType, dietTags, prep/cookMinutes, `servings = 4` default, **`emoji = "🍛"`**, imageUrl, isFavorite, isUserCreated, cookedCount), `Ingredient` (name, quantity Double?, unit, aisle, note), `Step` (text, timerMinutes), `Cookbook` (name, emoji), `CookbookRecipeCrossRef`, `MealPlanEntry` (epochDay, slot, servings), `GroceryItem`. **No sourceUrl, platform, creator, origin, extraction status, or servings provenance columns.**
- **Import pipeline:** `util/WebRecipeFetcher.kt` dispatches to `util/InstagramImporter.kt` (IG cascade: HTML/embed → 1 ScrapeCreators `/instagram/post` → cheap OpenRouter on comments → `StillsRecipeExtractor` keyframes+audio) or `util/SocialRecipeImporter.kt` (TikTok/YouTube/Pinterest/Facebook) or the generic JSON-LD/HTML site parser `util/RecipeHtmlParser.kt`. Also `CaptionRecipeParser`, `RecipeTextParser`, `OcrEngine`, `VideoRecipeExtractor`, `OpenRouterClient`, `CreatorsApiClient`, `SafeRegex` (ICU fix), `ImportErrors`, `ImportUrlValidator`. Hand-off to review uses `util/ImportDraft.kt` (in-memory, has `sourceUrl`, `warnings`, `partial`).
- **Seed data:** `…/data/seed/SeedData.kt` (361 lines of demo recipes) is inserted by `AppContainer.seedIfEmpty()` along with 3 demo cookbooks ("Weeknight Dinners", "Festive & Sunday Specials", "South Indian Tiffin"). `SettingsRepository` defaults the display name to **"Sai"**.
- **Nutrition:** `util/NutritionEstimator.kt` is a keyword table per 100 g that assumes `recipe.servings` (default 4). It is shown in the detail `StatsRow` ("Calories") and `NutritionSheet`.
- **Subscription/paywall:** none. `MoreScreen.kt:186` says "No subscriptions, no paywalls, no ads."
- **Share intents** (`AndroidManifest.xml`): SEND text/plain, markdown, uri-list, images (to OCR), SEND_MULTIPLE, and VIEW for text files. `MainActivity` sends them to `SharedPayload` and then `DesiKitchenRoot` (URL → web import, else paste, images → scan). It accepts **any** link and any text.
- **Theme** (`ui/theme/Theme.kt`): mustard `#F6A823` / sage `#47B977` v1 palette, Plus Jakarta Sans, `DarkColors` + `isSystemInDarkTheme()`. `res/values/colors.xml` has saffron `#E07A10` / cream window background.

## 1. Present (keep)
| Spec item | Where |
|---|---|
| IG extraction cascade, never invent, FULL only with real ingredients + steps, keys from git-ignored `local.properties` | `util/InstagramImporter.kt`, `StillsRecipeExtractor.kt`, `OpenRouterClient.kt`, `CreatorsApiClient.kt`, `app/build.gradle.kts` `secret()`; tests in `app/src/test/.../scraper/*` |
| YouTube import (oEmbed + description → caption parser → AI, no transcripts) | `util/SocialRecipeImporter.kt` (`Platform.YOUTUBE`) |
| URL normalisation and validation, IG post/reel check, crash-safe | `util/ImportUrlValidator.kt`, `WebRecipeFetcher.validate()`, `ImportErrors.kt` |
| Review/edit before save (edit title, add/remove ingredient rows and steps, partial warnings) | `ui/edit/AddEditRecipeScreen.kt`, `AddEditRecipeViewModel.kt:94-126` |
| Paste-URL import screen | `ui/importer/ImportScreens.kt` `WebImportScreen` |
| Android share sheet → import, cold start and running app (`singleTop`) | `AndroidManifest.xml`, `MainActivity.kt`, `DesiKitchenRoot.kt` `LaunchedEffect(shared)` |
| Local persistence of recipes/ingredients/steps (structured, not a blob) | Room `Entities.kt`, `Daos.kt` |
| Personal collections: create, rename, delete (cascade deletes only the join rows, not the recipes), add/remove recipe | `CookbookDao` (`rename`, `delete`, `addRecipe`, `removeRecipe`), `ui/cookbooks/*` |
| Ingredient-aware search ("paneer peas" finds Matar Paneer) | `util/RecipeSearch.kt`, `HomeViewModel.kt`, `RecipesPane.kt` |
| Delete recipe, edit saved recipe, share as text | `RecipeDetailScreen.kt` menu (lines 303-314) |
| Per-recipe AI Q&A with offline fallback (start of the assistant) | `util/RecipeAiAssistant.kt`, `RecipeAssistant.kt`, `ui/detail/AssistantSheets.kt` |
| App name "DesiKitchen", no "Ask Coco" anywhere in code or resources | `res/values/strings.xml`, grep for "coco" finds only coconut |
| Meal plan (weekly, manual) and grocery list | `ui/plan/*`, `ui/grocery/*` (Phase 3 base; grocery is not in the spec) |

## 2. Partial
| Spec item | Gap | Where |
|---|---|---|
| Import sources YT + IG only | Fetcher also accepts TikTok, Pinterest, Facebook and any recipe website (JSON-LD), plus Paste text, Camera OCR, and Write-it-yourself. The drawer and AddSheet say "Recipe sites and Instagram". The spec says to reject anything that is not YT/IG. | `WebRecipeFetcher.fetchUnsafe` l.73-74, `SocialRecipeImporter.Platform`, `RecipeHtmlParser.kt`, `DesiKitchenRoot.kt` `AddRecipeSheet`/drawer |
| Share sheet validates YT/IG | It accepts any URL or text and routes non-URL text to Paste. There is no "unsupported source" error, and image shares go to OCR. | `DesiKitchenRoot.kt` l.~150-170, manifest image/VIEW filters |
| Extracting state with honest stages + cancel | `WebImportScreen` shows a spinner with no stage list. There is no cancel action and no timeout UI. | `ImportScreens.kt:136-204` |
| Failure/recovery matrix (private, deleted, no recipe, timeout, duplicate, quota, save failure) | Failure carries a message plus a "draft" fallback. Duplicate and quota states don't exist. | `ImportScreens.kt`, `ImportErrors.kt` |
| Source link + creator credit | `sourceUrl` only exists in `ImportDraft` and is written into `description` as "Imported from …". Creator credit is parsed from the first description line (`RecipeDetailScreen.kt:595 sourceLine`). There is no clickable "Open original" and no persisted platform or creator. | `AddEditRecipeViewModel.kt:100`, `Entities.kt` |
| Saved library | Split across Favorites tab (hearted), All recipes (drawer) and Cookbooks (drawer, emoji grid). It is not one Saved tab, and collections are not text-first with counts. | `FavoritesScreen.kt`, `RecipesPane.kt`, `CookbooksScreen.kt` (`LazyVerticalGrid`, emoji tiles) |
| Move recipe between collections | Possible as add + remove via "Save to cookbooks" multi-select. There is no explicit move. | `RecipeDetailScreen.kt` cookbooks sheet |
| Search | It is the home search bar plus All recipes. There is no dedicated Search Results screen with loading/error states, and no natural-language constraints ("without tomatoes"). | `HomeScreen.kt` SearchBar, `RecipeSearch.kt` |
| Profile | It has a name (default "Sai"), units, screen-on, grocery sync, week start and version. There is no account, plan status, sign-out or delete account, and no privacy/terms links. | `MoreScreen.kt`, `SettingsRepository.kt:29` |
| Home greeting | It says "Good Morning, Sai ☀️ / What would you like to cook today?", not "What's the plan, [First Name]?". The import box only appears when a URL is typed into search, and there is no persistent import card above Recently Saved. | `HomeScreen.kt:116-160` |
| Nutrition | It exists but is not provenance-labelled, uses the `servings=4` default as if it were fact, and appears on recipe detail, which the spec forbids. | `NutritionEstimator.kt`, `RecipeDetailScreen.kt:610-640`, `AssistantSheets.kt:139` |
| Image handling (Meta-safe) | IG covers are downloaded and persisted to `filesDir/import-images`. The spec prefers not persisting thumbs. | `util/ImportImageStore.kt`, `InstagramImporter.kt:238` |
| Recipe data model | It is structured but missing spec fields: sourceUrl, sourcePlatform, sourceVideoId, creatorHandle, origin (imported/manual/AI), extractionStatus/confidence, sourceServings vs userServings vs estimatedServings (currently a non-null `servings` default of 4), ingredient `originalText`/uncertain flag, step temperature, updatedAt, ownerId. | `Entities.kt` |

## 3. Missing (Phase 1)
1. **Auth/account.** There is no sign-up/sign-in, user id or session handling, and no backend. Everything is local, and owner scoping does not exist. Decision needed (backend choice: Firebase Auth/Supabase/custom).
2. **Five-tab nav** Collections · Discover · Assistant · Saved · Profile with an emphasised centre Assistant. Today it is 4 tabs (Home/Favorites/Grocery/Profile) plus a drawer. `DesiKitchenRoot.kt` `tabs`, `Routes.topLevel`.
3. **Assistant tab (Coco), free and unlimited, global chat** over the saved library with tappable recipe cards, follow-ups, and retry/empty/error states. Only the per-recipe sheet exists. Needs `ui/assistant/*` and a library-wide prompt in `RecipeAiAssistant`.
4. **Discover home:** the "What's the plan, [First Name]?" header, a persistent paste-link import card **above** Recently Saved, Recently Saved horizontal cards (image or placeholder plus title only), a curated-collections row, and entries to search and the assistant. Remove "Popular Recipes", ratings/time pills and emoji category bubbles.
5. **Saved tab:** a single library with text-first personal collection names + counts, search inside Saved, and a useful empty state.
6. **Search Results screen** with initial/loading/results/no results/error states.
7. **Recipe detail per spec:** title, image/placeholder, ingredients, instructions, source attribution + an "Open original" link, and actions (edit, save/unsave, add to collection, share, back). **Remove:** `StatsRow` (servings/time/calories/diet), ingredient `Checkbox`es (l.408), the pantry counter, the timer chips/`KitchenTimers`, "I cooked this", grocery bottom bar, and nutrition menu entry. Bottom nav is already hidden on detail (good). Cook mode, PDF and print are not in the spec, so keep them only if Sai wants them (see Conflicts).
8. **Persisted provenance:** Room migration v2→v3 adding sourceUrl, platform, creator, videoId, origin, extractionStatus, sourceServings (nullable), and dropping the emoji default. Store the creator credit and URL explicitly instead of in `description`.
9. **Duplicate detection** by normalised source URL / IG shortcode / YT video id, with an "Already saved, open it?" prompt.
10. **YT/IG-only gate** in the validator, share handler and UI copy, with an explicit "Unsupported link" error. Hide or remove the TikTok/Pinterest/Facebook/website, paste-text and camera import entry points from the import UI. Needs a decision before code is deleted, because the spec says keep working features.
11. **Extraction states:** honest stage list from the real cascade callbacks, a cancel action, a timeout, and the full failure matrix; plus a success screen ("Saved", open it, back).
12. **True empty new user:** stop `seedIfEmpty()` (`AppContainer.kt:~66`), remove the demo cookbooks, and remove the "Sai" default name (`SettingsRepository.kt:29`). The name should come from the account. Keep `SeedData.kt` for tests only.
13. **Design rules:** no emoji in the UI (about 90 emoji literals across 20 files, for example `Recipe.emoji`, `Cookbook.emoji`, `MealSlot.emoji`, home category bubbles, "🎉 I cooked this!", "✨ Ask DesiKitchen", the "👋" avatar fallback, and the `CookbooksScreen` emoji picker), no dark mode (`Theme.kt` `DarkColors` / `isSystemInDarkTheme`), no surface gradients (`Components.kt:100 artBrush` placeholders; the `GroceryScreen.kt:330` scrim gradient is borderline), no purple (none found).
14. **Brand tokens:** Espresso & Saffron palette, Nunito-style headings + Inter body, and a new DesiKitchen wordmark to replace `res/drawable-nodpi/desikitchen_wordmark.png` if it does not match the new brand. Fix `themes.xml` window bg to `#FFFFFF` and `colors.xml` saffron/cream.
15. **Coco avatar asset** (small, for the chat header) and an onboarding/welcome screen. There are no mascot assets in `res/`.
16. **Profile basics:** account info, edit profile, sign-out, delete account, privacy/terms. Remove the "No subscriptions, no paywalls" copy (`MoreScreen.kt:186`) and the `unlocked`/`notIncluded` lists.

## 4. Missing (Phase 2+)
- **Phase 2:** the curated collection catalogue (8 collections with truthful counts, no target counts shown as achieved); locked previews; the DesiKitchen Plus paywall (₹69/mo, ₹699/yr, contextual entry points only); a 10 imports/month free quota that the **backend** enforces, where failed imports don't count; Play Billing + server entitlement verification + restore; subscription status in Profile; and the 3-month collection-unlock promo, built only if the config enables it. **None of this exists.** There are no billing deps in `app/build.gradle.kts`.
- **Phase 3:** daily and monthly plans plus auto-generation from saved recipes (today it's manual weekly add only: `PlanScreen.kt`, `PlanViewModel.kt`); replace or regenerate a meal; small-library and repetition messaging; a meal plan data model with period, preferences, status and provenance (today it's only `MealPlanEntry`); nutrition with provenance (measured vs DB vs AI) and missing-servings handling (the current `NutritionEstimator` is a keyword table and uses the default servings, so it must be relabelled or gated); dietary preferences + restrictions in Settings, used by the assistant and plans; health safeguards.
- **Phase 4:** async import jobs, idempotency and retries on a backend, analytics events, crash/error monitoring, and end-to-end tests. Today there are 79 JVM unit tests and Roborazzi screenshots (`UiTourScreenshots.kt`).
- **Security:** API keys are compiled into the APK (`BuildConfig` from `local.properties`, flagged in `STATUS.md`). The spec requires keys on the backend, so the scraper and AI calls must eventually move server-side.

## 5. Conflicts: ReciMe mustard v1 against Espresso & Saffron
| v1 (in code) | New spec | Location |
|---|---|---|
| Primary `Mustard #F6A823`, `MustardDeep #D98B0B`, `MustardCream/Pale` | Ember `#E4572E` as the primary accent, Ember Deep `#C13D14` for pressed, Saffron `#F5B544` as the warm accent | `Theme.kt` top vals and `LightColors`; `Mustard` is imported directly in `DesiKitchenRoot.kt`, `HomeScreen.kt` and others |
| Sage `#47B977` / `SageSoft #EAF6EE` / `SageDeep #2E8B57` | Pistachio `#2E5E4E` / Basil `#E7F0EA` | `Theme.kt`, detail checkboxes, grocery |
| `Ink #17171A`, `Muted #8E8E93`, `Hairline #ECECEF`, `Chip #F3F3F5`, cool greys | Espresso `#1C1815`, Ash `#948B80`, Oatline `#E5DED3`, Porcelain `#F7F4EF`, Linen `#EFEAE1` (warm neutrals) | `Theme.kt` |
| `Saffron = Mustard` alias, plus `Turmeric`, `Chilli`, `Cardamom`, `Masala` legacy aliases | The name `Saffron` now means `#F5B544`, so the alias **collides**. Rename or remove the legacy aliases first. | `Theme.kt:36-43` |
| Plus Jakarta Sans everywhere | Nunito-rounded headings + Inter body (need OFL fonts in `res/font`) | `Theme.kt` `Jakarta`, `res/font/plus_jakarta_sans.ttf` |
| Dark scheme auto-enabled | No dark mode | `Theme.kt:132` |
| Gradient art placeholders, emoji art | Flat placeholder, no gradient, no emoji | `Components.kt:88-100`, `Recipe.emoji` |
| Window bg `cream #FFF8EC`, launcher bg `#E07A10` | Page ground `#FFFFFF`. Launcher bg follows the new brand. | `res/values/colors.xml`, `themes.xml`, `mipmap/ic_launcher*.xml` |
| 4 tabs with Grocery, a hamburger drawer and a bell icon | 5 tabs, no drawer, Grocery is not in the IA | `DesiKitchenRoot.kt`, `HomeScreen.kt:105-114` |
| Detail: 2×2 stats, checkboxes, timer chips, grocery sticky bar, rating | All explicitly excluded | `RecipeDetailScreen.kt`, `mocks/v1/NOTES.md` |
| Home: "Popular Recipes", star rating/author, category bubbles | No fake popularity; Recently Saved + collections | `HomeScreen.kt:164-200`, `mocks/v1/home.png` |
| Profile copy "No subscriptions, no paywalls" | Plus exists | `MoreScreen.kt:186` |
| Kept extras not in spec: Grocery, Cook mode, kitchen timers, PDF/print, OCR scan, paste text, other platforms | The spec forbids a cooking timer "as a committed feature" and limits imports to YT/IG, while Sai also said "keep existing features". **Decision for Sai/parent:** hide these behind secondary menus or remove them. Recommendation: keep Grocery and Cook mode reachable from the recipe overflow menu, drop timers from detail, and disable non-YT/IG import UI while keeping the parser code for tests. | various |
| `mocks/v1` is the locked UI | Superseded by the master spec. Mark `mocks/v1` as historical in `README` / `FEATURE_PARITY.md` / `RECIME_PRODUCT_MAP.md`. | `mocks/v1/*` |

## 6. Recommended build order (smallest shippable Phase 1 increments)
1. **Tokens swap:** rewrite `Theme.kt` with the Espresso & Saffron tokens (rename the legacy aliases, delete `DarkColors`, force light), set `colors.xml`/`themes.xml` bg to `#FFFFFF`, and add Nunito + Inter fonts. Ship it, because nothing else changes visually except the colours.
2. **Design-rule sweep:** remove every UI emoji (drop the `Recipe.emoji`/`Cookbook.emoji` render paths and the `MealSlot` emoji), replace the `artBrush` gradient with a flat Porcelain placeholder plus a line icon, and remove the cookbook emoji picker.
3. **Empty new user:** remove the `seedIfEmpty()` call and demo cookbooks, and remove the "Sai" default name. Add a first-run name prompt until auth exists.
4. **Room v3 migration for provenance:** sourceUrl, sourcePlatform, sourceVideoId, creatorHandle, origin, extractionStatus, nullable sourceServings/userServings, updatedAt. Write them from `ImportDraft`/`AddEditRecipeViewModel` and stop putting "Imported from" into `description`.
5. **Five-tab shell:** Collections · Discover · Assistant (centre, emphasised) · Saved · Profile. Remove the drawer and bell, and move Grocery, Plan and Cook mode to secondary entries.
6. **Discover home:** "What's the plan, [First Name]?", a paste-link import card above Recently Saved (image/placeholder + title), a collections row (placeholder or locked until Phase 2), and search and Ask Coco entries.
7. **Saved tab:** a single library list plus text-first personal collections with counts, create/rename/delete/move, in-Saved search, and empty states. Merges Favorites, All recipes and Cookbooks.
8. **Recipe detail cleanup:** remove StatsRow, checkboxes, timers, nutrition, "I cooked this" and the grocery bar. Add source attribution + "Open original". Keep the actions: edit, save/unsave, add to collection, share, delete.
9. **YT/IG-only gate:** validator plus share handler, with an "Unsupported link" error. Hide the other-platform/website/paste/OCR entry points (keep the code paths under test).
10. **Import flow states:** honest stage list wired to the cascade stages, cancel + timeout, the full failure matrix, and an "It's ready" review → "Saved" success screen with Open.
11. **Duplicate detection** on normalised URL / shortcode / video id, with "Already saved, open it?".
12. **Search Results screen** with states. Add light constraint parsing ("without X", "quick") over title, ingredients and tags.
13. **Assistant tab (Coco):** library-wide chat via OpenRouter with a small avatar header, text-first, follow-ups, tappable recipe cards from saved recipes, labelled AI suggestions, retry/offline states, and no limit.
14. **Meta-safe images:** stop persisting IG thumbs (`ImportImageStore`) by default. Hotlink while valid, then fall back to a placeholder, or store only user-chosen photos.
15. **Profile v1:** name/edit, settings (units), privacy/terms links, and version, with no paywall copy. Leave sign-out and delete placeholders out until auth exists.
16. **Auth + backend (needs a stack decision):** sign-up/sign-in, user id, server-side import proxy (moves keys off the APK), and the quota counter. This is the prerequisite for Phase 2 quota and billing.

## 7. Open decisions to raise with Sai (only where truly unclear)
- Backend/auth stack (Firebase, Supabase or custom) and whether Phase 1 can ship local-only first.
- Keep, hide or remove the existing non-spec features: Grocery tab, Cook mode, timers, PDF/print, OCR scan, paste text, and TikTok/Pinterest/Facebook/website import.
- Coco mascot assets: are approved PNG/vector poses available? None are in the repo (`brand/` has only `desikitchen-logo.png`).
- Is the current `desikitchen_wordmark.png` / `brand/desikitchen-logo.png` the approved DesiKitchen wordmark, or does it need a redesign?
