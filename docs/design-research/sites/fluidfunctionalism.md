# Fluid Functionalism

- **URL:** https://www.fluidfunctionalism.com/
- **Set:** Extras (#E1)
- **Fetched 2026-10-29:** 200
- **Platform:** Web code; chat patterns transfer
- **DesiKitchen relevance:** 3/5
- **Tier:** Priority
- **Target screens:** Assistant / Coco, Discover (InputCopy → paste)

## What it is

Open-source UI system by @micka_design: 8 system pages (Fluid Hover, Motion, Reveal, Surfaces, Sizes, Typography) and 30 components including ChatMessage, AskUserQuestions, InputMessage, InputCopy, Badge, Banner, CarouselDots.

## Best patterns

- ChatMessage + InputMessage: an assistant-chat component set, directly relevant to Coco.
- AskUserQuestions: a component for an assistant asking structured follow-ups (chips instead of free typing).
- Surfaces and Sizes pages define elevation steps and touch sizes as a system.

## Compose adaptation

Coco: bubble = `Surface(shape = RoundedCornerShape(20.dp))`, user bubble `SoftSageFill` with `Ink` text, Coco bubble white `SoftCard(elevation = 0.dp)` with `Hairline` border; follow-up questions as a wrapping row of `SoftChip`. Input = `SoftField` with send `IconButton`.

## Do not copy

Fluid hover is pointer-only; skip.

Locks: no Theme.kt edits; Soft.kt changes wait for Sai's OK. Research only.
