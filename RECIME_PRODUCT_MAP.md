# ReciMe → DesiKitchen product map (public sources)

Sources: ReciMe Help Center, App Store / Play Store copy, ScreensDesign showcase, Uiland flow index.
Not from APK decompilation.

## Information architecture

### Bottom tabs (inferred from help + UX libs)
1. **Cookbooks** — primary home for recipes; toggle view Cookbooks vs Recipes; New Cookbook; search; filter by tags/ingredients; pin recipes
2. **My Meal Plan** — weekly calendar; slots Breakfast / Lunch / Dinner / Snack; drag-reorder within week; week start day setting; clear/remove recipes menu
3. **Groceries** — list auto-built from meal plan + manual adds + from-recipe adds; recipe carousel on top; sort Aisle | Recipe | A–Z | Recently added; checked items section + Clear all
4. **(+)** floating / center — Import from web/browser share, Import from Camera (scan), Import by Pasting Text, Create recipe manually, Browse/search web recipes
5. Other likely: Discover / Explore popular searches; Profile / Settings (account, subscription, start-day, help)

### Core objects
- **Recipe**: title, image, ingredients (qty+unit+name+aisle), steps, servings, tags (meal type, cuisine, diet), cookbooks (many-to-many), source URL, nutrition (Plus), pinned flag, cooked/confetti state
- **Cookbook**: title, cover?, recipe membership (multi)
- **MealPlanEntry**: week date, day, slot (breakfast|lunch|dinner|snack), recipeId; multi recipes per slot/day
- **GroceryItem**: name, qty, unit, aisle, recipeId?, checked, deselected/have-at-home, sort meta
- **User**: cloud account, Plus subscription, weekStartDay

## Flows to clone in DesiKitchen

### A. Import (hero feature)
1. System share sheet → app (URL / Instagram / TikTok / Pinterest / Facebook / browser)
2. In-app: + → Import from Camera (OCR cookbook/handwritten)
3. + → Import by Pasting Text (line-per-ingredient/step)
4. + → in-app browser search → Import to ReciMe button → parse ingredients+steps → review → save into cookbook(s)
5. Free: ~5 imports/week; Plus: unlimited + scan + paste

### B. Recipe detail
- Hero image, title, tags, cookbooks chip
- Servings stepper (scales all quantities)
- Metric ↔ imperial toggle (Plus)
- Ingredients list; steps with blue-highlighted ingredient names (tap → qty popup) and orange temperatures (tap → convert)
- Actions: Meal Plan, Grocery, Cookbooks membership, Share, Edit, Nutrition (Plus), Cook Mode (Plus), AI chat (Plus), Export/print (Plus)
- Mark cooked → confetti
- Keep screen awake while recipe open

### C. Cooking Mode (Plus)
- One step per card, step-by-step, hands-free focus, keep screen on
- AI assistant Q&A on recipe while cooking

### D. Cookbooks
- Tab view toggle Cookbooks | Recipes
- New Cookbook → title → Create
- Open cookbook → recipe grid
- From recipe: multi-select cookbooks to add/remove
- Move = change membership
- Filter pantry / by ingredients on hand (showcase)

### E. Meal Plan
- Week scroller (future weeks too)
- Tap day+slot → pick from cookbooks
- Or from recipe Meal Plan icon → pick day + slot
- Long-press drag within week
- ⋮ Remove recipes | Clear recipes | Change start day
- Grocery list auto-syncs from plan

### F. Groceries
- Sources: meal plan (auto), recipe add, manual type
- Scaled/converted amounts carry over
- UI: recipe carousel + aisle sections + Checked Items
- Sort modes: Aisle, Recipe, A–Z, Recently added
- Deselect pantry staples; tick off while shopping
- Optional grocery checkout / delivery (out of scope for v1 DesiKitchen)
- Cloud sync / household share (stub)

### G. Search
- Title keywords
- Exact ingredient filter
- Tag filter → Apply

### H. Onboarding / monetization (optional polish later)
- Long quiz onboarding, soft paywall + 7-day trial, then account
- Skip paywall copycat for DesiKitchen; keep feature parity toggles without billing

## Plus vs Free (gate in UI, unlock all in DesiKitchen or feature-flag)
| Feature | Free | Plus |
| Import from social/web | 5/week | Unlimited |
| Camera scan | — | ✓ |
| Paste import | — | ✓ |
| Manual create | ✓ | ✓ |
| Cookbooks / meal plan / groceries | ✓ | ✓ |
| Nutrition | — | ✓ |
| Cooking Mode | — | ✓ |
| Unit converter | — | ✓ |
| AI assistant | — | ✓ |
| PDF/print | — | ✓ |

## DesiKitchen deltas
- Branding: DesiKitchen, desi-first seed recipes + aisle taxonomy for Indian grocery (masala, dals, atta, etc.)
- No ReciMe trademarks/assets
- Local-first Room DB (cloud sync later)
- Android share intent receiver for URL/text import
- Simple HTML recipe parse for public URLs (no social login scraping of private content)

## IA labels to match closely
Tabs: Cookbooks, Meal Plan, Groceries (+ Discover/More as needed)
Slots: Breakfast, Lunch, Dinner, Snack
+ menu: Import from Camera, Import by Pasting Text, Create recipe, Browse/import from web

## Ask ReciMe (Plus)
- FAB bottom-right on recipe screen
- Suggested prompts: Substitute ingredient, Make it healthier, Make it simpler, Understand the recipe
- Scoped to current recipe only; no chat history persistence
- DesiKitchen: local stub prompts + optional LLM later

## Grocery Checkout
- From Groceries → Order Online → pick partner → hand off to store site/app
- DesiKitchen v1: omit or stub button

## Advanced recipe UX details
- Screen stays awake on any recipe page (not only cooking mode)
- Temperatures in steps highlighted orange → tap for C/F convert
- Ingredient names in steps highlighted blue → tap for scaled qty
