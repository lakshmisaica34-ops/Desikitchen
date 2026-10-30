# DesiKitchen: Deslop verification (Soft Critical+High pass)

**Date:** 2026-10-29, about 23:55 IST. **Verifier:** Grok Bot executor.
**Scope:** I checked the claims DK UI made about the Critical+High pass against the source code. **No app code was edited.** I found no compile-breaking typos, so there was nothing to fix.
**Baseline:** `DESLOP_AUDIT_2026-10-29.md`, Top 10 (§4) and scores (§6).
**Source freshness:** the screen files I read were modified 23:46–23:49 IST. All line numbers come from the Read tool or grep at the time of verification.

## Compile

`JAVA_HOME=/opt/tools/jdk17 ./gradlew --offline :app:compileDebugKotlin`
- **Exit code: 0** (`BUILD SUCCESSFUL`; `compileDebugKotlin UP-TO-DATE`, which means Gradle's input fingerprint matches the current sources).
- The first try, without `JAVA_HOME`, exited with **1** because Java was not found. That was an environment problem, not a code problem. The JDK is at `/opt/tools/jdk17`.
- **Compile-ready: confirmed.**

---

## Claim-by-claim

| # | Claim | Verdict | Evidence |
|---|---|---|---|
| 1 | RecipeArt: SoftSagePale + Ink monogram; no emoji or artBrush mustard gradients | **PASS** | See 1 below. |
| 2 | Force light theme | **PASS, with one gap** | See 2 below. |
| 3 | SoftCard presets Hero / List / InSheet / Collections | **PASS** | See 3 below. |
| 4 | Cook: one progress system, no "STEP n OF N", no emoji heroes | **PASS** | See 4 below. |
| 5 | Discover: clipboard is not a second full-width primary; curated Plus row demoted when empty | **PASS** | See 5 below. |
| 6 | EmptyState / emoji purge across Grocery, Cook and Discover | **PASS** | See 6 below. |
| 7 | Assistant: SoftChips + "Paste a link"; no "Ask Coco" user strings | **PASS** | See 7 below. Engineering copy remains (gap). |
| 8 | Collections 2-column grid; PlusSheet exists | **PASS** | See 8 below. |

### Evidence

**1. RecipeArt (`Components.kt`)**
- L123-124, doc comment: "own imageUrl → bundled keyword photo → SoftSagePale tile with an Ink monogram. No emoji, no gradients".
- L127-142: `Box(modifier.background(SoftSagePale))` with `recipeMonogram(...)`, `color = Ink`, ExtraBold Nunito. `AsyncImage` is drawn over it only when a photo exists.
- L112-120: `recipeMonogram` uses `"D"` as its fallback.
- A grep for `Brush.` / `artBrush` / `Gradient` across `ui/`, excluding `theme/`, found **0 hits**.
- `GroceryScreen.kt` L78 still has a dead `import androidx.compose.ui.graphics.Brush` that is never used.

**2. Force light (`MainActivity.kt`, `Theme.kt`, `themes.xml`)**
- `MainActivity.kt` L26: `DesiKitchenTheme(darkTheme = false) { // force-light product lock`.
- `Theme.kt` L193-202: the `darkTheme` parameter is `@Suppress("UNUSED_PARAMETER")` and the theme always uses `colorScheme = LightColors`. `DarkColors` (L78) is kept but unused.
- `res/values/themes.xml` and `values-v31/themes.xml` use `android:Theme.Material.Light.NoActionBar`. I found no `forceDarkAllowed`.
- **Gap:** `MainActivity.kt` L23 calls `enableEdgeToEdge()` with defaults, so it uses `SystemBarStyle.auto`. On a phone in system dark mode, the status-bar and nav-bar icons turn **light** while the app stays white, so the icons become invisible. The fix is to pass `SystemBarStyle.light(TRANSPARENT, TRANSPARENT)` for both bars. It is not compile-breaking, so I did not edit it.

**3. SoftCard presets (`Soft.kt`)**
- L66-76, `enum class SoftCardStyle`:
  - `Hero(28dp, 8dp)`
  - `List(20dp, 4dp)`
  - `InSheet(20dp, 0dp)`, which uses a hairline border (L146-147)
  - `CollectionTile(24dp, 4dp)`. The claim calls it "Collections"; the code name is `CollectionTile`.
- The default style is now `List` (L136), down from the old 28/8 default.
- Wrapper functions: `SoftCardHero` L158-164, `SoftCardList` L167-173, `SoftCardInSheet` L176-182.
- Clip-before-shadow is fixed. The shadow is applied first (L149) and `clip` is applied only to the clickable layer (L152). `RecipeGridCard` (L153-154) and `RecipeListRow` (L201-202) also put shadow before clip.

**4. Cook mode (`CookingScreen.kt`)**
- One progress system: the segments plus a single `"${idx + 1} / ${steps.size}"` (L177-195). There is no "STEP n OF N" string and `NumberBadge` is not rendered. Its import on L62 is unused.
- Photo: shown only when a real or bundled photo exists (L198-204, 140dp, down from 180). There is no monogram hero per step.
- Step text is the hero: 26sp SemiBold Ink (L211).
- One timer control (L212-231), with sentence-case copy "Start X timer".
- Emoji: none. The empty state is `EmptyState("No steps to cook", …)` (L174). The timer snackbar reads `"${t.label} timer is done"` (L122).
- The assistant icon's contentDescription is "Coco" (L170).
- Ingredient links: `StepText.kt` L20 sets `StepLinkColor = Ink`, underlined (L73-77), so the links are no longer blue.

**5. Discover (`DiscoverScreen.kt`)**
- The clipboard action is now a **trailing IconButton inside the SoftField** (L208-214), with contentDescription "Paste link from clipboard".
- `PillButton("Import")` (L221) is the only primary.
- `SoftCardHero` (L196) is the only Hero card. The empty "Recently saved" card uses `SoftCardStyle.InSheet` (L137).
- Curated row: `CuratedCollections.all.take(if (libraryEmpty) 2 else 6)` (L147, L151).
- A locked tile opens `PlusComingSoonDialog`, which wraps PlusSheet (L166-173).

**6. Emoji purge**
- A Unicode grep (U+1F300–1FAFF, U+2600–27BF, ✓/✔/⏰) over `ui/` found only:
  - a `✕` inside a KDoc comment (`AddEditRecipeScreen.kt` L314)
  - `☐` in Grocery's share/export *text* (`GroceryViewModel.kt` L126, L156). This is plain text for the share sheet, not UI chrome, so it is acceptable.
- `EmptyState` (`Components.kt` L239-268) has **no `emoji` parameter**. Its doc says "No emoji."
- Grocery:
  - The store list is plain names (L107-115).
  - The empty state is `EmptyState("Your list is empty", …)` (L169-172).
  - "Have at home" is a plain `SectionHeader` (L223) with an `Icons.Outlined.Home` icon (L371).
  - The copy action reads "Copied", with no ✓ (L296).
- The legacy `cookbooks/` code still carries an `emoji` *data* parameter (`CookbooksViewModel.kt` L22, `RecipeDetailViewModel.kt` L120). Nothing renders it.

**7. Assistant (`AssistantScreen.kt`, `AssistantSheets.kt`)**
- Empty library: starter `SoftChip`s (L142-147) plus one `PillButton("Paste a link", fill = false)` (L149).
- With a library: starter chips under the greeting while the thread is empty (L158-163).
- Input placeholder "Ask about your recipes" (L204). Detail sheet title "Coco" (`AssistantSheets.kt` L79). Detail menu item "Coco" (`RecipeDetailScreen.kt` L221).
- A grep for "Ask Coco" / "Ask DesiKitchen" in user strings found **0 hits**. Only the function name `AskDesiKitchenSheet` (L56) remains, and it is internal.
- `NutritionSheet` is **deleted** (0 grep hits).
- **Still open:**
  - `AssistantSheets.kt` L82-83 still shows "Answers by AI (OpenRouter)…" and "(AI key not set in this build)".
  - `MoreScreen.kt` L229 still shows "AI isn't configured in this build."
  - The Assistant screen still imports `Mustard*` (L64-66) and uses `color = Mustard` (L124), `MustardDeep` (L211) and `MustardCream` (L225).
  - Recipe links are still `SoftChip(selected = true)` (L176), so a selected chip is still used as a link.
  - The Coco bubble still has shadow 4 (L224).

**8. Collections and PlusSheet**
- `CollectionsScreen.kt` L123-129: `LazyVerticalGrid(GridCells.Fixed(2))`.
- Tiles use `SoftCardStyle.CollectionTile` (L205-215), with a 2×2 `Mosaic` (L217-237).
- One `PillButton "New collection"` (L147 when empty, L161 otherwise).
- Curated tiles show a monogram plus the PlusLockBadge (L167-174).
- `PlusLockBadge` text is now Ink (L89).
- `components/PlusSheet.kt` exists (131 lines):
  - `PlusPlan` ₹69/mo and ₹699/yr (L46-49)
  - `ModalBottomSheet` with a real drag handle (L72-77)
  - two selectable plan cards with `Role.RadioButton` (L94-111)
  - one `PillButton "Get Plus"` (L119) and "Not now" (L120-122)
- The old tap-to-OK dead end is gone. "Get Plus" shows the snackbar "Plus isn't available to buy yet." (L180), which is honest.

---

## 9. Top 10 status vs `DESLOP_AUDIT_2026-10-29.md` §4

| Rank | Sev | Item | Status | Evidence / remaining |
|---|---|---|---|---|
| 1 | Crit | Palette lock in writing + alias purge | **OPEN (half)** | See row notes below. |
| 2 | Crit | Kill mustard gradient + emoji art | **DONE** | §1 above. Residual: the Detail hero is still **336dp** (`RecipeDetailScreen.kt` L403, L200). With no photo, that is 336dp of SoftSagePale plus a 64sp monogram. |
| 3 | Crit | Force light | **DONE, with one gap** | System-bar icons via `enableEdgeToEdge()` defaults (§2). |
| 4 | High | One primary per screen | **DONE** | See row notes below. |
| 5 | High | Cook layout | **MOSTLY DONE** | See row notes below. |
| 6 | High | Emoji purge | **DONE** | §6. |
| 7 | High | Contrast + nav semantics | **MOSTLY DONE** | See row notes below. |
| 8 | Med | Tokens and components | **OPEN** | See row notes below. |
| 9 | Med | Assistant identity + engineering copy | **PARTIAL** | Naming fixed and NutritionSheet deleted. Engineering copy stays (§7). The `AutoAwesome` sparkle is still the mark (`AssistantScreen.kt` L75, `DesiKitchenRoot.kt` L156). |
| 10 | Med/Low | IA cleanup | **PARTIAL** | See row notes below. |

**Row 1, palette lock and aliases**
- `DK_UI_SOFT_PASS_PLAN.md` L4 now says SoftSage is live ("Sai OK") and that the mustard wording is historical.
- `MASTER_PRODUCT_SPEC.md` **L60 still says "keep mustard/green UI v1"**.
- `Theme.kt` L36-48 still has the `Mustard`/`MustardDeep`/`MustardCream`/`MustardPale`/`Saffron`/`Turmeric`/`DeepGreen`/`Cardamom`/`Masala` aliases, and L119 still has `Jakarta = Nunito`.
- **34** non-theme, non-legacy lines still reference them, for example `MoreScreen.kt` L68-70 and L105, and `AssistantScreen.kt` L64-66.

**Row 4, one primary per screen**
- Discover: clipboard moved into the field (§5).
- Status pill: "Open" is now a `TextAction` (`DesiKitchenRoot.kt` L389, L403, L437).
- AddEdit: only the bottom `PillButton` Save (L100 comment, L242).
- Detail menu: "Cook mode" removed (L218-226).

**Row 5, cook layout**
- Done: one progress system, one timer, Ink links, text as the hero.
- Remaining:
  - The same photo still repeats on every step when one exists (L199-204).
  - The "to taste" fill-in is still there (`CookingScreen.kt` L249, L274; `RecipeDetailViewModel.kt` L49, L164, L187), which breaks the never-invent rule.
  - The finish and ingredient-highlight dialogs are stock `AlertDialog`s (L258, L277).

**Row 7, contrast and nav semantics**
- `Muted` is now `#6E6E73` (`Theme.kt` L29), 5.07:1 on white, so it passes.
- Nav: selected Ink, unselected Muted, `Role.Tab` via `selectable` (`DesiKitchenRoot.kt` L445-477).
- The Ready badge is an Ink check on SoftSage (L423-429).
- Remaining:
  - `SoftSageDeep #3F8F6C` is still **3.92:1** on white. It is now mostly used for icons (1 text use left), which is fine for icons at 3:1.
  - I did not re-verify the liveRegion on the import pill (grep found no `liveRegion`), so it is likely still missing.

**Row 8, tokens and components**
- Partly fixed: clip-before-shadow, and the SoftCard presets.
- Remaining:
  - **16** stock `AlertDialog(` calls in live screens (down from 17).
  - Grocery still uses M3 `OutlinedTextField` (L158) and `OutlinedButton` (L295, L298), plus unused `FilterChip`/`SegmentedButton`/`Brush` imports.
  - Profile still uses M3 `ListItem` (L159, L171, L184) and `Switch` (L175).
  - `AssistantSheets` still uses `OutlinedTextField` (L118).
  - `SoftOutlinePill` is still misnamed (`Soft.kt` L305).
  - Radius soup persists: 16 / 18 / 20 / 24 / 28 / 30 are still hand-set.

**Row 10, IA cleanup**
- Done:
  - Saved shows a single empty state (`SavedScreen.kt` L100-106).
  - Collections is no longer a dead end (§8).
  - Discover shows 2 tiles when the library is empty.
  - The Detail fake grab handle is gone (no `GrabHandle` in the Detail file; `SoftGrabHandle` is only on real sheets, `PlusSheet.kt` L127).
  - Grocery takes an `onBack` (L123, L140).
- Open:
  - The Detail creator handle is still `SoftChip(selected = true, outlined = true)` used as a link (L242).
  - "Start cooking" still uses a `PlayArrow` icon (L333).
  - Profile still has a sage "?" avatar (`MoreScreen.kt` L105, `Mustard`).
  - The unused `SectionTitle` is still in Detail (L396).
  - Screenshots have not been re-captured. **Nothing here was verified visually.**

---

## Deslop scores for the current code (1–10)

These are the same categories as audit §6. They come from reading source only, not pixels. The screenshots are stale.

| Dimension | Audit | Now | Why |
|---|---|---|---|
| Visual hierarchy | 4 | **6** | One Hero per screen, one primary each, and cook step text as hero. ExtraBold still dominates (Discover headline L126, section titles L179, Collections L132/L192, PlusSheet L83/L107). |
| Layout | 4 | **6** | Cook mode is tighter and the paste card cleaner. The Detail 336dp hero is still big with no photo. Cook repeats the same photo on every step. |
| Typography | 5 | **5.5** | Title-case drift fixed in the timer copy. Weight inflation and the inverted scale are unchanged. |
| Spacing | 5 | **5.5** | Presets cut the radius variety for cards. Hand-set radii 14/16/18/20/24/28/30 still sit outside the tokens. |
| Color | 3 | **6.5** | No gradients, no blue links, Muted passes, Ink on sage, and light-only. Chilli tertiary and SoftSageDeep at 3.9 remain. The system bars can render light icons in dark mode. |
| Brand distinctiveness | 2 | **3** | Monogram tiles are calmer but generic. The AutoAwesome sparkle is still the "Coco" mark and the Assistant tab icon. |
| Usability | 5 | **6.5** | Collections works, the paywall is honest, there is a Grocery back button, and Assistant has starter chips. "To taste" invention and the creator chip used as a link remain. |
| Component consistency | 3 | **4.5** | Soft presets and PlusSheet are in place. 16 AlertDialogs remain, plus M3 ListItem/Switch/OutlinedTextField/OutlinedButton on Profile, Grocery and the Assistant sheet. |
| Craft | 3 | **4.5** | NutritionSheet deleted and the clip/shadow order fixed. The lying `Mustard*` aliases (34 uses), engineering strings, dead `SectionTitle` and several unused imports remain (Cooking L62/L93; Grocery L53-65, L78). |
| **Overall** | **3.8** | **5.3 / 10** | The Critical+High pass is real and honest: 7 of the 8 claims fully verified, and 1 has a small gap. The remaining debt is naming, component migration and brand. |

## Three weakest remaining details

1. **Lying palette aliases and the doc contradiction.**
   - `Theme.kt` L36-48 still has the aliases, used on 34 call sites. Examples: `AssistantScreen.kt` L124 `color = Mustard`, and `MoreScreen.kt` L105 a `Mustard` "?" avatar.
   - `MASTER_PRODUCT_SPEC.md` L60 still says the palette is locked to mustard.
   - This was Top-10 #1 (Critical) and it is still open.
2. **Engineering copy in user-facing UI.**
   - "Answers by AI (OpenRouter)…" and "AI key not set in this build" (`AssistantSheets.kt` L82-83).
   - "AI isn't configured in this build." (`MoreScreen.kt` L229).
   - Both sit inside surfaces that still use stock M3 components (`OutlinedTextField`, `ListItem`, `Switch`).
3. **Light-only theme with dark-mode system bars.**
   - `enableEdgeToEdge()` uses defaults (`MainActivity.kt` L23), so system-dark phones get invisible status-bar icons over the white app.
   - It is a one-line fix: `SystemBarStyle.light(...)` for both bars.
   - Runner-up: cook and detail invent "to taste" (`CookingScreen.kt` L249, L274; `RecipeDetailViewModel.kt` L49).

*No app code was edited. `local.properties` contains live API keys. They are not reproduced here, but check that the file is git-ignored.*
