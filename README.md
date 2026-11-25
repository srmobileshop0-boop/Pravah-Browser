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

- v1.3: new 3D orbiting-ring intro (once only), redesigned start page, local profile sign-in card
- Settings pages: Search Engine (Google, DuckDuckGo, Brave, Bing, Ecosia, Yahoo, custom, suggestions), Video Options (listen in background, video toolbar, video menu, YouTube options), Privacy Guard (ads & trackers, HTTPS-only, fingerprint, script control, cookies, app redirects, site permissions, Advanced: Safe Browsing, DNT, De-AMP, GPC, Secure DNS shortcut, WebRTC), Lock Access (app, private tabs, history & downloads), Manage Personal Data (delete data, delete on exit, delete site data, autofill, import/export bookmarks), Download Settings (ask first, location, external downloader)

## Files
- `app/src/main/java/com/pravah/browser/MainActivity.java` - browser engine (tabs, downloads, bridge)
- `AdBlocker.java` - network-level ad/tracker blocking
- `assets/ui.html` - start page, tabs, history, bookmarks, settings, menu
- `assets/top.html` - address bar; `assets/theme.css` - theme colours
- `assets/adblock.js` - in-page ad removal (YouTube, Google, Bing, Yahoo)

## v1.4
- New logo (tricolour globe tile) everywhere: app icon, start page, About, intro
- Intro (first launch after this update): only the globe rotates; rings, blue background and logo text stay frozen; P-R-A-V-A-H letters unchanged
- Start page redesigned: big logo, glass shortcut panel, X added; touch & hold any shortcut and drag it anywhere, x to remove, + to add your own
- Wallpaper button on top of the start page: Tech / Nature / Modern wallpapers, preview + Set Wallpaper, or your own photo
- Links open once, in the same tab (no second page); ad links / ad popups are dropped
- Stronger ad blocking: many more ad networks, ad-host navigation blocked, auto-redirects to ads stopped, invisible click-jack layers removed

## v1.5
- White background everywhere (window, status/navigation bar, start page) like the Jio app
- Start page: logo globe keeps rotating (rings, text and blue tile stay still), cleaner white layout, colourful quick buttons
- Settings > Customize Address Bar: left button (Home / Back / Forward / New Tab / Refresh / Extension / Favorites), Custom Homepage, top/bottom bar, Auto-hide Bar, Swipe for Fullscreen, Swipe to View Tabs, Show Full URL
- Settings > Customize Menu: Reader, Page Zoom, Find on Page, Desktop Site, Add to Home Screen, Developer Tools on/off

## v1.6
- No bottom bar on web pages (only on the start page and tabs); address bar now has tab count and menu (three dots), a Stop button shows while loading
- App icon: white background with the logo in the centre (like the Jio app)
- Start page: tricolor line around the logo border, polished search box, segmented search-engine switch, Shortcuts section

## v1.7
- Start page redesigned: date + greeting, rotating-globe logo with tricolor border line, engine switch, quick-topic chips (News, Cricket, Movies, Weather, Recharge, Maps, Shopping, Music), Shortcuts card
- Shortcuts can be placed ANYWHERE on the start page (touch & hold, drag up/down/left/right; page auto-scrolls); Reset puts them back in a grid
- Crisp brand icons for Google, YouTube, X, Facebook, Instagram, Telegram, Flipkart, Amazon; any site you add gets a coloured letter / favicon icon
- Round or box icon shape (button in the Shortcuts header)
- About page: rotating-globe logo, gradient title, Highlights grid, Browser / Protection / Privacy / Developer cards

## v1.8
- Wallpaper now shows behind the whole start page and About page (cards are transparent glass); status/navigation bars take the wallpaper colour
- Stronger ad blocking for video sites: more ad / cam-widget networks, "Ad"/"Sponsored" labelled blocks and native ad strips are hidden
- Video download button + toolbar is OFF by default (Settings > Video Options > Download button & toolbar, or Download Settings)
- Downloads fixed: sends Referer / cookies / user-agent, finds the real video behind blob: players, downloads HLS (.m3u8) streams (AES-128 too) and saves them as MP4 in Downloads/Pravah, shows them in the Downloads list
