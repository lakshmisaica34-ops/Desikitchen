# DesiKitchen 1.1.0: ReciMe feature parity

The sources are **public ReciMe material only**: the Help Center, store listings and public UX showcases (see RECIME_PRODUCT_MAP.md). No ReciMe APK was decompiled and no private API was used. The design is the locked v1 UI (`mocks/v1/`, mustard #F6A823, soft green #47B977). The tab chrome is unchanged (Home · Favorites · Grocery · Profile + ☰ drawer); features were deepened inside those screens and the drawer destinations.

**Legend.** **Done** = works in the app. **Stub** = honest UI that explains what it does not do. **Won't** = deliberately out of scope.

| # | ReciMe feature | Status | DesiKitchen 1.1.0 |
|---|---|---|---|
| 1 | Import from camera (cookbook / handwritten OCR) | Done | ☰ → Add → Camera. Up to 5 photos from the camera or gallery. On-device ML Kit text recognition, with 2-column reading order and OCR clean-up (`OcrTextAssembler`). Then parse → review editor. |
| 2 | Share sheet → app (links, text, images, files) | Done | Accepts SEND / SEND_MULTIPLE / VIEW for text, links, .txt/.md files and images. Images go to the scanner; everything else goes to web or paste import (`SharedPayload`). |
| 3 | Import from web page | Done | JSON-LD → microdata → recipe cards → headings → text (SCRAPER.md). |
| 4 | Browse / search the web for recipes | Done | "Import from web" has **Browse recipes on the web / Search the web for "…"**. It opens the browser; from there you Share → DesiKitchen. |
| 5 | Instagram posts / reels | Done | Embed → OG → caption parse → Scrape Creators → OpenRouter video. Unclear results come back as an honest partial. |
| 6 | TikTok, YouTube, Pinterest, Facebook | Done | `SocialRecipeImporter`: public oEmbed/OG → caption/description parse → Scrape Creators post + transcript → OpenRouter (caption + transcript, or video). Pinterest follows the pin's outbound recipe link. |
| 7 | Paste text import | Done | Paste screen + .txt/.md file import (Markdown flattened). |
| 8 | Create recipe manually + photo | Done | Editor with a **photo from gallery or camera** (copied into app storage), remove photo, or a photo URL. |
| 9 | Servings scaling | Done | Servings −/+ scales every quantity, the grocery add, the PDF and the AI answers. |
| 10 | Metric ↔ Imperial | Done | Metric \| Imperial toggle on the recipe page; also a Profile default. |
| 11 | Ingredient names highlighted in steps (tap → scaled qty) | Done | Recipe page and cook mode. |
| 12 | Temperatures highlighted (tap → °C/°F) | Done | Recipe page and cook mode. |
| 13 | Keep screen awake on recipe | Done | Recipe page (Profile setting, default on). Cook mode is always awake. |
| 14 | Cook mode, one step at a time | Done | Steps n/N, Prev/Next, timers, Ask AI scoped to the current step. |
| 15 | Mark cooked + confetti | Done | Recipe page and the end of cook mode. **Persisted** (`cookedCount`, `lastCookedAt`, DB v2). Shows "Cooked N× · last …"; Profile shows totals. |
| 16 | Export / print PDF | Done | Recipe ⋯ → Share as PDF / Print. A4 `PdfDocument` with paginated ingredients and steps, plus the photo. Shared through FileProvider; prints through PrintManager. |
| 17 | Share recipe as text | Done | Recipe ⋯ → Share. |
| 18 | Ask AI on a recipe | Done | **OpenRouter chat** (the OPENROUTER key from local.properties) scoped to one recipe, with servings, current step and chat history in the session. Suggested prompts. Falls back to the offline assistant (labelled) when there is no key or no network. |
| 19 | Nutrition | Done (estimate) | Per-serving estimate from the built-in ingredient table, clearly labelled as an estimate. |
| 20 | Cookbooks: Cookbooks \| Recipes toggle, New Cookbook, rename/delete | Done | Drawer → Cookbooks. |
| 21 | Add/remove recipes in cookbooks (multi) | Done | Recipe → Save to cookbooks (multi-checkbox). Cookbook → Add recipes (multi-select picker with search). Search inside a cookbook. |
| 22 | Search: titles, tags, **ingredients** | Done | Home and Recipes search match all words across title / description / cuisine / meal / diet / ingredient names; title hits rank first. Tag filter chips. |
| 23 | Favourites / pinned | Done | Favorites tab. |
| 24 | Meal plan: week with Breakfast/Lunch/Dinner/Snack, add from recipes | Done | Drawer → Meal plan. |
| 25 | Future (and past) weeks | Done | Week chips from last week to +8 weeks, plus arrows. |
| 26 | Drag to move meals | Done | Long-press a meal and drag it onto any slot or meal (insert before). Also ⋮ → Move to… (day + slot), Move up/down, Servings… |
| 27 | ⋮ Remove recipes / Clear / Change start day | Done | Multi-select remove (All/None), clear week, any of the 7 start days. |
| 28 | Grocery list auto-syncs from the meal plan | Done | `PlanGrocerySync` re-syncs plan items when the plan changes and keeps ticks. It never deletes recipe/manual items. Toggle in Profile. |
| 29 | Grocery sorts Aisle \| Recipe \| A–Z \| Recently added | Done | Plus filters All / Meal plan / From recipes / Added by you. |
| 30 | Grocery recipe carousel | Done | Photo cards with a "Meal plan" badge. Open a recipe or remove its items. |
| 31 | Checked items section + Clear | Done | Collapsible Checked section, Clear checked, Clear entire list. |
| 32 | Deselect pantry staples / have at home | Done | Per-item 🏠 toggle and ⋮ → Deselect pantry staples. "Have at home" items are not counted or shared. |
| 33 | Manual grocery items + from-recipe adds (scaled) | Done | "1 kg onions" is auto-sorted into an aisle. Adds from a recipe keep scaling and pantry ticks. ⋮ → Add a recipe's ingredients… |
| 34 | Order Online (grocery checkout) | **Stub** | Sheet with an honest explanation: it copies the list and opens BigBasket / Blinkit / Zepto / Swiggy Instamart / Amazon Fresh / JioMart. There is no in-app cart or checkout. Share list is also available. |

## Out of scope (shown honestly under Profile → "Not included")
| ReciMe feature | Status | Why |
|---|---|---|
| Cloud account, sync, multi-device | Won't (stub text) | Local-first Room DB; nothing leaves the phone except imports and AI requests. |
| Real supermarket checkout | Stub | See #34. |
| Collaborative / shared cookbooks | Won't | Needs an account backend. |
| Chrome extension / web app | Won't | Android only. Share from Chrome → DesiKitchen covers web import. |
| Direct Gemini API | Won't | OpenRouter (Gemini via OpenRouter) is used. `TODO(direct Gemini)` stays in `VideoRecipeExtractor`. |
| Onboarding quiz, paywall, Plus billing | Won't | Everything is free and unlocked. |

## Upgrade
DB v1 → v2 (`MIGRATION_1_2`) adds the cooked stats, the plan position and the grocery source / have-at-home fields. It keeps every recipe, cookbook, plan and grocery item; this is tested in `DatabaseAndPdfTest`. The APK is signed with the same box debug key as 1.0.2, so it installs over 1.0.2.
