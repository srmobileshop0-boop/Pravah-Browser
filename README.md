# Pravah Browser

Fast, private, ad-free Android browser. Created by Anil Yadav.

## Build the APK
**GitHub:** push this folder to a repo, open Actions > "Build APK" > download the `Pravah-APK` artifact.
**Android Studio:** open the folder, then Build > Build APK(s). Or run `./gradlew assembleRelease`.

## Features
- Start page: Google / Bing / Yahoo search buttons, shortcuts for Google, YouTube, Facebook, Instagram, Telegram, Flipkart, Amazon
- Bottom bar: back, forward, search, tab count, menu; tab switcher with grid/list view, history, + new tab
- Tabs close by swiping left, right or up; private tabs
- 3D start-up intro (plays only once, on the very first launch): the centre logo keeps spinning while P-R-A-V-A-H appears (tap to skip)
- About page (Settings > About Pravah) with version, engine, developer
- Theme: System default / Light / Dark switch in the menu; Daylight, Sky (light) and Midnight, AMOLED Black (dark) styles
- Dark mode for web pages is OFF by default; Google and YouTube turn dark only when you switch it on (menu > Dark pages)
- Downloads screen with progress, file-type icons, open, share, delete and retry
- Videos start playing directly (autoplay allowed)
- History (grouped by day, searchable) and bookmarks (searchable)
- Ad blocking: network block list + page filters; YouTube ads stripped and skipped; Google/Bing/Yahoo sponsored results hidden; pop-up blocker

- Quetta/Opera-style Settings: Search Engine, Appearance (wallpaper, font, night mode, themes, Address bar top/bottom, Scrolling behavior), Extensions (built-in), Video Options, Download Settings, Accessibility (text size, force zoom), Privacy Guard, Lock Access (phone screen lock), Manage Personal Data, default browser, notifications, share, About, feedback
- Menu sheet: theme switch, Bookmarks / History / Downloads / Private tab tiles, Set as default browser, quick bottom row

## Files
- `app/src/main/java/com/pravah/browser/MainActivity.java` - browser engine (tabs, downloads, bridge)
- `AdBlocker.java` - network-level ad/tracker blocking
- `assets/ui.html` - start page, tabs, history, bookmarks, settings, menu
- `assets/top.html` - address bar; `assets/theme.css` - theme colours
- `assets/adblock.js` - in-page ad removal (YouTube, Google, Bing, Yahoo)
