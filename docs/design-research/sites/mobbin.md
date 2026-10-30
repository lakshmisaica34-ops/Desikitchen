# Mobbin

- **URL:** https://mobbin.com
- **Set:** Himanshu 40 (#12)
- **Fetched 2026-10-29:** 200 (home + public explore pages: recipe-detail, tab-bar, empty-state, ReciMe flow index); screen images are login-gated
- **Platform:** Mobile-first
- **DesiKitchen relevance:** 5/5
- **Tier:** Priority
- **Target screens:** Discover, Recipe detail, Cook mode, Saved, Collections, tab chrome

## What it is

Library of 621,500+ searchable mobile and web app screenshots, plus flows and UI-element indexes from shipped apps.

## Best patterns

- Public `recipe-detail` index tags real apps (Kitchen Stories, NYT Cooking, Blue Apron, Tasty, Epicurious, Lifesum) with Bottom Sheet, Progress Indicator, Stepper, Tab and Floating Action Button: the hero-then-sheet detail and the progress-indicator cook step are the common shapes.
- Public ReciMe iOS flows exist: `Import a recipe tutorial` and `Adding a recipe to a cookbook` (6 steps). Screens themselves need a login.
- Tab Bar index shows shipped bottom bars across consumer apps; cross-check with Material 3 Navigation Bar guidance (3 to 5 labelled destinations).
- Empty State index: Pinterest (Bookmarks & Collections), Paramount+, Speak. Pattern = one line plus one button, sometimes a small illustration and suggestions.

## Compose adaptation

Use as the reference catalogue, not a copy source. Map: Bottom Sheet detail → `SoftSheet` over hero in `RecipeDetailScreen.kt`; Progress Indicator cook step → segmented `LinearProgressIndicator` + `NumberBadge` in `CookingScreen.kt`; Empty State → one `Text` + one `PillButton`.

## Do not copy

Skip nutrition tables, related-recipe carousels and servings steppers that dominate these examples (Phase 1 detail lock).

Locks: no Theme.kt edits; Soft.kt changes wait for Sai's OK. Research only.
