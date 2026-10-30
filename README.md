# DesiKitchen 🍛

A desi-first recipe organiser, meal planner and grocery list for Android, built with Kotlin + Jetpack Compose (Material 3), Room and Navigation-Compose.

**Every feature is free and unlocked.** There's no subscription, paywall, billing library, ads or account, and all data stays on the device.

## Open in Android Studio
1. Android Studio Koala (2024.1) or newer, with JDK 17 (the bundled JBR works).
2. *File → Open…* → select this `DesiKitchen` folder and let Gradle sync.
3. Pick a device or emulator (API 26+) and press **Run ▶**.

## Build from the command line
```bash
export JAVA_HOME=/opt/tools/jdk17          # any JDK 17
export ANDROID_HOME=/opt/tools/android-sdk # your Android SDK
export PATH="$JAVA_HOME/bin:$PATH"
cd DesiKitchen
./gradlew :app:assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk  (signed with the box debug key; the 1.1.0 build is copied to ./DesiKitchen-1.1.0.apk)
# UI screenshot tour (Robolectric + Roborazzi, no emulator needed):
#   ./gradlew :app:testDebugUnitTest --tests '*UiTourScreenshots*' -Pscreens=1   → app/build/screens/*.png
```
Install on a phone with USB debugging: `adb install -r app/build/outputs/apk/debug/app-debug.apk`,
or copy the APK to the phone and open it (allow "Install unknown apps" for your file manager).

Tests: `./gradlew :app:testDebugUnitTest` · Lint: `./gradlew :app:lintDebug`

## Features
**v1.1.0: ReciMe-parity release.** The full table is in **FEATURE_PARITY.md**.
- Share sheet for links, text, images and .txt/.md.
- **TikTok / YouTube / Pinterest / Facebook / Instagram** import.
- Browse/search the web.
- **PDF export & print**.
- **Persisted "cooked"** stats with confetti.
- Recipe **photo from gallery/camera**.
- **Ask DesiKitchen AI** (OpenRouter, recipe-scoped; offline fallback).
- Meal plan with **future weeks, drag & drop**, Move to…, multi-remove, any start day, and **auto-synced groceries**.
- Grocery list with **Recently added** sort, source filters, **have-at-home / pantry deselect**, photo recipe carousel, share list, and an **Order Online** stub (copy list + open store).
- **Ingredient search**.
- Multi-select add to cookbooks.
- Not included: cloud sync, real checkout, collaborative cookbooks, web app/extension.

**v1.0.2:** Instagram import now uses the Scrape Creators API (`/instagram/post`) when the caption has no recipe, then OpenRouter video reading. Keys live only in git-ignored `local.properties`; the 1.0.2 personal APK contains them, so don't share it. See SCRAPER.md.

**v1.0.1 layout (matches `mocks/v1/`): bottom tabs Home · Favorites · Grocery · Profile; ☰ drawer holds Add a recipe (link / paste / camera / manual) and Cookbooks, Meal plan, All recipes, Settings.**

- **Home:** greeting, "What would you like to **cook** today?", search with filters, Popular Recipes cards (time pill, heart), round category chips, and the full recipe list. ＋ Add opens the import sheet.
- **Recipe page (v1):** photo hero with X / ♥ / ⋯, a white sheet, Servings −/+ and Time / Calories / Diet cards, and a black Ingredients | Directions pill. Ingredients have pantry checkboxes on the right; ticked items are skipped when you tap the green **Add N items to Grocery List**. Directions show coloured timer and temperature chips (plus custom "+"), and the active step is a raised card with a green **Start X Timer** button.
- **Cook mode (v1):** "Steps 3 / 7" with a segmented bar, a photo with a minutes pill, a numbered list (done = mustard tick, current = pale-mustard row), and Prev / Next Step.
- **Instagram import:** reads the recipe from the post caption (recipe-as-caption); see SCRAPER.md for the Scrape Creators / video-AI path. It never invents a recipe: if the caption has none, you get a partial draft and Paste import.

Older feature list (still present, now reached through the drawer/Profile):

- **Cookbooks:** Cookbooks | Recipes toggle, search, cover-mosaic grid, New Cookbook (name + emoji), rename/delete, add/remove recipes; Recipes view with cuisine/meal/diet filter chips and a pinned row.
- **＋ Add recipe:** browse/import from a web link (schema.org recipe cards, with text fallback), or create manually (1.2.0: only YouTube/Instagram links are importable, and import runs in the background; OCR scan was removed). Every import opens a review/edit screen before saving. You can also **Share → DesiKitchen** from Chrome, WhatsApp, Notes, etc.
- **Recipe page:**
  - Hero, tags and times.
  - A servings stepper that scales quantities, plus a Metric ↔ Imperial toggle.
  - Steps with ingredient names highlighted in blue and temperatures in orange (tap either for details).
  - Pin, add to Meal Plan, add to Groceries, Cookbooks multi-select, Edit, Delete, and Share/Export as text.
  - Nutrition estimate, **Ask DesiKitchen** (substitutions, healthier, simpler, explain techniques) and **I cooked this! 🎉**.
  - Keeps the screen on (setting).
- **Cooking Mode:** one step per full-screen card, swipe or Prev/Next, ingredient prep card, per-step timers, always screen-awake, and an Ask DesiKitchen button.
- **Meal Plan:** week view with Breakfast/Lunch/Dinner/Snack per day, add from your recipes, remove, clear week, previous/next week, configurable week start, and one-tap "Make grocery list".
- **Groceries:**
  - Build the list from the meal plan or from a recipe, or add items manually by typing e.g. "1 kg onions", which is auto-sorted into an aisle.
  - Sort by Aisle | Recipe | A–Z | Recently added, with duplicates merged; filter by source; mark "have at home".
  - Check items off, clear checked or clear all, and use a recipe carousel to open or remove a recipe's items.
- **More:** Favourites (pinned recipes), All recipes, Settings (units, keep screen on, week start), and About.

Brand and assets are original to DesiKitchen.
