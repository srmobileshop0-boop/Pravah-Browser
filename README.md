# Pravah Browser

Fast, private, ad-free Android browser. Created by Anil Yadav.

## Build the APK
**GitHub:** push this folder to a repo, open Actions > "Build APK" > download the `Pravah-APK` artifact.
**Android Studio:** open the folder, then Build > Build APK(s). Or run `./gradlew assembleRelease`.

## Features
- Start page: Google / Bing / Yahoo search buttons, shortcuts for Google, YouTube, Facebook, Instagram, Telegram, Flipkart, Amazon
- Bottom bar: back, forward, search, tab count, menu; tab switcher with grid/list view, history, + new tab
- Tabs close by swiping left, right or up; private tabs
- 4 themes: Daylight, Sky (light) and Midnight, AMOLED Black (dark) + optional dark mode for web pages
- History (grouped by day, searchable) and bookmarks (searchable)
- Ad blocking: network block list + page filters; YouTube ads stripped and skipped; Google/Bing/Yahoo sponsored results hidden; pop-up blocker

## Files
- `app/src/main/java/com/pravah/browser/MainActivity.java` - browser engine (tabs, downloads, bridge)
- `AdBlocker.java` - network-level ad/tracker blocking
- `assets/ui.html` - start page, tabs, history, bookmarks, settings, menu
- `assets/top.html` - address bar; `assets/theme.css` - theme colours
- `assets/adblock.js` - in-page ad removal (YouTube, Google, Bing, Yahoo)
