# TypeUI

- **URL:** https://typeui.sh
- **Set:** Extras (#E11)
- **Fetched 2026-10-29:** 429 / Vercel checkpoint to our fetcher; used typeui.sh docs pages via search and the public GitHub repo bergside/typeui
- **Platform:** Tooling (platform-neutral)
- **DesiKitchen relevance:** 3/5
- **Tier:** Priority
- **Target screens:** Process: Soft.kt spec, QA

## What it is

CLI and MCP that generate SKILL.md or DESIGN.md design-system files (`npx typeui.sh generate --format design`), with a blueprint: Mission, Brand, Style Foundations, Accessibility (WCAG 2.2 AA), Writing Tone, Rules Do/Don't, component rules, QA checklist.

## Best patterns

- Rules use must / should; every accessibility rule must be testable.
- Component rules must cover states (default, focus, active, disabled, loading, error), long content, overflow and empty states.

## Compose adaptation

Adopt the blueprint for `docs/DESIGN.md` and add a QA checklist per Soft primitive (states, 48dp touch target, contrast). Documentation only.

## Do not copy

Web keyboard-first rules translate to TalkBack and focus order on Android.

Locks: no Theme.kt edits; Soft.kt changes wait for Sai's OK. Research only.
