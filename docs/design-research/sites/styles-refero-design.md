# Refero Styles

- **URL:** https://styles.refero.design
- **Set:** Himanshu 40, Post (#6)
- **Fetched 2026-10-29:** 200 (home, a style page, DESIGN.md examples page)
- **Platform:** Web styles; the method transfers to mobile
- **DesiKitchen relevance:** 4/5
- **Tier:** Priority
- **Target screens:** All screens (token roles), Soft.kt docs

## What it is

2,000+ AI-readable style records from real product websites, each with color roles, type scale, spacing, components and a DESIGN.md / Tailwind v4 / CSS variables export. Collections: Playful consumer app, Botanical & organic, Cream paper & serif, and more.

## Best patterns

- Each style names colors by role and states where an accent may appear. Example (ElevenLabs): two accent sparks appear only inside product visuals, never as UI chrome; flat or barely elevated cards with hairline 1px borders, 20px card radius, fully pilled buttons.
- The DESIGN.md examples page teaches: document each color by function (canvas, content surface, readable text, selected state, action) and record which face owns display vs reading text.
- It warns that a website capture suggests a direction and does not prove the layout of every screen inside a product.

## Compose adaptation

Write a DesiKitchen DESIGN.md that mirrors the live Theme.kt and Soft.kt roles (read-only): canvas `#FFFFFF`, ink `Ink`, solid `SoftSage` only on the one `PillButton` per screen, `SoftSageFill` for secondary pills, chips and badges, hairline `Hairline`, 28dp card radius, pill radius 50%. Feed it to agents so they stop inventing colors.

## Do not copy

Do not adopt another brand's palette or fonts from a style record; Theme.kt stays locked.

Locks: no Theme.kt edits; Soft.kt changes wait for Sai's OK. Research only.
