# DESLOP verify: Soft creators (Detail Soft stack + Creator page)

Verified 2026-10-30 ~01:12 IST. Scope: `ui/detail/RecipeDetailScreen.kt`, `ui/creator/CreatorScreen.kt` (new), `ui/navigation/DesiKitchenRoot.kt`, `ui/AppViewModelProvider.kt`, plus the data lane (`util/CreatorProfile.kt`, `Daos.kt`, DB v6). Backups at `/tmp/dkbak/ui-creators-soft/` were diffed only and not modified.

## Expectations
| # | Expectation | Evidence | Result |
|---|---|---|---|
| D1 | Detail order: hero, title, creator row, quiet chips, Ingredients and Steps stacked, floating Start cooking | RecipeDetailScreen.kt:212 hero item; :299 title; :303-319 creator row (36dp avatar + handle, clickable to `onOpenCreator`); :324-338 FlowRow of `SoftChip(selected = false)` chips: "Open original" :329, "Follow on Instagram" :332, "More from creator" :335; :352-386 `SectionTitle("Ingredients")` then `SectionTitle("Steps")` in one sheet; :390-407 floating CTA | PASS |
| D2 | No Ingredients\|Steps SoftChip toggle | The backup's tab state and the `listOf("Ingredients","Steps")` SoftChip switch (backup :153, :334-350) are gone. `mutableIntStateOf` import removed. The only remaining SoftChips in the file are the link chips plus the plan dialog chips (:540, :561). | PASS |
| C1 | CreatorScreen on SoftSagePale | CreatorScreen.kt:167 `.background(SoftPageGround)`; Soft.kt:86 `SoftPageGround = SoftSagePale` (#F3FAF6) | PASS |
| C2 | Avatar is HTTPS only and never cached to disk | CreatorScreen.kt:104 `CreatorProfile.safeAvatarUrl` (https only, no whitespace, 9-2048 chars); :112 `diskCachePolicy(CachePolicy.DISABLED)`; :116 onError falls back to a monogram | PASS |
| C3 | IG follow | CreatorScreen.kt:196-202 shows the "Follow on Instagram" chip only when `instagramUrl` is set (IG-sourced recipe with an IG-shaped handle, CreatorScreen.kt:82-83 → CreatorProfile.profileUrl) | PASS |
| C4 | Unclaimed banner | CreatorScreen.kt:205-215 SoftSageFill card with `CreatorProfile.UNCLAIMED_COPY` (creators@desikitchen.app). `claimed` defaults to false (:136), so the banner always shows for now. | PASS |
| C5 | 2-column local grid by creatorHandle | CreatorScreen.kt:143 `repo.observeByCreator(handle)`, which calls Daos.kt:57-62 (trim, strip leading @, lower). :230-239 `chunked(2)` RecipeGridCard rows, with a Spacer for an odd last item | PASS |
| N1 | `creator/{handle}` route | DesiKitchenRoot.kt:140 `CREATOR = "creator/{handle}"`; :145 `creator()` uses `Uri.encode`; :283 Detail `onOpenCreator`; :286-288 composable with a StringType arg; AppViewModelProvider.kt:50 `CreatorViewModel(createSavedStateHandle(), …)` | PASS |
| T1 | Theme hex and fonts untouched | `Theme.kt` md5 `8a114ed8aa169d93d4b8a6229457a903` matches the backup and the B2 baseline; mtime 2026-10-29 23:49 IST | PASS |
| T2 | Soft depth B floating hero and CTA kept | `Soft.kt` md5 `6685e99fe0bef5ded1aebb35cbefaacb` matches the backup. Hero inset card: RecipeDetailScreen.kt:219-230 (16dp inset, 24dp radius, `softShadow` Hero/List). Sheet 12dp gap: :290-293. Float CTA: :394-401 (`SoftElevFloat` 12dp, 28dp shape, Hairline border). Bottom padding: :210 (B2 fix kept) | PASS |

## Build
`JAVA_HOME=/opt/tools/jdk17 ./gradlew --no-daemon :app:compileDebugKotlin :app:assembleDebug` gave BUILD SUCCESSFUL, **EXIT 0**. compileDebugKotlin was UP-TO-DATE on that run, so I re-ran it with `--rerun`: BUILD SUCCESSFUL, EXIT 0, 0 `w:` lines.

## Artifact
- `/workspace/DesiKitchen-soft-creators.apk`: 20,644,059 B, md5 `003672b3ebe1872731784b1d38e71b1a`
- Drive (My Drive root): https://drive.google.com/file/d/1mcin1uwWBS7OQSm5pk6K5bAy_pgTSSFZ/view

## Score
About 7.6 / 10 (B2 was about 7.4). The Detail screen is calmer: one action layer of quiet chips, a single scroll, and Start cooking as the only solid sage. The Creator page reuses Soft primitives with no new tokens. Points held back: duplicate creator affordances, dead VM helpers, and the stacked-length risk below.

## Open Sai decision: Ingredients|Steps stacked vs switch
This build **stacks** Ingredients and Steps in one scroll, so the mock-04 switch is gone.
- Pro: no mode to manage, scanning matches ReciMe, and a cook sees the whole recipe in one scroll.
- Con: on long recipes (for example 20+ ingredients), Steps sits several screens down, and nothing jumps there in one tap. Start cooking partly covers this because it opens the step-by-step CookingScreen.
- Options: (a) keep stacked; (b) bring back the switch; (c) keep stacked and add a small sticky "Ingredients · Steps" anchor row that scrolls to each section.

## Leftovers / risks
- Duplicate creator entry points on Detail: the creator row (:307) and the "More from creator" chip (:335) both go to the same page. "Follow on Instagram" appears on both Detail and Creator.
- Dead code: `RecipeDetailViewModel` `creatorHandle`, `creatorAvatarUrl`, `creatorProfileUrl`, `creatorFollowLabel`, `canOpenCreator` (:56-68). The Screen uses `CreatorLinks` instead, so there are two sources for the same facts. `CreatorLinks.CLAIM_EMAIL` (CreatorScreen.kt:76) is unused, and `CreatorProfile.followLabel` (YouTube label) is reached only through that dead VM getter.
- `CreatorProfile.key()` says it uses the "same rule as the DAO", but it also collapses whitespace and the DAO does not. They can diverge for YouTube channel names with double spaces. The DAO's ASCII-only `lower()` is also case-sensitive for non-ASCII handles.
- Unused imports in RecipeDetailScreen.kt (present before this pass): ShoppingCart :20, offset :33, statusBarsPadding :36, itemsIndexed :41, Close :48, TextAlign :77. The B2 `statusBars`/`asPaddingValues`/`WindowInsets` imports were removed.
- Carried over from B2: the CTA `.navigationBarsPadding()` (:396) is redundant, and `DetailCtaClearance` 100dp is hard-coded in Root (:360).
- Coil still uses its memory cache for avatars, which is fine because nothing is persisted. OkHttp has no separate HTTP disk cache configured, so this holds.
- Not checked on a device: stacked-sheet scroll performance on long recipes (the whole sheet is one LazyColumn item, so every row is composed at once), avatar fallback on IG CDN 403s, and back-stack behaviour for Detail → Creator → Detail → Creator loops.
