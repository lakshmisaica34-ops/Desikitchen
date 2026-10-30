# DesiKitchen — Master Product Specification (locked 2026-10-29)

Source of truth delivered by Sai. Full prose lives in chat turn t99u; this file captures the binding decisions for implementers.

## Brand
- App: **DesiKitchen** | Subscription: **DesiKitchen Plus**
- Mascot/assistant character: **Coco** (not the app name; no "Ask Coco" branding)
- Platform: Android-first | Market: India
- Palette: Espresso & Saffron (Page `#FFFFFF`, Porcelain `#F7F4EF`, Linen `#EFEAE1`, Oatline `#E5DED3`, Espresso `#1C1815`, Ash `#948B80`, Cocoa `#3A2E27`, Ember `#E4572E`, Ember Deep `#C13D14`, Saffron `#F5B544`, Pistachio `#2E5E4E`, Basil `#E7F0EA`)
- Type: Nunito Rounded-style headings, Inter-style body; no serif
- Hard rules: no emoji in UI; no purple/violet/magenta; no dark mode; no UI-surface gradients; one primary action per screen

## Navigation (5 tabs)
1. Collections — curated (Plus-locked)
2. Discover — home / search / import entry
3. Assistant — Coco, visually central, free unlimited
4. Saved — personal library + user collections (text-first)
5. Profile — account, Plus, settings

Recipe detail and modals: **no** bottom nav.

## Monetization
- Free: **10 imports/month**, unlimited Coco chat, save/organize, own library
- Plus: **₹69/mo** or **₹699/yr** — unlimited YT+IG imports + curated collections
- Paywall only at import limit or locked collection (no onboarding hard paywall)
- Backend enforces quotas; chat never behind paywall

## Import
- Sources: **YouTube + Instagram only** (paste URL or Android share sheet)
- Flow: validate → extract (staged, cheap-first) → review/edit → save
- Never invent ingredients, quantities, steps, servings, nutrition
- Credit creator + original source URL; Meta-safe media (no forbidden persisted thumbs)
- Background processing with quiet progress/done feedback
- Existing cascade retained where it fits: HTML/embed → ScrapeCreators → cheap model on comments → keyframes+audio Gemini

## Screens (14 categories)
Sign Up · Home · Save a Recipe · Extracting · It's Ready · Chat · Search · Recipe Detail · Collections · Saved · Profile · Settings · Premium Paywall · Onboarding

### Recipe detail exclusions
No prep/cook time blocks, servings blocks, nutrition blocks, ingredient checkboxes, related recipes, bottom nav.

### Home
Greeting: "What's the plan, [First Name]?" — import above Recently Saved — curated collections surface.

## Assistant (Coco)
Free unlimited. Prefer saved/verified recipes; label generated vs imported; dietary/safety safeguards; small avatar in chat header.

## Phase priorities
1. Core: auth, 5-tab, home, import YT+IG, extract/review/save, detail, Saved, personal collections, search, Coco
2. Monetization: curated catalogue, paywall, quota, billing, profile Plus
3. Meal plan + nutrition estimates with provenance
4. Reliability, analytics, a11y, billing/security review

## Acceptance
Never treat pretty screens as done. Import success, honest missing values, free chat, backend quota, design tokens, and brand name rules all required.

See also: `SPEC_GAP_REPORT.md` (codebase vs this spec).

## Override (Sai, 2026-10-29)
**Visual theme:** Tangerine palette + Nunito/Inter — Sai OK 2026-10-30 after mocks/desikitchen-palette-tangerine-mock.jpg + sai-discover-target.png (replaces SoftSage, OK'd 2026-10-29). Theme tokens: `Tangerine` `#FE7A25` (accent only: Discover top wash 12%→transparent, clipboard icon), `SoftPistachio` `#E9E8CA` (soft fills: Plus badge, selected-tab wash, secondary pills; `SoftPistachioPale` `#F6F6EA` for collection cards / quiet grounds), `RetroGreen` `#006732` (Material primary, primary pills, selected chips, See all; white text 7.0:1, never Ink on it), `RetroGreenDeep` `#004D25`, `Canvas` `#FFFFFF`, `Ink` `#17171A`; Hairline/Chip shifted pista-neutral. SoftSage `#5BAF8A` / `#EAF6EE` retired — the old SoftSage*/Sage*/Mustard* names remain as Theme aliases onto the new tokens. Logo / launcher mark stay Ink (D-with-fork), never green/orange fills. Do **not** change Theme.kt palette/typography further without a new mock + explicit Sai OK. Feature/IA decisions from this spec still need Sai confirmation question-by-question.

## Implementation notes (1.2.0)
- Import runs in the background: a status pill shows the stage with cancel, and when it's done you get "Review". You keep using the app meanwhile, and every import still ends on the review screen before saving.
- Review import shows ingredients as single clean lines and steps as numbered text (no timers, aisles or notes). Those stay editable in full edit mode.
- There's no OCR/photo scan: import is YT + IG links only. The free quota as built is 5 imports per rolling week.
