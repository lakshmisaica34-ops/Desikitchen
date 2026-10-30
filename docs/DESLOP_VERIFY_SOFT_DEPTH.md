# Deslop verify — Soft depth pass

Verified 30 Oct 2026, ~00:40 IST, by reading the code (diffed against `/tmp/dkbak/ui-soft-depth/`, the pre-depth backups) and running Gradle. I didn't check it on a device or in screenshots.
Source mtimes are 00:37 IST; the APK was built at 00:40 IST, and no source file is newer than the APK.

## Theme lock
- `ui/theme/Theme.kt` mtime is 2026-10-29 23:49 IST, earlier than the pre-depth backup (00:36) and every depth edit (00:37). md5 `8a114ed8aa169d93d4b8a6229457a903`. This pass did not touch it.
- SoftSage `#5BAF8A`, SoftSageDeep `#3F8F6C`, SoftSageFill `#EAF6EE`, SoftSagePale `#F3FAF6`, Canvas `#FFFFFF` and the fonts are unchanged (Theme.kt:20-33). `SoftPageGround` is an alias in Soft.kt:86 and doesn't add a hex value.

## Checklist (Design Lab 1–6)
| # | Item | Result | Where |
|---|------|--------|-------|
| 1 | Named shadow tokens: List/Hero/Float/Sheet | PASS | Soft.kt:67-90 (`SoftAmbient*/SoftSpot*/SoftElev*`, `Modifier.softShadow`) |
| 1a | SoftCard List/Hero/CollectionTile use the tokens | PASS | Soft.kt:93-102 (enum carries ambient/spot), Soft.kt:178 |
| 1b | Shadow stronger than pre-depth | PARTIAL | The pre-depth code used `0x22/0x1A` on every style. Now Hero is the same (`0x22/0x1A`), but List and CollectionTile are lighter (`0x1A/0x14`, Soft.kt:69-70), as is `RecipeListRow` (Components.kt:256). Lift reads stronger only because of the pale ground, not the token alpha. Photo tiles get the Hero ambient at List elevation. |
| 2 | SoftSagePale page ground on Discover/Detail/Saved/Collections; cards, sheet and nav stay Canvas | PASS | Discover:122, Detail:197, Saved:96, Collections:128. Cards: `surface`/`surfaceContainerLowest` = white (Theme.kt:65,69). Sheet bg = surface (Detail:204). Nav bar = Scaffold `background` = Canvas (DesiKitchenRoot.kt:209). |
| 3 | Discover spacing: H20; paste → 16 → row; 28 between section titles | PASS | Discover:199-204 tokens; paste card H uses `ScreenPadH` (227); SectionRow top (158/166/171/207); LazyRows H20 (160/173). Greeting row is start 20 / end 8 so the icon button keeps its touch target (125). |
| 4 | Detail sheet 8/20/12/10 + 24 above sticky | PASS | Detail:450-455 (TitleToSource 8, PillToSwitch 20, SwitchToList 12, DetailRowPadV 10, SheetBottomAir 24); used at 272/275/305/322/345/358; contentPadding clears bar + 24 + nav inset (209). |
| 4a | Sticky bar Canvas + Hairline + Float elev 12 | PASS | Detail:365-372 (`softShadow(SoftElevFloat…)`, `background(sheetBg)`, `border(1.dp, Hairline)`) |
| 4b | PillButton elevation stays 0 | PASS | Soft.kt:274 |
| 5 | Saved and Discover photo tiles: List elev + Hero ambient, radius 20, press 0.98 | PASS | Saved grid `RecipeGridCard` Components.kt:200-207 (List radius 20, Hero ambient, `SoftCardPressScale`); Discover `RecentPhotoTile` Discover:277-290. Collections tiles stay CollectionTile 24 / List tokens (Collections:209). |
| 6 | SoftSagePale → Canvas washes | PARTIAL (accepted) | No gradient wash anywhere. The Discover ground is solid pale on purpose, as DK UI said. |

Collateral change, not on the list: the image inset inside the Saved grid card went from 18 to 14dp radius (Components.kt:210) to nest inside the 20dp card (it was 24 before). This is fine.

## Builds (JAVA_HOME=/opt/tools/jdk17)
- `./gradlew :app:compileDebugKotlin`: exit 0 (up to date; DK UI had already compiled this source). No warnings.
- `./gradlew :app:assembleDebug`: exit 0 (BUILD SUCCESSFUL in 3s).
- APK: `/workspace/DesiKitchen-soft-depth.apk`, 20,491,211 bytes, sha256 `92c9fe624ca575f3c472e94afe8ba87caa07fba1590cc3b565da0b70920569f6`.
- Drive (My Drive root, next to the photo-led APK): https://drive.google.com/file/d/1FAm7n9sg0035abeU5_QWwwC6vhiq3mpP/view?usp=drivesdk

## Known leftovers (documented, not blocking)
- Discover ground is solid SoftSagePale, with no mint→white wash.
- Detail sticky bar sits flush to the bottom with top-rounded corners. It isn't a floating inset card.
- Detail hero is still full-bleed photo-led A, not the Choco-style inset floating card.
- "Recently saved" tiles are a floating photo only, with no white card chrome.
- Detail: the SoftSheet and rows are full-width Canvas, so the pale ground only shows below the 24dp sheet-end cap and behind the bar. On Detail, depth is mostly the sheet over the photo and the bar shadow, not cards on the ground.
- List-token alpha is lower than pre-depth (see 1b). If cards still read flat on a device, raise `SoftAmbientList`/`SoftSpotList` to `0x22/0x1A`. That's a token edit and doesn't touch Theme.

## Regressions
None found in code. I found no Theme edits, no new hex values in Theme, and no extra solid-sage surfaces, and PillButton stays flat.

## Score
Photo-led A was ~6.3/10. With soft depth it's **~6.7/10** (+0.4). White cards on the pale ground finally separate on Discover, Saved and Collections, and Detail spacing is tighter and more deliberate. It still reads flatter than the Choco and mint references. There's no tonal wash, the Detail hero isn't a floating inset card, the sticky CTA is docked rather than floating, and List shadows are subtle. Content is still the biggest limit: many recipes have no photo, so they show monogram tiles. I haven't checked any of this on a device.
