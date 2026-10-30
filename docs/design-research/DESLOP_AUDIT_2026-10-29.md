# DesiKitchen — Deslop Audit (factual inventory)

**Date:** 2026-10-29 · **Auditor:** Grok Bot executor (for Deslop Designer master audit) · **Scope:** Android Compose app at `/workspace/DesiKitchen`
**Rule:** this is an inventory only. No app code was edited. Every claim cites a file I actually opened. Line numbers are from the Read tool, taken on 2026-10-29 at about 23:30 IST.

> **Freshness warning:** the screenshots in `app/build/screens/` and `soft-02-05/` were captured at 18:59–19:00 IST. `DiscoverScreen.kt` and `DesiKitchenRoot.kt` were modified later, at 19:45 IST, so the screenshots are **stale versus source**. For example, the screenshots show a black house-and-hat Coco mark in the Assistant tab, but `DesiKitchenRoot.kt` now uses `Icons.Filled.AutoAwesome`. Discover's `desikitchen_logo` image (DiscoverScreen.kt ~L115) does not appear in the screenshots either. Re-capture before the master audit relies on pixels.

---

## 0. Files opened

**Docs**
- `docs/MASTER_PRODUCT_SPEC.md`
- `docs/DK_UI_SOFT_PASS_PLAN.md`
- `docs/design-research/COMPOSE_ADAPTATIONS.md`
- `docs/design-research/RANKED_SHORTLIST.md`
- `docs/design-research/mocks/README.md`

**Theme and components**
- `ui/theme/Theme.kt`
- `ui/components/Soft.kt`
- `ui/components/Components.kt`
- `ui/components/Extras.kt`
- `ui/detail/StepText.kt` (grep only)

**Navigation**
- `ui/navigation/DesiKitchenRoot.kt`

**Screens**
- `ui/discover/DiscoverScreen.kt`
- `ui/importer/ImportScreens.kt`
- `ui/edit/AddEditRecipeScreen.kt`
- `ui/detail/RecipeDetailScreen.kt`
- `ui/cooking/CookingScreen.kt`
- `ui/assistant/AssistantSheets.kt`
- `ui/assistant/AssistantScreen.kt`
- `ui/saved/SavedScreen.kt`
- `ui/collections/CollectionsScreen.kt`
- `ui/more/MoreScreen.kt`
- `ui/search/SearchScreen.kt`
- `ui/plan/PlanScreen.kt`
- `ui/grocery/GroceryScreen.kt`

**Other source**
- `MainActivity.kt` (grep)
- `res/values*/themes.xml` (grep)
- `src/test/.../UiTourScreenshots.kt` (grep)

**Screenshots** (all 1179×2556, viewed downscaled)
- `app/build/screens/`: `01_discover_empty`, `02_collections`, `03_assistant`, `04_saved_empty`, `05_profile`, `06_paste_link`
- `app/build/screens/soft-02-05/`: `01_discover_soft_paste_hero_empty`, `02_import_status_pill_running`, `03_discover_soft_with_recent`, `04_import_status_pill_duplicate`, `05_detail_soft_sheet_ingredients`, `06_detail_soft_sheet_steps`, `07_cook_soft_one_step`, `08_cook_soft_step2_prev_next`

**Not opened / not viewed:** the mock PNGs (`mocks/01-05`; I read the README only), `SCORECARD.md`, `RECIME_UX_TAKEAWAYS.md`, and the legacy files `cookbooks/*` and `home/RecipesPane.kt`. The soft-pass plan L22 calls those legacy files dead.

---

## 1. Contradictions: docs vs code vs docs

| # | Claim A | Claim B | Verdict |
|---|---|---|---|
| C1 | `MASTER_PRODUCT_SPEC.md` L59-60: "keep mustard/green UI v1 … Do not migrate … or change fonts/palette without explicit approval". `DK_UI_SOFT_PASS_PLAN.md` L4: "Theme.kt: locked (mustard #F6A823)", plus L18 and L26-29 (mustard current step / mustard pill). | `Theme.kt` L20-31 is a **sage** palette (SoftSage `#5BAF8A`) with the comment "Sai OK 2026-10-29". | **Unresolved palette lock.** Two docs say mustard is locked; the code says sage. One of them must be rewritten. |
| C2 | `MASTER_PRODUCT_SPEC.md` L9 says Espresso & Saffron is the palette. | L59-60 overrides it to mustard/green; the code is sage. | Three palettes are named in two docs. |
| C3 | `COMPOSE_ADAPTATIONS.md` L5 and L18: PillButton has "white text" (and notes it is 2.64:1). | `Soft.kt` L127: PillButton content is **Ink**. `mocks/README.md` says Ink on SoftSage is "already shipping". | The doc is stale. The code is correct (Ink is 6.77:1). |
| C4 | `RANKED_SHORTLIST.md` #5: nav selected = **Ink**, indicator SoftSageFill. | `DesiKitchenRoot.kt` L430: selected tint **SoftSageDeep**; labels SoftSageDeep or Muted. | The code diverges from the shortlist, and both label colours fail AA at 11sp. |
| C5 | `COMPOSE_ADAPTATIONS.md` §7 L31: sticky bar has an "Add to grocery" strip "(as built today)". | `RecipeDetailScreen.kt` ~L328 comment: the action stays in the ⋯ menu. The sticky bar has only "Start cooking". | The doc is false about the build. |
| C6 | `MASTER_PRODUCT_SPEC.md` L11: **no dark mode**. | `Theme.kt` L78-100 has DarkColors. L193: `darkTheme = isSystemInDarkTheme()`. `MainActivity.kt` L26 calls `DesiKitchenTheme {}` with the default. | **Dark mode ships** whenever the phone is dark. About 28 hard-coded `Color.White`/hex colours in `ui/` (excluding theme and legacy) would then render white blocks on a dark surface. Rule violation plus a rendering bug. |
| C7 | `MASTER_PRODUCT_SPEC.md` L7: no "Ask Coco" branding. | `AssistantSheets.kt` L83 sheet title "Ask Coco". `AssistantScreen.kt` L176 placeholder "Ask Coco about your recipes". Detail ⋯ menu item "Ask Coco". `CookingScreen.kt` L172 contentDescription "Ask DesiKitchen". | Three names for one assistant, and one of them is banned. |
| C8 | `MASTER_PRODUCT_SPEC.md` L24: 10 imports/month. | Spec L65 "as built": 5 per rolling week. `MoreScreen.kt` L186: "Free plan · 5 imports a week". Discover screenshot: "5 of 5 imports left this week". | The spec contradicts itself. |
| C9 | Spec hard rule: no emoji in UI. `RANKED_SHORTLIST.md` #6: EmptyState without emoji. | Emoji ship in UI. See the list after this table. | Rule violated in **6 files**. |
| C10 | Spec / `COMPOSE_ADAPTATIONS.md` L53, L59: no UI gradients; leftover `#F6A823` gradient flagged. | `Components.kt` L91-103 still has mustard/peach `Brush.linearGradient` art palettes. `GroceryScreen.kt` L329 has a vertical black scrim gradient. | Visible in every screenshot with a recipe (05–08 and 03): **mustard gradient inside a "sage" app**. |
| C11 | Spec detail exclusions: no nutrition. | `AssistantSheets.kt` L136-180: `NutritionSheet` still exists and appears unused. | Dead surface that violates a lock. Delete it. |
| C12 | `DesiKitchenRoot.kt` L142-149 comment: "Assistant (centre, emphasised)". | Nothing in BottomBar L419-453 emphasises it. In the screenshots, the emphasis came only from a heavy black logo glyph, which current code replaced with a generic sparkle. | Comment lies. |

**C9 emoji inventory:**
- `Components.kt` L128 🍛 fallback
- `Components.kt` L243-245 EmptyState `emoji` param
- `Components.kt` L331 and L380 picker leading emoji
- `DiscoverScreen.kt` L136 🍲
- `CookingScreen.kt` L121 ⏰ and L176 👨‍🍳
- `GroceryScreen.kt` L102-107 store emoji, L160 🛒, L229 🏠, L302 ✓

**Additional naming drift.** `Theme.kt` L36-48 aliases `Mustard`, `MustardDeep`, `Saffron`, `Turmeric`, `DeepGreen` and `Cardamom` to sage, and `Masala` to Ink. Seven screen files still import `Mustard*`: Saved, Search, Assistant, More, AddEdit, Grocery and Plan. Thirty non-theme references in total. Anyone reading `tint = Mustard` will believe the UI is yellow.

---

## 2. Global system issues

1. **Radius soup.** Thirteen distinct corner radii are hard-coded in `ui/`: 2, 6, 8, 10, 14, 16, 18, 20, 22, 24, 26, 28 and 32 dp, plus 50% pills. That count comes from a grep over `RoundedCornerShape(n.dp)` / `radius = n.dp`. `Theme.kt` Shapes defines 10/14/20/28/32, and the screens ignore it. There is no token layer.
2. **Everything is a shadowed card.**
   - SoftCard defaults to radius 28 and elevation 8 (`Soft.kt` L52-68).
   - RecipeListRow puts a card with shadow 8 on **every list row** (`Components.kt` L181-211).
   - Saved, Collections and Profile wrap each row group in a 24/6 card.
   - AddEdit review stacks three or more 28/8 cards.

   With shadows everywhere, elevation carries no meaning.
3. **Shadows probably clipped.** `.clip(...)` is applied before SoftCard's internal `.shadow` (`DiscoverScreen.kt` L152, L242). In the screenshots, cards render with no visible elevation at all (this may also be a Robolectric limitation). Either way, the "soft" look shown in the screenshots is flat white on white.
4. **Typography weight inflation.**
   - Nunito ExtraBold is used for nearly every heading.
   - `DkTopBar` is titleLarge ExtraBold (`Extras.kt` L29).
   - Section titles are titleLarge ExtraBold (Saved L194, More L227, Search L99/L132).
   - EmptyState is ExtraBold titleLarge (`Components.kt` L230-264).

   When everything shouts, there is no hierarchy. The scale is also inverted: headlineLarge 34 is bigger than displaySmall 32 (`Theme.kt`). `Jakarta = Nunito` (L119) is a lying alias.
5. **A fourth and fifth hue sneak in.** `StepText.kt` L17-18 has `IngredientBlue #1565C0` and `TempOrange #E65100`. The cook screenshots (07, 08) show **blue** ingredient links in a sage/mustard app. Chilli `#D9534F` is the tertiary colour (`Theme.kt` L46).
6. **Contrast failures baked into tokens.**
   - Muted `#8E8E93` on white is 3.26.
   - SoftSageDeep on white is 3.92.
   - SoftSageDeep on SoftSageFill is 3.53.

   These are used for body-size text: quota lines, nav labels, the "See all" link, chip-selected labels, NumberBadge, the review count line, the ingredient quantity column, and "In <collection>" links. A white check on SoftSage (2.64) is used in `DesiKitchenRoot.kt` L398-404 and `GroceryScreen.kt` L353.
7. **Half-migrated component set.** The Soft primitives exist, but **17 stock `AlertDialog`s** remain: Components 3, Plan 4, Detail 4, Cooking 2, Saved 1, Grocery 1, Collections 1, AddEdit 1. Material components also remain on these surfaces:

   | Surface | Material components still used |
   |---|---|
   | Plan | `Card`, `FilterChip`, `ExtendedFloatingActionButton` |
   | Grocery | `OutlinedTextField`, `SegmentedButton`, `FilterChip`, `OutlinedButton`, `ModalBottomSheet` |
   | Assistant sheets | `Card`, `OutlinedTextField` |
   | Profile | `ListItem`, M3 `Switch` |
   | AddEdit | `FilterChip` |

   The app looks like two products stitched together.
8. **`SoftOutlinePill` misnomer.** It is a *filled* SoftSageFill pill with an optional border (`Soft.kt` L189). It gets used as a secondary button, a tertiary button and an empty-state CTA interchangeably.
9. **Accessibility gaps.**
   - Bottom-bar icons have `contentDescription = null` and no tab role or selected semantics.
   - The import status pill has no liveRegion.
   - The bottom-bar labels at 11sp fail contrast.

---

## 3. Per-screen findings

### 3.1 Root / bottom bar / import status pill — `DesiKitchenRoot.kt`

**Tabs and bottom bar**
- **Tabs L142-149.** The order Collections · Discover · Assistant · Saved · Profile matches the lock. The Assistant icon is `AutoAwesome`, the generic "AI sparkle" every 2024 app uses. It has zero brand, and the comment promising emphasis is false (C12).
- **Duplicate routing L236-237.** Saved and Profile both route "Cookbooks" and "All recipes" to SAVED. Profile therefore has two rows that land on the same screen the Saved tab already shows.
- **BottomBar L419-453.** It is hand-rolled, not M3 `NavigationBar`.
  - Selected uses SoftSageDeep, against the shortlist's Ink (C4).
  - labelSmall 11sp in SoftSageDeep (3.92) or Muted (3.26) fails AA.
  - Icons have null descriptions.
  - The 56×32 SoftSageFill indicator is fine.

**ImportStatusPill L306-385**
- **Two primaries.** The Ready state puts a solid-sage PillButton "Open it" plus a dismiss X inside the pill. That is a second solid-sage primary floating over Discover's "Import" primary. Screenshot `soft-02-05/04` shows two identical green pills on one screen.
- **Truncated copy.** The Ready copy truncates badly: `Already saved: "Weekn…` (screenshot 04).
- **Overlaps content.** It sits bottom-centre in a Box with no content-padding reservation. In screenshot `soft-02-05/02` it covers the Collections tiles. Discover pads 96dp; other tabs do not.
- **Inconsistent badges.**
  - The Ready StatusBadge (L398-404) is a white check on SoftSage (2.64).
  - The Failed state has **no** badge (L366-371).
- **Styling.** The 4dp LinearProgressIndicator has a white track on a SoftSageFill body, which is weak. Radius 26 is yet another radius. It has no liveRegion.
- **Hidden routes.** The pill is hidden on COOKING and EDIT (L286), which is correct.

**Dead routes.** Cookbook, plan, grocery and web still exist. Plan and grocery are reachable from Saved and Profile.

### 3.2 Discover — `DiscoverScreen.kt`; screenshots `01`, `01_discover_soft…`, `03`
- **Header L112-128.** Four tiers before any action: logo, then "Good evening" in Muted, then "What's the plan, chef?" at 28 ExtraBold, then the search icon. The greeting is locked; the time-of-day line above it is noise.
- **PasteLinkCard L177-231.** This is the #1 slop on the most important surface.
  - A card title "Import a recipe" sits above a field whose placeholder already says it.
  - The SoftField uses SoftSagePale plus a Hairline border (L196-197).
  - **Then two full-width pills stacked:** "Import" (solid) and "Paste link from clipboard" (bordered fill, L211-223).

  Screenshot 01: three full-width rounded rectangles stacked (field, button, pill), and the pill is *wider* than the card content. It sits outside the card's padding, so the edges misalign by about 18dp.
- **Placeholder clipped.** It reads "Paste a YouTube or Instagram" and loses "link" (screenshot 01).
- **Quota line.** "5 of 5 imports left this week" is Muted bodySmall, at 3.26 contrast.
- **Empty recent L136.** A 🍲 emoji at 30sp beside "Nothing saved yet." Emoji rule violation, and a sad one-liner where the spec wants import above Recently Saved to do the work.
- **Recent card (screenshot 03).** A mustard gradient square with a 🍛 emoji (C10). The `By @desikitchen.d…` credit is truncated. The card has no shadow and no border, so it floats.
- **Collections row L148-162.** Six locked tiles, each with a "Plus" lock badge. The home screen ends in a paywall wall. In the screenshot, the tiles have no image and no container: just a badge plus a two-line title plus a truncated blurb ("Quick everyday plat…").
- **Radius soup on one screen:** 24 / 22 / 24 / 18.

### 3.3 Import (web route) — `ImportScreens.kt`; screenshot `06_paste_link`
- This screen duplicates the Discover paste card with **different** design. It uses a default SoftCard (28/8) and a default SoftField container, and the paste icon is **trailing** (L72-76), where Discover's link icon is leading.
- Its placeholder "YouTube or Instagram link" differs from Discover's "Paste a YouTube or Instagram link".
- The disabled Import button is grey-on-grey. Screenshot 06 is basically unreadable as a button.
- The quota copy "5 of 5 free imports left" differs from Discover's "5 of 5 imports left".
- About 70% of the screen is empty white below.

### 3.4 Review / Add-Edit — `AddEditRecipeScreen.kt`
- **Two Save primaries:** a top-bar TextButton "Save" (L98) and a bottom PillButton (L239). Violates one-primary-per-screen.
- **Credit hidden in review.** Source/creator credit is not shown in review mode; `sourceUrl` renders only when `!review` (L142-155). The spec requires creator credit and the original link.
- **No honest-gap states.** Missing ingredients or steps get no "Not found in the video caption · Add manually" state, although `RANKED_SHORTLIST.md` #8 calls for one.
- **Count line.** "Partly read · n ingredients · m steps" is labelLarge in MustardDeep (actually SoftSageDeep, 3.92). The most important trust signal is rendered in a failing colour under a misleading token name.
- **Mixed components.** Every section is a 28/8 SoftCard. Manual mode uses Material `FilterChip` (L273-277). Qty and unit have fixed widths of 48 and 56dp. The paste-ingredients dialog is a stock AlertDialog, and the default CircularProgressIndicator is used (L103).
- **Mustard aliases** at L68-69, L98, L127, L203, L214, L234, L276 and L298.

### 3.5 Recipe detail — `RecipeDetailScreen.kt`; screenshots `05`, `06` (soft)
- **Hero.** A 336dp hero (L199, L409). With no photo, it is 336dp of **mustard gradient plus a 72sp emoji** (screenshots 05 and 06), roughly 35% of the viewport spent on a cartoon plate.
- **Grab handle on a non-draggable sheet** (L237-240). It is a fake affordance and is visible in the screenshots.
- **Creator chip misuse.** The creator handle is a selected, outlined SoftChip used as a link (L248). A filter chip is being used as a hyperlink. In the screenshot it reads as a toggled tag.
- **"Open original on Instagram"** is a third pill style on the screen: a filled SoftSageFill pill with an icon.
- **Segmented switch** (L278-290). The selected segment has an outline *and* a fill *and* green bold text, while the unselected segment is Muted grey (3.26).
- **Ingredients.**
  - The quantity column is a fixed 96dp in SoftSageDeep bold (L298-301).
  - The "480 ml" for cooked chickpeas in screenshot 05 is a volume for a solid. This is a data/units-conversion smell, not design, but it is visible.
  - Rows are hairline-divided.
- **Steps (screenshot 06).** NumberBadge circles use SoftSageDeep on SoftSageFill (3.53). Step text alignment shifts between rows: step 1 text starts at x≈569 and step 2 at x≈572, which suggests the badge widths differ.
- **⋯ menu L215-229: 10 items.**
  - Items: Edit, Collection, Share, Cook mode, Ask Coco, Meal plan, Grocery, Photo, PDF, Delete.
  - "Cook mode" duplicates the sticky CTA.
  - "Ask Coco" is buried and uses the banned name.
  - Delete is not styled destructive.
- **Sticky bar** (L329-341). 28dp top corners, shadow 16, and a "Start cooking" PillButton with a PlayArrow icon. A play triangle is media semantics, not cooking.
- **Leftovers.**
  - The `SectionTitle` composable (L402-407) is unused dead code.
  - Dialogs are stock AlertDialogs with Checkbox ListItems.
  - The "In <collections>" link is bodySmall SoftSageDeep, which fails contrast.

### 3.6 Cook mode — `CookingScreen.kt`; screenshots `07`, `08` (soft)
- **Progress shown three times:**
  1. "1 / 4" in Muted in the top row
  2. A four-segment bar (L180-187)
  3. "STEP 1 OF 4" (L217)

  The NumberBadge "1" makes a fourth.
- **Same photo on every step.** A 180dp RecipeArt repeats the identical image on every step (L190-191). In the screenshots it is the mustard gradient plus emoji. It pushes the step text down, and below the step text more than 50% of the screen is empty white (07 and 08). This layout wastes the biggest, most-used screen.
- **Timer shown twice:** a pill on the photo (L194-207) and an inline row (L227-242). The copy "Start X Timer" uses title-case "Timer".
- **Emoji:** ⏰ in a snackbar (L121) and 👨‍🍳 in the empty state (L176).
- **Top row.** It holds a close circle, the title (truncated to "Weeknight Chana Mas…"), "n / N", a Checklist icon in Muted and a sparkle icon. The sparkle's contentDescription is "Ask DesiKitchen" (L172), a third assistant name.
- **Ingredient links** are blue `#1565C0` (StepText L17), an off-palette colour.
- **Bottom bar.** The disabled "Previous" ghost pill is nearly invisible (screenshot 07). Next is a solid pill.
- **Finish.** A stock AlertDialog "Mark cooked & finish".
- **"to taste" fill-in.** The ingredients sheet shows "to taste" when the quantity is null (L260). That invents a value the creator never stated, and the detail screen leaves it blank. The two screens are inconsistent, and it brushes the never-invent rule.

### 3.7 Assistant — `AssistantScreen.kt`, `AssistantSheets.kt`; screenshot `03_assistant`
- **Avatar.** `CocoAvatar` (L66-75) is an AutoAwesome sparkle on SoftSageFill. The doc comment says it "replaces the old house+hat mark", but the screenshot (stale) shows the black house+hat. Either way there is no ownable character.
- **Empty screen.** Screenshot 03 shows a header, one greeting bubble, and about 75% blank canvas. Starter chips are hidden when the library is empty (L130), so a new user gets nothing to tap.
- **Bubble styles mismatch.** Coco's bubble is white with shadow 4 (L196). The user's bubble is MustardCream (actually sage) with no shadow (L197). The shadowed bubble on white looks like a floating card.
- **Header and progress.** "New chat" is a SoftSageDeep TextButton. The progress bar colour is `Mustard` (L120).
- **Chips doing two jobs.** Recipe-link chips are `SoftChip(selected = true)` (L148). The "selected" visual is again used for links, the same misuse as the detail creator chip.
- **Sheet (`AssistantSheets.kt`).**
  - Title "Ask Coco" (L83, C7).
  - **Engineering copy shown to users:** "Answers by AI (OpenRouter)…" and "AI key not set in this build" (L86-87).
  - Uses M3 `Card(secondaryContainer)`, `OutlinedTextField` and a default `ModalBottomSheet` instead of the Soft primitives.
  - Loading copy "Thinking about…".
- **Profile echo.** Profile echoes the build-config copy too: "AI isn't configured in this build" (`MoreScreen.kt` L200).

### 3.8 Saved — `SavedScreen.kt`; screenshot `04_saved_empty`
- **Kitchen-sink tab.** One tab holds four sections: Meal plan, Grocery entry, Collections and Recipes (L97-121). The doc comment (L72-73) says it replaces Favourites, All recipes and Cookbooks. On an empty account (screenshot 04), the user sees "Nothing planned this week", "Grocery list", "No collections yet." and "Recipes (0)" before the empty-state CTA. Four empty states stack on one screen.
- **Two "New/Open" text actions** in SoftSageDeep (L195-197).
- **The empty-state CTA is the weak pill.** It is a filled-light `SoftOutlinePill` "Paste a link" (L150), visually weaker than the section links above it.
- **Containers.** EntryRow, MealPlanCard and CollectionRow are each a 24/6 SoftCard (L204-206, L225-227, L269-271). The screenshots show them with no visible container at all, which suggests clipped shadows. The rows float with inconsistent left insets: the icon sits at x≈530 and the "Meal plan" title at x≈514.
- **Two searches.** There is a search field here (L124-133) and a separate SearchScreen reached from Discover, so two search UIs exist.
- **Delete** in the collection dialog (L185) is not destructive-styled.

### 3.9 Collections tab — `CollectionsScreen.kt`; screenshot `02_collections`
- **Screenshot is broken.** `02_collections.png` is identical to `01_discover_empty.png` except for a 6×61px text-cursor blink in the paste field, so the tour captured Discover. **There is no valid screenshot of the Collections tab.**
- **List against the shortlist.** Eight text-only rows (L98-117), each a 24/6 SoftCard with a "Plus" lock badge. `RANKED_SHORTLIST.md` #10 wants a two-column grid with a mosaic; the code is a list.
- **The whole tab is a locked list of placeholders.** CuratedCollections are "placeholder catalogue names — no recipes ship with them yet" (L45). Every tap opens a stock AlertDialog "coming with DesiKitchen Plus" with a single "OK" (L73-80). A primary tab whose only interaction is "no" is a dead end.
- **PlusLockBadge** is labelSmall SoftSageDeep on SoftSageFill (L60-68), at 3.53 contrast.

### 3.10 Profile — `MoreScreen.kt`; screenshot `05_profile`
- **Avatar.** The default is a solid sage (`Mustard` alias) circle with a "?" (L94-95). "Add your name" with "0 recipes · 0 favourites" is the first thing a new user sees.
- **"Your kitchen" duplicates Saved.**
  - Collections leads to Saved.
  - Meal plan leads to Plan, which Saved already links.
  - All recipes leads to Saved.

  That is three rows to two destinations the user can already reach in one tap.
- **No Plus or account card,** although Plus at ₹69/₹699 is a spec lock. The only plan mention is a bodyMedium line buried in About (L185-186).
- **Stock Material.** Settings use M3 `ListItem` and M3 `Switch` with `checkedTrackColor = Mustard` (L144, L157). The Imperial chip is grey Muted (3.26).
- **Build-config copy** in About: "Offline answers only — AI isn't configured in this build." (L200).

### 3.11 Search — `SearchScreen.kt`
- **Own empty-state component.** It uses its own `Message()` (L125-136) instead of `EmptyState`, which makes a third empty-state style.
- **Detached match line.** The match line is rendered **outside** RecipeListRow's shadowed card, with a magic `start = 116.dp` (L114). Under each card, a line of grey text hangs below the card edge.
- **Header.** The results header "N recipes" is titleLarge ExtraBold (L99), louder than the results.
- **Spinner.** A bespoke CircularProgressIndicator in `MustardDeep` (L84).

### 3.12 Meal plan — `PlanScreen.kt`
- **Effectively untouched by the soft pass.**
  - Containers: M3 `Card` with `surfaceContainerLow` and a `primaryContainer @35%` tint for today (L207-212).
  - Chips and FAB: M3 `FilterChip` week chips (L173), and an `ExtendedFloatingActionButton` "Make grocery list" (L142-146).
  - Dialogs: four stock AlertDialogs, plus RadioButton rows.
- **Spreadsheet density.** Seven days × four slots, separated by hairlines, with "—" for empty (L341). Every slot row carries a DragIndicator, a ⋮ menu and a "+".
- **Title case.** The title "Meal Plan" is Title Case, while the rest of the app uses sentence case.
- **Off-token colours.** The sync hint is coloured `Sage` (L186). Drop targets use `MustardPale` with a `Mustard` border (L334).

### 3.13 Grocery — `GroceryScreen.kt`
- **The emoji capital of the app:**
  - store emoji 🧺⚡🛵🟠📦🛒 (L102-107)
  - 🛒 EmptyState (L160)
  - "🏠 Have at home" (L229)
  - "Copied ✓" (L302)
- **UI gradient.** `RecipeChipCard` paints `artBrush` (the mustard gradient) under a black vertical-gradient scrim (L327-329). White text sits on that scrim, and the "Meal plan" tag is white on Sage (2.64) (L331-333).
- **Stock Material.** `OutlinedTextField` (L148), `SegmentedButton` (L180), `FilterChip` (L191), `OutlinedButton` "Order Online" (L198-205), and a default `ModalBottomSheet` (L292).
- **No back affordance.** `DkTopBar` is called without `onBack` (L130) on a pushed route that has no bottom bar.
- **Order Online.** It appears twice, in the overflow menu and as a full-width outlined button. The copy is honest, which is good.

---

## 4. Top 10 fixes, ranked by impact

| Rank | Severity | Fix | Evidence |
|---|---|---|---|
| 1 | **Critical** | **Resolve the palette lock in writing, then purge aliases.** Get Sai's explicit call (sage vs mustard), update `MASTER_PRODUCT_SPEC.md` L59-60 and `DK_UI_SOFT_PASS_PLAN.md` L4, then delete `Mustard*`/`Saffron`/`Turmeric`/`Cardamom`/`DeepGreen`/`Masala`/`Jakarta` aliases and fix the 30 call sites. | C1, C2, `Theme.kt` L20-48, L119 |
| 2 | **Critical** | **Kill the mustard gradient + emoji fallback art.** Replace `RecipeArt`'s gradient/🍛 fallback with a flat neutral tile + monochrome dish glyph or initials; don't draw the emoji under loading photos. This one change fixes Discover, Detail hero, Cook, Grocery chips and Plan thumbnails at once. | `Components.kt` L91-103, L125-138; screenshots 03, 05–08 |
| 3 | **Critical** | **Force light theme** (`darkTheme = false`) or delete DarkColors; the 28 hard-coded whites make system-dark a broken UI. | C6, `Theme.kt` L78-100, L193, `MainActivity.kt` L26 |
| 4 | **High** | **One primary per screen.** Covers four places: (a) Discover paste card: field + Import only; demote clipboard to a trailing icon in the field. (b) Status pill Ready: text link "Open", not a solid PillButton. (c) Review: remove the top-bar Save. (d) Detail menu: drop "Cook mode". | `DiscoverScreen.kt` L177-231; `DesiKitchenRoot.kt` L306-385; `AddEditRecipeScreen.kt` L98/L239; `RecipeDetailScreen.kt` L215-229 |
| 5 | **High** | **Rebuild cook mode layout.** Remove the per-step 180dp image. Keep one progress indicator (the segmented bar). Keep one timer control. Make step text the hero (bigger, vertically centred). Recolour ingredient links to Ink/underline, not blue. | `CookingScreen.kt` L180-242; `StepText.kt` L17; screenshots 07/08 |
| 6 | **High** | **Purge all UI emoji** and remove the `emoji` param from EmptyState. | C9 list (6 files) |
| 7 | **High** | **Contrast pass on tokens.** Text tokens become Ink, or a darker sage ≥4.5:1 (e.g. ~#2F6E52). Muted becomes ≥#6B6B70. Nav labels: selected Ink, unselected ≥4.5:1. Add tab semantics and contentDescriptions. No white on SoftSage anywhere. | §2.6, `DesiKitchenRoot.kt` L419-453, L398-404 |
| 8 | **Medium** | **Token + component consistency.** Collapse the radii to 4 tokens (field 16 / card 24 / sheet 28 / pill full). Drop shadows from list rows and grouped settings (hairline or tonal surface instead). Replace the 17 stock AlertDialogs, plus the Plan/Grocery M3 Card/FilterChip/SegmentedButton/OutlinedTextField, with Soft equivalents. Rename SoftOutlinePill. Fix clip-before-shadow. | §2.1–2.3, §2.7; `Soft.kt` L189 |
| 9 | **Medium** | **Unify assistant identity and strip engineering copy.** Name it "Coco" everywhere, with no "Ask Coco" and no "Ask DesiKitchen". Give Coco an ownable mark instead of AutoAwesome. Delete "OpenRouter"/"AI key not set"/"this build" strings. Delete the unused NutritionSheet. Show starter chips to empty libraries with an import nudge. | C7, C11, `AssistantSheets.kt` L83-87, L136-180; `AssistantScreen.kt` L66-75, L130; `MoreScreen.kt` L200 |
| 10 | **Medium/Low** | **Information architecture cleanup.** (a) Profile: replace the duplicate "Your kitchen" rows with a Plus/account card. (b) Saved: collapse the four empty states into one. (c) Collections tab: stop being a tap-to-"OK" dead end (grid + paywall sheet per shortlist #10/#11). (d) Discover: show ≤2 locked tiles, not 6. (e) Detail: remove the fake grab handle and turn the creator chip into a plain link row. (f) Grocery: add a back button. (g) Re-capture the screenshots, since `02_collections` is wrong and all are stale vs 19:45 source. | §3.5, §3.8–3.10, §3.13 |

---

## 5. What must NOT change (product locks)

Sources: `MASTER_PRODUCT_SPEC.md` and `RANKED_SHORTLIST.md`.

**Navigation and home**
- **5 tabs, in this order:** Collections · Discover · Assistant · Saved · Profile. Assistant stays the centre tab.
- **Discover greeting:** "What's the plan, [First Name]?" stays.
- **Import above Recently Saved** on Discover.

**Import**
- **Import from YouTube and Instagram links only.**
- **Never invent recipe data.** Honest gaps beat filled guesses, which is why the cook-mode "to taste" fill-in should be removed, not expanded.
- **Creator credit and original-link** on detail and import.

**Recipe detail exclusions**
- No time, servings, nutrition, checkboxes, related recipes or bottom nav.
- One sticky primary: "Start cooking".
- The Ingredients/Steps segmented switch stays.

**Cook mode**
- One step per screen, Previous/Next, timers detected from text, keep screen awake.

**Brand rules**
- No emoji in UI. No UI gradients. No dark mode.
- One primary action per screen.
- The assistant is called **Coco**, without "Ask Coco" branding.

**Business**
- **Plus pricing ₹69/₹699.** Curated collections are Plus. The user's own recipes and collections stay free.
- **Import quota:** the number needs reconciling (C8), but quota visibility stays.

**Palette and fonts**
- Do not change the palette or fonts without Sai's explicit approval (spec L59-60). This audit recommends *resolving* the lock, not silently picking a winner.

---

## 6. Scores: shipped soft-02-05 look (1–10)

| Dimension | Score | Why |
|---|---|---|
| Visual hierarchy | **4** | ExtraBold everywhere. A four-tier header. Three stacked full-width controls in the hero. Progress shown three times in cook mode. |
| Layout | **4** | Cook mode is more than 50% dead white. A 336dp emoji hero. The status pill overlaps content. Clipboard pill wider than card content. |
| Typography | **5** | Nunito/Inter is a fine pairing, but weight inflation, an inverted scale, truncated titles and placeholders, and Title-Case drift ("Meal Plan", "Start X Timer"). |
| Spacing | **5** | Generous, but inconsistent insets (Saved icons vs titles, Search's 116dp magic offset), 13 radii, misaligned pill edges. |
| Color | **3** | Mustard gradients in a sage app, blue ingredient links, red tertiary, failing Muted/SageDeep text, accidental dark mode. |
| Brand distinctiveness | **2** | Generic sage-and-white plus an AI sparkle tab plus emoji food. Nothing reads "desi" or "DesiKitchen"; swap the logo and it's any recipe app. |
| Usability | **5** | The core flow (paste → import → detail → cook) works, and the honest copy is good. Undermined by duplicate primaries, a locked-tab dead end, a ten-item overflow, a fake grab handle, and no back on Grocery. |
| Component consistency | **3** | Soft primitives coexist with 17 stock dialogs and untouched Plan/Grocery M3. Two different paste cards. Three empty-state styles. Chips double as links. |
| Craft | **3** | Dead code (SectionTitle, NutritionSheet), lying comments and aliases, a wrong screenshot, engineering strings in UI, clipped shadows. |
| **Overall** | **3.8 / 10** | A competent functional skeleton dressed in a half-finished soft skin. The doc/code palette dispute must be settled before any polish is worth doing. |

---
*No app code was edited in producing this file.*
