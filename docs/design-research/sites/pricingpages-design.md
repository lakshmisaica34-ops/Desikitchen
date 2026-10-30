# Pricing Pages

- **URL:** https://pricingpages.design
- **Set:** Himanshu 40 (#18)
- **Fetched 2026-10-29:** 200
- **Platform:** Web
- **DesiKitchen relevance:** 3/5
- **Tier:** Priority
- **Target screens:** Profile / Plus paywall sheet

## What it is

Gallery of pricing pages from SaaS, ecommerce and agencies.

## Best patterns

- Plan cards side by side with one action per plan; useful to judge how little a two-plan sheet needs (no claims beyond the gallery framing).

## Compose adaptation

Paywall as a `ModalBottomSheet`: two `SoftCard` plan tiles (monthly, yearly) using `SoftChip`-style selection, one `PillButton`, `SoftOutlinePill` to dismiss. Shown only at the import limit or a locked collection.

## Do not copy

No feature-comparison tables, no fake urgency, no onboarding hard paywall.

Locks: no Theme.kt edits; Soft.kt changes wait for Sai's OK. Research only.
