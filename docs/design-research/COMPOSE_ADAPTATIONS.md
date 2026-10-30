# Compose adaptations

Research proposals for DK UI's less-flat pass. Nothing is implemented. These extend the existing Soft.kt primitives and do not fork them. Soft.kt changes wait for Sai's OK; Theme.kt is not edited.

Current Soft.kt baseline (read 2026-10-29): `SoftCard(radius 28.dp, elevation 8.dp, padding 18.dp, ambient 0x22000000 / spot 0x1A000000)`, `SoftField` (borderless, surfaceContainer, 16.dp radius, min 48.dp), `PillButton` (solid SoftSage, white text, 50% radius, min 52.dp), `NumberBadge` (SoftSageFill circle, SoftSageDeep text), `SoftSheet` (30.dp top corners, 10.dp shadow), `SoftOutlinePill` (SoftSageFill container, Ink text), `SoftChip` (selected SoftSageFill + SoftSageDeep; unselected Chip + Muted).

## 1. SoftCard elevation and radius
- Keep 28.dp for hero-level cards (paste strip, plan cards); propose a `radius = 20.dp` variant for list rows and grid tiles, so not everything looks like the same card (the "everything-a-card" tell in `mocks/v2/DIRECTION.md`).
- Elevation steps: 0.dp + `Hairline` border for in-sheet groups; 4.dp for list cards; 8.dp only for the one hero card per screen. Refero style records describe the same restraint: flat or barely elevated cards with hairline borders.
- Pressed state: scale 0.98 (pattern #12); no elevation jump.

## 2. Soft-fill chips
- Keep `SoftChip`. Propose 36.dp height with a 48.dp touch target (`Modifier.minimumInteractiveComponentSize()`).
- Selected text: SoftSageDeep on SoftSageFill measures 3.53:1: it clears only the 3:1 large-text bar, so it fails for regular 14sp chip labels. Until a token change is approved, use `fontWeight = SemiBold` or Ink text on the selected chip (16.1:1).

## 3. CTA pills
- One `PillButton` per screen (spec). Secondary = `SoftOutlinePill`; tertiary = `TextButton` in Ink.
- **Contrast finding:** white on SoftSage `#5BAF8A` is 2.64:1, below WCAG AA even for large text (3:1). Options are in *Needs Sai OK* below. Do not change it in code until Sai approves.

## 4. Tab chrome
- `NavigationBar(containerColor = White, tonalElevation = 0.dp)` + 1.dp `Hairline` top divider; `indicatorColor = SoftSageFill`, selected icon/label Ink, unselected Muted; filled icon when selected; labels always visible; reselect scrolls to top.
- The import status pill floats 8.dp above the bar and never overlaps it.

## 5. Empty states
- `EmptyState(title, body, actionLabel, onAction)`: centred column, 24.dp horizontal padding, `titleMedium` Ink, `bodyMedium` Muted, one `PillButton`. No emoji, no greeting. Illustration slot off by default (needs Sai OK).

## 6. Import paste strip
- `SoftCard` › `SoftField` (single line, `KeyboardType.Uri`, `ImeAction.Go`, trailing paste icon) › `PillButton("Import")` full width. Error under the field as `bodySmall` Chilli: "Only YouTube or Instagram links". Quota line `bodySmall` Muted from the backend.

## 7. Clean detail sheet under the hero
- Hero 4:3; `SoftSheet` overlaps by 24.dp; sheet content: title, creator + source pill, Ingredients | Steps switch, lists. No stats row. Sticky bottom bar on white with `Hairline` top: "Add to grocery" soft strip + `PillButton("Start cooking")` (as built today).

## 8. Cook NumberBadge progress
- Segmented progress: `Row` of N `Box(Modifier.weight(1f).height(4.dp).clip(CircleShape))`, done = SoftSage, current = SoftSageDeep, upcoming = SoftSageFill; `Text("3 / 6")` in `labelLarge` Ink. `NumberBadge` 40.dp for the current step. For N > 12, switch to one `LinearProgressIndicator`.

## 9. Coco chat header
- `TopAppBar`: 28.dp circular Coco avatar, "Coco" `titleMedium`, optional context pill ("About: Chicken curry") as `SoftOutlinePill`; overflow for "New chat". No Plus badge (Coco is free).

---

## Needs Sai OK (Theme proposal gate, not approved)

Theme.kt already ships sage (`SoftSage #5BAF8A`, `SoftSageDeep #3F8F6C`, `SoftSageFill #EAF6EE`, `SoftSagePale #F3FAF6`; Mustard/Saffron/Turmeric aliases point at SoftSage). Nothing below is applied. Any change needs Sai's explicit OK plus a mock, coordinated with Grok Bot.

| Pair | Ratio | WCAG |
|---|---|---|
| White on SoftSage (PillButton text) | 2.64:1 | Fails AA (even large) |
| Ink on SoftSage | 6.77:1 | Passes AA |
| SoftSageDeep on white | 3.92:1 | Large text only |
| SoftSageDeep on SoftSageFill (badge, chip) | 3.53:1 | Large/bold text only |
| Ink on SoftSageFill | 16.11:1 | Passes |
| Muted #8E8E93 on white | 3.26:1 | Large text only |
| Legacy mustard #F6A823 on white (Components.kt gradient line 92) | 1.99:1 | Fails |

Options for Sai (pick one or none):
1. **No token change:** PillButton text becomes Ink on SoftSage (6.77:1). Smallest change, Soft.kt only.
2. **Deeper CTA sage:** a new `SoftSageStrong` for PillButton fill: `#2F7A58` (white text 5.19:1; 4.68:1 as text on SoftSageFill) or `#2E6F52` (5.98 / 5.39). Theme.kt change.
3. **Body muted:** use `#6E6E73` (5.07:1; already the onSurfaceVariant value) for body-size secondary text, keep `Muted` for ≥ 18sp or icons.
4. Remove the leftover `#F6A823` gradient in Components.kt:92 (spec: no UI gradients).
