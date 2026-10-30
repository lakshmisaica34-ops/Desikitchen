# Deslop verify — Soft photo-led character pass A
Verified 2026-10-30 ~00:10 IST (Asia/Calcutta) by Grok Bot. Code-read against the actual files and built from /workspace/DesiKitchen. No git repo; Theme was checked by diffing against the /tmp/dkbak backups and by file mtime.

## Checklist (paths under app/src/main/java/com/desikitchen/app/ui/)
| # | Treatment | Result | Evidence |
|---|-----------|--------|----------|
| 1 | Detail hero: 4:3 Crop with top bias, 48dp Ink@20% scrim, SoftSheet −24dp / 30dp corners, no-photo → 120dp SoftSagePale band + 28sp monogram | PASS | detail/RecipeDetailScreen.kt:206-221 (heroHeight = maxWidth*0.75 when photo, else status bar + 120dp; RecipeArt alignment=TopCenter; scrim 48dp Ink.copy(alpha=0.20f), shown only with a photo); :252-253 SoftSheet padding top = heroHeight − SheetOverlap; :426-427 HeroFallbackHeight=120.dp, SheetOverlap=24.dp; components/Soft.kt:281-283 SoftSheet radius 30.dp; Components.kt:173 compactFallback caps the monogram at 28sp |
| 2 | Discover: LazyRow of real imageUrl thumbs only (140²); row hidden when there are none; no stock, emoji or gradient | PASS | discover/DiscoverScreen.kt:138 `withPhotos = recent.filter { hasOwnPhoto() }`; :148-153 LazyRow only when non-empty; :156-157 otherwise a quiet "Your recipes / See all" row; :141-146 an empty library shows an honest EmptyState; :255-261 140.dp tile, 20dp radius |
| 3 | Saved 2-col photo SoftCards (1:1 Crop + heart); Collections mosaic uses real thumbs first, up to 4; empty → SoftSagePale + monogram | PASS | saved/SavedScreen.kt:177-190 chunked(2) RecipeGridCard; components/Components.kt:210 aspectRatio(1f) RecipeArt (Crop), :211-226 SoftSageFill heart chip; collections/CollectionsScreen.kt:225-249 filter has photo → sortedByDescending(hasOwnPhoto) → take(4); empty → SoftSagePale + 30sp monogram; 1 cover fills the tile |
| 4 | Cook: optional 140dp context photo, left out when there's no photo or it fails to load; never a monogram hero; step text leads | PASS | cooking/CookingScreen.kt:199-208 `if (photo.hasPhoto)` RecipeArt height 140.dp; hasPhoto turns false after both the imageUrl and the bundled photo fail (Components.kt:142-145), so the slot collapses; step text 26sp Ink at :210-215 |
| 5 | Cascade: imageUrl → bundled → SoftSagePale monogram; a Coil failure falls through; no stock, demo, emoji or gradient | PASS | components/Components.kt:133-146 RecipePhotoState (ownFailed/bundledFailed); :160-186 RecipeArt (SoftSagePale bg, AsyncImage Crop, onError → photo.onError); :101-113 bundled assets only on clear dish keywords (assets/food/*.jpg). The only Brush in the touched screens is the detail scrim |

Cookbooks (cookbooks/CookbooksScreen.kt:174-185): 2×2 RecipeArt mosaic (sorted by own photo first), monogram for fewer than 2 recipes, no emoji or gradient. Nav code never calls CookbooksScreen, so it looks like dead code.

## Theme
Pass A did not touch Theme.kt (mtime 2026-10-29 23:49 IST, before the pass-A backups at 00:05). SoftSage #5BAF8A, SoftSageDeep #3F8F6C, SoftSageFill #EAF6EE and SoftSagePale #F3FAF6 are unchanged. Nunito (headings) and Inter (body) font families are unchanged (Theme.kt:102-116). Diffs against older backups show only earlier-pass changes (Muted contrast, onPrimary=Ink, light-only lock).

## Build (JAVA_HOME=/opt/tools/jdk17)
- `./gradlew :app:compileDebugKotlin` → EXIT 0. I also ran a forced `--rerun`, which also exited 0 with no warnings.
- `./gradlew :app:assembleDebug` → EXIT 0
- APK: /workspace/DesiKitchen-soft-photo-led.apk (sha256 8b0fa943…e988, 20.5 MB)

## Nits / leftovers (not blockers)
- If an imageUrl hotlink dies, the Discover tile drops to the bundled photo or monogram instead of being removed from the row. The cascade does the right thing, but a dead thumb can still show a small monogram tile there.
- saved/SavedScreen.kt:221,238,257,310-311 still import MustardDeep/MustardCream. In Theme these are aliases for SoftSageDeep/SoftSageFill, so they render as sage. It's naming debt only, with no visual mustard.
- CookbooksScreen NewCookbookDialog still passes an `emoji` param (:145-146). The UI doesn't render it.
- No emoji glyphs, stock URLs (unsplash/pexels/picsum) or demo recipes in ui/.

## Score
Prior Soft chrome: ~5.3/10. After photo-led pass A: **~6.3/10** (+1.0). Real photos now lead Detail, Saved, Collections and Discover. Recipes without photos fall back to a quiet monogram instead of a tall void. Cook mode stays text-first. What still holds the score back is content, not code: many recipes have no thumb, so there are lots of monogram tiles. The score covers photo-led character only; SoftSage stays locked. Verified by reading code and building; I didn't check it on a device or in screenshots.

## Drive
`1wUSMFQ5S4hUNfcjD-weMTjsffwuU9ZhF` is not a folder. It's the earlier APK *file* DesiKitchen-soft-overnight-final.apk, which sits in My Drive root. Drive has no folder named "DesiKitchen". I uploaded the new APK next to it in My Drive root: https://drive.google.com/file/d/1k71Yrwy6CUP6RqIiDRseFkAHS-74qkah/view?usp=drivesdk (private to Sai; not shared).
