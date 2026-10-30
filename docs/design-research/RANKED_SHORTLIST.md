# Ranked shortlist: Compose patterns for DesiKitchen

Updated 2026-10-29 (IST). Research only: nothing here is implemented. Soft.kt changes wait for Sai's OK, and Theme.kt is not touched. Each pattern builds on DK UI's existing conventions (`SoftCard`, `SoftField`, `PillButton`, `SoftOutlinePill`, `SoftChip`, `NumberBadge`, `SoftSheet`, with SoftSage / SoftSageFill already live in Theme.kt) rather than forking them.

Ranking weighs: (1) it serves a locked flow (paste a link → background import → review → clean detail → cook), (2) it is backed by shipped mobile apps, (3) it maps onto an existing primitive with little new code.

---

## 1. Paste-link strip as the Discover hero
- **Sources:** ReciMe import flow (public Mobbin flow index, help docs); CTA.gallery *Form* type (one input + one action); appshot.gallery recipe-importer peers (Wecipe, Pestle, Mr. Cook) that lead their store frames with the import step.
- **Why it ranks:** Add means pasting a YouTube or Instagram link, so the most-used action should be the first thing on Discover.
- **Screens:** Discover (above Recently saved).
- **Compose:** keep `PasteLinkCard` = `SoftCard` › `SoftField` (placeholder "Paste a YouTube or Instagram link", trailing `ContentPaste` `IconButton` reading `ClipboardManager`) › one `PillButton("Import")`. Under it, one `bodySmall` line in `Muted`: "3 of 5 imports left this week" from the backend rolling-week count (never hardcoded). Detect a YT/IG URL on the clipboard and offer it as a `SoftOutlinePill` "Paste link from clipboard".
- **Do not copy:** camera/OCR, paste-text or website import (YT + IG only); multiple CTAs; illustrated hero banners.

## 2. Background import status pill
- **Sources:** Component Gallery *Toast / Snackbar* (41 examples, mobile included); spec 1.2.0 (status pill with Cancel, then Review).
- **Why:** import runs in the background, so the user needs ambient status that never blocks browsing.
- **Screens:** global chrome in `DesiKitchenRoot.kt`, floating above the NavigationBar.
- **Compose:** `ImportStatusPill` = `Surface(shape = RoundedCornerShape(50), color = SoftSageFill, shadowElevation = 6.dp)` with label ("Importing from YouTube…"), a 2dp `LinearProgressIndicator` (indeterminate while extracting), and a `TextButton("Cancel")`. When ready, `AnimatedContent` swaps to "Recipe ready" + `PillButton`-small "Review". `AnimatedVisibility(enter = fadeIn() + slideInVertically { it })`. Announce changes with `Modifier.semantics { liveRegion = LiveRegionMode.Polite }`.
- **Do not copy:** ReciMe confetti; a blocking modal spinner.

## 3. Hero image + SoftSheet clean detail
- **Sources:** Mobbin *recipe-detail* index (Blue Apron and NYT Cooking bottom-sheet detail); ReciMe Chicken Curry reference in `mocks/recime-match/NOTES.md`.
- **Why:** the strongest shipped pattern for recipe detail, and it suits the clean lock: no stats row is needed.
- **Screens:** Recipe detail.
- **Compose:** full-bleed hero (`AsyncImage`, 4:3) with `SoftSheet` (30dp top corners) overlapping by 24dp. Sheet shows title (`headlineSmall`), creator credit + source `SoftOutlinePill` (opens the YT/IG link), then content. The sticky bar keeps one `PillButton("Start cooking")`.
- **Do not copy:** time / servings / nutrition stats row, ingredient checkboxes, related recipes (Phase 1 lock).

## 4. Cook mode: one step per screen with n / N progress
- **Sources:** ReciMe cook mode (ScreensDesign summary + Pasta reference: "3 / 6", segmented bar, Next pill, Prev outline); Mobbin Kitchen Stories and Tasty *Progress Indicator* steps; Paprika (keeps screen on, highlights current step); Component Gallery *Progress indicator*.
- **Why:** cooking is hands-busy; one large step reduces lost place.
- **Screens:** Cook mode.
- **Compose:** top row of N `LinearProgressIndicator` segments (filled `SoftSage`, track `SoftSageFill`) + `Text("3 / 6")`. Step body: `NumberBadge(3)` + `headlineSmall` text. Bottom: `SoftOutlinePill("Previous")` and `PillButton("Next")`; last step says "Done". `HorizontalPager` for swipe. `view.keepScreenOn = true` via `DisposableEffect`.
- **Do not copy:** auto timers or invented durations (never invent recipe content); confetti at the end.

## 5. Five-tab NavigationBar with labels and one soft indicator
- **Sources:** Material 3 Navigation Bar guidance (3 to 5 destinations, labels, filled icon when active, FAB never covers the bar, reselect scrolls to top); Mobbin tab-bar index.
- **Why:** the tab set is locked (Collections · Discover · Assistant · Saved · Profile); the polish is in chrome.
- **Screens:** tab chrome.
- **Compose:** Material3 `NavigationBar(containerColor = Color.White, tonalElevation = 0.dp)` with a top `Hairline` divider; `NavigationBarItemDefaults.colors(indicatorColor = SoftSageFill, selectedIconColor = Ink, selectedTextColor = Ink, unselectedIconColor = Muted)`; outlined icons inactive, filled active; reselect → `listState.animateScrollToItem(0)`.
- **Do not copy:** a centre "+" tab (Add is not a tab); glass or blurred bars; hiding labels.

## 6. Empty states with one action
- **Sources:** Component Gallery *Empty state* (16; Atlassian tone notes); Mobbin *Empty State* (Pinterest Bookmarks & Collections).
- **Why:** no demo seed data, so first-run Saved and Collections are empty by design.
- **Screens:** Saved, Collections, Assistant first open.
- **Compose:** `EmptyState(title, body, actionLabel, onAction)` = centred `Column`, `titleMedium` Ink + `bodyMedium` Muted (≤ 2 lines) + one `PillButton` ("Paste a link" → Discover; "New collection"). Optional illustration only with Sai's OK (Kitbitz, CC0).
- **Do not copy:** emoji, cheerful greetings, multiple suggestions cards.

## 7. Ingredients | Steps segmented switch
- **Sources:** Component Gallery *Segmented control* (28); Mobbin NYT Cooking and Blue Apron detail use tabs inside the sheet.
- **Why:** long recipes scroll less, and the switch keeps the detail clean.
- **Screens:** Recipe detail, Import/Review.
- **Compose:** two `SoftChip`s in a `Row` (selected = SoftSageFill + SoftSageDeep) or `SingleChoiceSegmentedButtonRow` restyled; state in `rememberSaveable`. Ingredients as single lines (no checkboxes); Steps numbered with `NumberBadge`.
- **Do not copy:** a servings stepper next to the switch.

## 8. Import Review as grouped SoftCards with honest gaps
- **Sources:** spec 1.2.0 (single-line ingredients, numbered steps); ReciMe review step; TypeUI rule that components must specify empty and error states.
- **Why:** imports can miss data; the app must not invent it.
- **Screens:** Import/Review (`AddEditRecipeScreen` review mode; Grok Bot owns `ImportScreens.kt`).
- **Compose:** stacked `SoftCard`s: Title, Ingredients, Steps, Source. Missing section → `bodyMedium` Muted "Not found in the video caption" + `SoftOutlinePill("Add manually")`. One `PillButton("Save recipe")` in a sticky bar.
- **Do not copy:** auto-filled guesses, nutrition, aisle grouping.

## 9. Coco chat header and suggested-prompt chips
- **Sources:** Ask ReciMe help doc (recipe-scoped prompts: substitute an ingredient, make it simpler, understand a step); Fluid Functionalism *ChatMessage*, *InputMessage*, *AskUserQuestions*.
- **Why:** Coco is free and unlimited; starter prompts cut blank-page friction.
- **Screens:** Assistant / Coco; entry from detail.
- **Compose:** `TopAppBar` with a 28dp Coco avatar + "Coco" (`titleMedium`); recipe context as a `SoftOutlinePill` ("About: Chicken curry"). Prompt chips in `FlowRow` of `SoftChip`. User bubbles `SoftSageFill` + Ink; Coco bubbles white with `Hairline` border, 20dp radius. Input = `SoftField` + send `IconButton`. Label suggestions as Coco's, never as recipe content.
- **Do not copy:** Plus-gating the assistant; emoji avatars; streaming text effects.

## 10. Collections grid
- **Sources:** ReciMe cookbooks (New Cookbook → title → Create); Mobbin Pinterest collections.
- **Screens:** Collections.
- **Compose:** `LazyVerticalGrid(GridCells.Fixed(2))` of `SoftCard(radius = 24.dp)` tiles: 2×2 cover mosaic from member recipe images (or a SoftSagePale block when empty), name, count. "New collection" as the screen's single `PillButton`. If a limit applies, a small lock `Icon` on the tile opens the paywall sheet.
- **Do not copy:** meal plan or grocery tabs inside Collections (not Phase 1 tabs).

## 11. Soft paywall sheet only at the limit
- **Sources:** Pricing Pages; CTA.gallery *Pricing / Subscription*; ScreensDesign ReciMe summary (contrast: ReciMe runs a long quiz onboarding then a trial paywall).
- **Screens:** Profile/paywall; triggered when the 6th import of the rolling week is attempted.
- **Compose:** `ModalBottomSheet` (SoftSheet look): one line "You've used 5 imports this week", two plan `SoftCard`s (₹69/month, ₹699/year) with selected = SoftSageFill + 1.5dp SoftSage border, one `PillButton("Get Plus")`, `TextButton("Not now")`, and when the quota resets.
- **Do not copy:** onboarding hard paywall, quiz funnels, countdown timers, fake strike-through prices.

## 12. Touch micro-interactions
- **Sources:** MicroKit (54 micro-interactions), Kinetics (spring examples), Motion-Primitives; principles only.
- **Screens:** PillButton, SoftCard taps, save heart, tab indicator.
- **Compose:** `interactionSource.collectIsPressedAsState()` → `animateFloatAsState(if (pressed) 0.97f else 1f, spring(stiffness = Spring.StiffnessMediumLow))` in `graphicsLayer { scaleX = s; scaleY = s }`; heart toggle `spring(dampingRatio = 0.5f)`; skip when animator scale is 0.
- **Do not copy:** hover effects, looping decorative motion, CSS or JS.

---

## Optional

### 13. A DesiKitchen DESIGN.md that mirrors Theme.kt (docs only)
- **Sources:** designmd.ai, Refero Styles (color roles per surface, "accent only for product moments"), TypeUI blueprint (must/should rules, QA checklist), designmd.supply section list.
- **Why:** several agents build DesiKitchen UI; one read-only rules file keeps them on SoftSage roles. It documents what is live; it does not change Theme.kt.

### 14. Search entry pill on Discover
- **Sources:** Navbar Gallery *Search Bar* type; Mobbin Lifesum recipe home (tab bar + search bar).
- **Compose:** a `Surface(shape = RoundedCornerShape(50), color = Chip)` row with search icon + "Search your recipes" that navigates to `SearchScreen`; placed under the paste strip so it is never the primary action.
