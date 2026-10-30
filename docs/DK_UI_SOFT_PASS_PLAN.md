# DK UI — soft-pass plan

**Status (2026-10-29):** Soft 02–05 shipped in `DesiKitchen-soft-02-05.apk`. Soft.kt + screens owned by DK UI.  
**Theme.kt:** SoftSage `#5BAF8A` + SoftSageFill `#EAF6EE` + Nunito/Inter already live (Sai OK). Mustard aliases remap to SoftSage — **do not edit Theme.kt**; hold competing palette/type edits. Treat any mustard-locked wording below as historical.  
**Refs:** Sai Soft Design Lab mocks 01–05 (approved); earlier ReciMe/Pasta refs for layout feel only.

## UI tree (Phase 1 Compose)

| Area | Path | Soft-pass role |
|------|------|----------------|
| Shell / tabs | `ui/navigation/DesiKitchenRoot.kt` | Keep tab locks; chrome only if rebuild leaves gaps |
| Soft primitives | `ui/components/Soft.kt` | Already present (`SoftCard`, `SoftField`, `PillButton`, `NumberBadge`) — **own after rebuild**, don’t fork |
| Shared chrome | `ui/components/Components.kt`, `Extras.kt` | Airy `RecipeGridCard` / EmptyState; no Theme edits |
| Discover | `ui/discover/DiscoverScreen.kt` | Soft paste card, shorter empty copy, airy recent/collection chips |
| Import (paste link) | `ui/importer/ImportScreens.kt` | **Grok Bot owns this pass** (bg extract + simplify) |
| Review / edit | `ui/edit/AddEditRecipeScreen.kt` | Soft fields; hide aisle/note/timer clutter on Review import |
| Recipe detail | `ui/detail/RecipeDetailScreen.kt` | Keep clean lock: **no** nutrition / time / servings / pantry checkboxes |
| Cook mode | `ui/cooking/CookingScreen.kt` | Toward Pasta ref: numbered steps, mustard current step, soft progress |
| Saved / Search / Collections / Assistant / Profile / Plan / Grocery | `ui/saved`, `search`, `collections`, `assistant`, `more`, `plan`, `grocery` | Light copy + card softness after primary screens |
| Theme | `ui/theme/Theme.kt` | **Do not edit** without Sai + Grok Bot |

Dead / legacy (out of soft-pass unless delete batch): `home/RecipesPane.kt`, cookbooks screens still compiling.

## What the refs say (take for soft look; not Phase 1 feature reverts)

**From Pasta / home ref (mustard-forward):**
- White canvas, soft rounded search, photo cards with light depth, mustard for active / primary CTA / step number.
- Detail: full-bleed hero → white sheet with large radius; “Start Cooking” mustard pill.
- Cook: `n / N` mustard progress, one highlighted step, quiet done steps.

**From Chicken Curry ref (airy structure — adopt feel, not locked-away chrome):**
- Sheet under hero, big radius, calm type hierarchy.
- Active direction step lives in a soft raised card (not a rigid outline stack).
- Purple stat pills / Alexa bar / pantry checkboxes on detail = **refs only**; Phase 1 lock keeps detail free of nutrition/time/servings/checkboxes.

**Sai import-review complaints (simplify, not palette):**
- Blocking extract UI → background extract (Grok Bot).
- Per-step timer fields on Review → drop or collapse.
- Aisle + note rows on every ingredient → hide by default on Review (qty / unit / name only).

## Soft-pass after APK (proposed order for DK UI)

1. **Reconcile Soft.kt** with rebuild (single source; no duplicate Soft* APIs).
2. **Discover** — SoftCard paste strip, SoftField link, short empty “Nothing saved yet.”, airy collection tiles (hairline or soft fill, not heavy elevation).
3. **AddEdit / Review import** — SoftField throughout; SoftCard groups (title block / ingredients / steps); Review mode: no aisle/note/timer chrome unless expanded.
4. **Recipe detail** — verify lock still holds; optional soft sheet under hero / quieter dividers only (no stats row).
5. **Cook mode** — NumberBadge + mustard current step row toward Pasta ref.
6. **Saved / Search / Assistant / Profile** — EmptyState + card elevation pass; strip leftover filler strings.
7. **Theme.kt** — still out of scope unless Sai okays palette (then coordinate with Grok Bot first).

## Explicit non-goals this pass
- Palette / font / Theme.kt changes.
- Reintroducing detail stats, pantry checkboxes, nutrition sheet.
- Competing edits on Discover / Import / Review / Components while Grok Bot rebuild runs.

## Coordination
- Stopped DK UI soft-pass executor on Grok Bot’s clash warning.
- Theme.kt verified unchanged vs backup.
- Next code: after rebuild APK ping from Grok Bot.
