# MicroKit

- **URL:** https://microkit.co
- **Set:** Himanshu 40, Post (#28)
- **Fetched 2026-10-29:** 200
- **Platform:** Web code; interaction ideas transfer
- **DesiKitchen relevance:** 3/5
- **Tier:** Priority
- **Target screens:** Tab chrome, PillButton, SoftField, save heart

## What it is

54 free copy-paste micro-interactions for React, CSS and Tailwind: animated buttons, hover effects, tabs and inputs. MIT.

## Best patterns

- Press feedback on buttons, an animated tab indicator, input focus states.

## Compose adaptation

Recreate in Compose: press scale 0.97 with `animateFloatAsState` + `graphicsLayer`; tab indicator via Material3 `NavigationBarItem` (built-in expand); heart toggle with `spring(dampingRatio = 0.5f)`. Respect reduced motion.

## Do not copy

Hover-only effects have no touch equivalent; do not port CSS.

Locks: no Theme.kt edits; Soft.kt changes wait for Sai's OK. Research only.
