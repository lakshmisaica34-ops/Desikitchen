# Deslop verify: Soft depth B (Detail floating stack)

Date: 2026-10-30, ~00:55 IST. Verifier: Grok Bot (executor). Code reading plus Gradle only. Not run on a device or emulator.

## Scope / provenance
- Only 2 files changed after the pre-B backup (`/tmp/dkbak/ui-soft-depth-b/`, 00:49 IST):
  `ui/detail/RecipeDetailScreen.kt`, `ui/components/Soft.kt` (`find -newer`).
- Soft.kt diff vs backup: SoftSheet gets a new `bottomRadius: Dp = 0.dp` param (Soft.kt:318, 321) plus a doc comment. The default keeps depth A
  behaviour (top corners only) for every other caller. No token values changed.

## Theme
- `Theme.kt` md5 `8a114ed8aa169d93d4b8a6229457a903` matches `/tmp/dkbak/ui-soft-depth-b/theme.md5`, so it is **untouched**. SoftSage hex values and fonts are unchanged.

## Tokens (Soft.kt)
| Token | Expected | Actual | |
|---|---|---|---|
| Hero | elev 8, 0x22 / 0x1A | Soft.kt:73-75 | PASS |
| List | elev 4 | Soft.kt:69-71 (0x1A/0x14) | PASS |
| Float | elev 12 | Soft.kt:77-79 (0x28/0x1F) | PASS |
| SoftSheet | elev ~10 | Soft.kt:81-83 (0x1F/0x14, 10dp) | PASS |
| PillButton | elev 0 | Soft.kt:274 | PASS |
| SoftPageGround | SoftSagePale, solid | Soft.kt:86 | PASS |

## DetailScreen (RecipeDetailScreen.kt)
| Spec | Evidence | |
|---|---|---|
| SoftSagePale page ground | :202 `background(SoftPageGround)` | PASS |
| Hero H16 inset | :226, :479 `HeroInsetH = 16.dp` | PASS |
| Hero top ~8, controls on card | :226 `top = statusTop + HeroInsetTop`; buttons are inside the card Box at :259-287 | **PARTIAL, see R1** |
| 4:3, capped at 220 on short phones | :228 `maxWidth*0.75`, `< 700dp` gives `min(…,220)` (:484-485) | PASS (cap is a guess) |
| R24 on all corners | :481-482 `RoundedCornerShape(24)`, shadow, bg and clip use the same shape (:233-235) | PASS |
| Hero elev 8 (photo) | :229/:233 `SoftCardStyle.Hero` | PASS |
| No-photo: Canvas card, SoftSageFill, Ink monogram, 120dp, List elev 4 | :229 List, :232/:474 120dp, :234 Canvas bg, :248-257 SoftSageFill inner (8dp pad, R16) with Ink Nunito ExtraBold 28sp monogram | PASS (not pale-on-pale) |
| Dead photo falls back | `RecipePhotoState.hasPhoto` flips after both own and bundled fail, then collapses to the 120dp card | PASS |
| SoftSheet with no −24 overlap and a 12dp gap | :296 `top = HeroToSheetGap` (:486 12dp). `SheetOverlap` was removed | PASS |
| SoftSheet Canvas, H16, R24, elev 10 | :295-298 (`radius = bottomRadius = 24`, `color` defaults to surface), Soft.kt:325 | PASS |
| Floating Start cooking: H16, bottom 16 above nav | :392-399, :497-498 | PASS (the root Scaffold already applies the nav inset, see R2) |
| Canvas, R28, pad 12, Float elev 12, Hairline | :396-399, :499-500 | PASS |
| PillButton elev 0 | Soft.kt:274 | PASS |

## Photo-led A preserved
- The cascade still goes own imageUrl → bundled → monogram (`rememberRecipePhoto`, `RecipeArt`), with 4:3, Crop and `Alignment.TopCenter` (:238-241). PASS.
- The sheet still has its title, creator chip, Open original, segmented switch and plain ingredient/step lines. The Phase 1 lock holds: no checkboxes, servings or nutrition. PASS.
- The bottom scrim (under the old overlap) was replaced by a light top scrim at Ink 14% (:243-246) behind the white round buttons. That change is intentional.

## Soft depth A preserved
- No other screen files changed. The new SoftSheet parameter defaults to the old shape. PASS.

## Gradle (JAVA_HOME=/opt/tools/jdk17, --offline)
- `:app:compileDebugKotlin`: EXIT 0 (was up-to-date, then `--rerun` forced a recompile, also EXIT 0). No warnings or errors in the 2 changed files.
- `:app:assembleDebug`: EXIT 0. The APK was built at 00:52:55 IST.

## APK
- `/workspace/DesiKitchen-soft-depth-b.apk`, 20,491,222 B, md5 `ee3c69d01f405130267a81faab606b44`
- Drive (My Drive root): id `1RSDaV9nSLmgjNaH-3vcuUJNZ-2rEtNr0`
  https://drive.google.com/file/d/1RSDaV9nSLmgjNaH-3vcuUJNZ-2rEtNr0/view?usp=drivesdk
  Only the owner can open it, the same as the prior depth APK `1FAm7n9sg0035abeU5_QWwwC6vhiq3mpP`.

## Regressions / risks found
- **R1 (visual, low effort to fix): the status-bar gap is added twice above the hero.** The root `Scaffold` in DesiKitchenRoot.kt:207-212 uses the
  default systemBars `contentWindowInsets` and `padding(inner).consumeWindowInsets(inner)`, so Detail already starts below the status bar.
  `WindowInsets.statusBars.asPaddingValues()` (:222) ignores consumed insets, so the hero top becomes about statusBar (24-48dp) + 8 instead of ~8.
  Fix: `top = HeroInsetTop` at :226, or use `windowInsetsPadding(WindowInsets.statusBars)`, which respects consumed insets. Depth A avoided this with
  `statusBarsPadding()`.
- **R2 (cosmetic): the nav inset is also counted twice at the bottom.** :210/:214 add the raw `navInset` to the LazyColumn bottom padding, which only adds
  extra trailing air. The CTA's `navigationBarsPadding()` (:394) resolves to 0 because the inset is consumed, so the CTA really sits 16dp above the nav bar. That is correct.
- R3 (info): the whole sheet is a single LazyColumn item (:291), so ingredient and step rows are no longer lazy. This is fine at recipe scale.
- R4 (info): the 120dp no-photo card carries the 44dp round buttons at 12dp padding, top-aligned. The monogram is centred (about 41-79dp vertically),
  so nothing collides, but the card feels tight.

## Leftovers (documented, not blocking)
- ImportStatusPill (DesiKitchenRoot.kt:312-319) is still at `padding(vertical = 12.dp)` at BottomCenter, so it shows on Detail and covers the floating
  Start cooking card (navBar+16 … +92dp) while an import runs.
- The page ground is solid SoftSagePale with no wash.
- The 700dp → 220dp short-phone hero cap is a guess and needs a device check.

## Verdict
Soft depth B is **PASS with one visual defect (R1)**. Tokens, the floating stack, the no-photo card, the CTA card and Theme all match spec, and the build is green.
Deslop score is **~7.0** (Soft depth A was ~6.7). The page now reads as a layered Choco-style stack, not a flat full-bleed page. Fixing R1 and the
ImportStatusPill overlap would bring it to about 7.2.
