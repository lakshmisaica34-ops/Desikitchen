# The Component Gallery

- **URL:** https://component.gallery
- **Set:** Himanshu 40, Post (#7)
- **Fetched 2026-10-29:** 200 (home + tabs, empty-state, segmented-control, toast, progress-indicator pages)
- **Platform:** Web design systems; component semantics are platform-neutral
- **DesiKitchen relevance:** 4/5
- **Tier:** Priority
- **Target screens:** Discover, Import/Review, Recipe detail, Cook mode, Saved, Collections

## What it is

60 components, 95 design systems, 2,671 examples with usage guidelines, accessibility notes and tone-of-voice notes.

## Best patterns

- Toast / Snackbar (41 examples, several mobile): an alert in a layer above content. Basis for the background-import status pill.
- Empty state (16): no data plus an alternative action. Atlassian and Elastic document tone of voice.
- Segmented control (28): between a button group and tabs. Fits Ingredients | Steps.
- Progress indicator (38): progress through discrete steps. Fits cook mode n / N.
- Tabs (80, 4 tagged Mobile): keep for in-page switching, not top-level nav.

## Compose adaptation

Use the usage-guideline text as acceptance criteria for Compose: `SnackbarHost`-style `ImportStatusPill`; `SoftChip` pair as segmented control; `LinearProgressIndicator` segments; `EmptyState` composable with one action.

## Do not copy

Ignore React/Web Component markup and web hover states.

Locks: no Theme.kt edits; Soft.kt changes wait for Sai's OK. Research only.
