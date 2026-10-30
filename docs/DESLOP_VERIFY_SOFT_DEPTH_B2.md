# DESLOP verify — Soft depth B2 (Detail insets + ImportStatusPill routing)

Verified 2026-10-30 ~00:58 IST. Scope: `ui/detail/RecipeDetailScreen.kt`, `ui/navigation/DesiKitchenRoot.kt`. Backups: `/tmp/dkbak/ui-soft-depth-b2/` (read-only, diffed only).

## Fixes
| # | Expectation | Evidence | Result |
|---|---|---|---|
| R1 | Hero top pad = `HeroInsetTop` (8dp) only, no `statusTop` | RecipeDetailScreen.kt:225 `.padding(start = HeroInsetH, end = HeroInsetH, top = HeroInsetTop)`; :480 `HeroInsetTop = 8.dp`. Backup had `val statusTop = WindowInsets.statusBars…` + `top = statusTop + HeroInsetTop`; both removed. Root Scaffold has no topBar, so `inner` top already holds the status inset (DesiKitchenRoot.kt:207-212 `padding(inner).consumeWindowInsets(inner)`). | PASS |
| R2 | LazyColumn bottom = FloatBarHeight + FloatBarGap + SheetBottomAir (~116dp), no navInset | RecipeDetailScreen.kt:214; :501 FloatBarHeight = 12*2+52 = 76, :498 Gap 16, :495 Air 24 → 116dp. `val navInset = …navigationBars…` removed (backup :210). CTA `.navigationBarsPadding()` at :393 is a no-op under the consumed root inset, so it is not doubled. | PASS |
| R3 | ImportStatusPill bottom 100dp on RECIPE, 12dp elsewhere | DesiKitchenRoot.kt:320-323 `bottom = if (currentRoute == Routes.RECIPE) DetailCtaClearance else 12.dp`; :332 `DetailCtaClearance = 100.dp`; Routes.RECIPE = "recipe/{id}" (:132), which matches `destination.route` (:178). Math: CTA top = 16 + 76 = 92dp → pill 8dp clear. Hidden on COOKING/EDIT (:311), as before. | PASS |

## Theme / Soft untouched
- `Theme.kt` md5 `8a114ed8aa169d93d4b8a6229457a903` matches `/tmp/dkbak/ui-soft-depth-b2/theme.md5`; mtime 2026-10-29 23:49 IST. **Untouched.**
- `Soft.kt` md5 `6685e99fe0bef5ded1aebb35cbefaacb`, mtime 00:50:38 IST. That is the depth-B edit, older than the B2 backup (00:55:45). No B2 Soft baseline exists, but the mtime shows nothing changed this pass. **Untouched.**
- The only diffs against the B2 backups are the Detail and Root hunks above, plus comments.

## Build
`JAVA_HOME=/opt/tools/jdk17 ./gradlew --offline compileDebugKotlin assembleDebug` → BUILD SUCCESSFUL, **EXIT 0**, 0 `w:` lines.

## Artifact
- `/workspace/DesiKitchen-soft-depth-b2.apk`: 20,699,571 B, md5 `b8df203a0542c17acc8e2fa023100d3e`
- Drive (My Drive root): https://drive.google.com/file/d/1ukNGDtlMvTX6TYxNT4_OKuQhLisVNDRB/view (prior B: 1RSDaV9nSLmgjNaH-3vcuUJNZ-2rEtNr0)

## Score
~7.4 / 10 (B was ~7.0). The double status gap, the doubled nav air, and the pill/CTA collision are gone. The floating hero/CTA/sheet stack from B is unchanged.

## Leftovers
- Unused imports in RecipeDetailScreen.kt: `statusBars` (:3), `asPaddingValues` (:4), `WindowInsets` (:5), `navigationBars` (:112), `statusBarsPadding` (:40). They are harmless but are lint debt.
- CTA `.navigationBarsPadding()` (:393) is redundant under the consumed root inset. Either drop it or note it.
- `DetailCtaClearance` (100dp) is hard-coded in Root and duplicates Detail's private FloatBar tokens. It will drift if the CTA size changes.
- Device check not done: the pill over the CTA during import, the hero gap on gesture vs 3-button nav, and the last sheet row clearing the CTA.
