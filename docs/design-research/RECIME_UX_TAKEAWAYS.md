# ReciMe UX takeaways (take / reject)

Sources: `RECIME_PRODUCT_MAP.md` (in repo), `mocks/recime-match/NOTES.md` (two reference screens), Mobbin public ReciMe flow index (flow titles only; screens are login-gated), ScreensDesign public summary, ReciMe help-centre article on Ask ReciMe, appshot.gallery ReciMe page (tags: Friendly, Minimalist, Flat Design, Orange/White/Black, Sans-Serif). No claims below go beyond these sources.

## Take

| ReciMe pattern | Why it fits DesiKitchen | DesiKitchen form |
|---|---|---|
| Share/paste import with a caption → audio → original-site fallback | Matches the Add lock (paste a YT/IG link) and background import | Paste strip on Discover + background status pill (Ranked #1, #2) |
| Import tutorial flow (Mobbin flow title) | First-run users need to learn the share sheet once | One-time `SoftCard` tip under the paste strip, dismissible; no carousel |
| Cookbooks as folders (New Cookbook → title → Create) | Same shape as Collections | Collections grid + one "New collection" PillButton (#10) |
| Hero image with sheet detail (Chicken Curry ref) | Clean, image-first, no clutter required | Hero + `SoftSheet` (#3) |
| One step per card in cook mode, "3 / 6" + segmented bar, Next pill + Prev outline (Pasta ref) | Hands-busy cooking | Cook mode (#4) with `NumberBadge` |
| Screen kept awake in cook mode | Real cooking need | `keepScreenOn` while Cook mode is visible |
| Recipe-scoped AI prompts (Ask ReciMe) | Reduces blank chat | Coco prompt chips (#9) |
| Minimal, flat, friendly store frames (appshot tags) | Same tone as the soft white UI | Play Store screenshot set: paste → review → cook |

## Reject

| ReciMe pattern | Lock it breaks |
|---|---|
| Servings stepper and scaling | Clean detail: no servings |
| Nutrition, prep/cook time | Clean detail: no nutrition or time |
| Ingredient / pantry checkboxes | Clean detail: no checkboxes |
| Confetti on save or finish | Calm brand; spec bans gimmicks (at most a subtle check) |
| Camera / OCR / paste-text / website import | Add = YouTube or Instagram link only |
| Long quiz onboarding then a trial paywall | Paywall only at the 5-per-week limit; soft sheet |
| Meal Plan and Groceries as tabs, + menu tab | Tabs are locked to Collections · Discover · Assistant · Saved · Profile |
| AI behind Plus | Coco is free and unlimited |
| Orange accent | Theme.kt is locked (SoftSage live); no palette change without Sai's OK |
| Highlighted temperatures and quantities inside steps | Would require parsing and could misstate content; show the imported text as-is |
