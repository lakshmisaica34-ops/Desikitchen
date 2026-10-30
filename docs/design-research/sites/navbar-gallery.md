# Navbar Gallery

- **URL:** https://navbar.gallery
- **Set:** Himanshu 40, Post (#14)
- **Fetched 2026-10-29:** 200 (home + type list)
- **Platform:** Web-only
- **DesiKitchen relevance:** 2/5
- **Tier:** Later
- **Target screens:** Discover top bar (search entry)

## What it is

550+ website navbars by type: Static/Sticky, Dropdown, Mega Menu, Side Bar, Search Bar, Announcement Bar, Full Screen, Breadcrumbs. Sponsored by Mobbin.

## Best patterns

- The Search Bar type (input in the header) is the only one that maps to a phone.
- Announcement bar → a slim quota or import-status strip is the nearest mobile analogue.

## Compose adaptation

Mobile nav comes from Material 3 Navigation Bar guidance instead (5 labelled items, filled icon when active, FAB never covers the bar). Search entry = rounded `SoftField`-look `Surface` that opens `SearchScreen`.

## Do not copy

Mega menus, dropdowns and sidebars do not apply to a 5-tab phone app.

Locks: no Theme.kt edits; Soft.kt changes wait for Sai's OK. Research only.
