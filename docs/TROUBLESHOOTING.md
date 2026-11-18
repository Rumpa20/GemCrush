# Troubleshooting Guide

## Gradle / build problems

**"Could not resolve com.android.application" / plugin not found**
The first sync needs internet to fetch Gradle 8.2 + AGP 8.2.2. Connect to the internet once,
sync, then you can go offline again.

**"Unsupported class file major version" / JDK error**
AGP 8.2.2 requires JDK 17. In CodeOnTheGo select the bundled JDK 17 in project settings.

**"gradle-wrapper.jar not found"**
Expected — the jar is intentionally not in the ZIP. CodeOnTheGo doesn't need it. On a PC run
`gradle wrapper` once (see BUILD_GUIDE §6), or build with a local Gradle 8.2.

**"Out of memory / daemon killed"**
`gradle.properties` already limits the JVM to 1536 MB for phones. Close other apps, or lower
to `-Xmx1024m` if your phone has ≤ 3 GB RAM. Don't raise it on-device.

**"Namespace not specified"**
Use the project folder that directly contains `settings.gradle.kts` — not a parent or child
folder. The namespace is set in `app/build.gradle.kts` (`com.rumpa.gemduel`).

**Duplicate resource / AAPT errors**
Don't add files into `res/` with uppercase letters or spaces in names. Resource names must be
lowercase `[a-z0-9_.]`.

## Runtime problems

**App crashes at launch on old Android (< 7.0)**
minSdk is 24 (Android 7.0). Older devices are not supported.

**No sound**
Check Settings → Sound/Music toggles and both volume sliders. All audio is synthesized
(AudioTrack); if the device media volume is 0 you'll hear nothing — raise media volume.

**Progress lost after reinstall**
Progress lives in SharedPreferences under key `gd` and is removed on uninstall. Use
Settings → Account → cloud save (needs your PHP backend) to keep progress across devices.

**Game feels slow on a very old phone**
The engine caches all gem/button bitmaps and reuses them; animations use a single View
invalidate loop. Close background apps; avoid running at battery-saver CPU clocks.

**Board says "No moves — shuffling" often at high levels**
That's the stuck-board detector working as intended; it reshuffles without costing a move.

## Online features

**Leaderboard / friends show "offline"**
Online features need a real backend. Point the app at your PHP server via
Settings → Account → server URL. Without a server the game stays fully playable offline —
this is by design, not a bug.

**Login via Google doesn't open**
It opens a browser and returns via the `gempop://` deep link. Some browsers block app
links; try Chrome, and confirm the manifest's intent filter is intact.

## Still stuck?

Re-extract the ZIP fresh (a partial extraction is the most common cause), verify the file
`app/src/main/java/com/rumpa/gemduel/MainActivity.java` exists, and sync again.
