# Gem Crush — Ultimate Edition (v15.0)

A complete, offline-first Match-3 puzzle game for Android. Pure **Java**, **zero external
dependencies** — designed to build entirely on an Android phone with the **CodeOnTheGo IDE**,
no PC or Android Studio required.

## Project facts

| Item | Value |
|---|---|
| Project name | Gem Crush (Ultimate Edition) |
| Package / applicationId | `com.rumpa.gemduel` |
| Version name / code | 15.0 / 15 |
| Language | Java 11 (no Kotlin, no Compose) |
| Min / Target / Compile SDK | 24 / 34 / 34 |
| Gradle | 8.2 (wrapper properties included) |
| Android Gradle Plugin | 8.2.2 |
| External dependencies | none (Android framework only) |
| Persistence | SharedPreferences (key `"gd"`) |
| Graphics | 100% procedural (cached Bitmaps) — no image assets except launcher icons |
| Audio | 100% synthesized via `AudioTrack` — no audio files |
| Online | optional PHP backend (`Api.java`), silent offline fallback |

## What's in the box

- **Match-3 engine** (`GameView.java`): 8×8 board, swap + invalid-move reversal, gravity,
  cascades, combo scoring, auto-shuffle when no moves remain, pause/resume/restart,
  move counter, target score, 1–3 stars, win/fail/replay flow.
- **Special gems**: striped (row/column), wrapped bomb, color bomb (match 5), fish (match 4
  in square), **Lightning** (match 6, three lines), **Super Gem** (match 7+, 5×5 blast),
  plus special+special combo effects (SUPERNOVA, THUNDERSTORM, fish attacks, cross lines).
- **Boosters** (functional inventory, buyable in the shop): Hammer, Shuffle, +5 Moves,
  **Cross Blaster** (row+column), **Color Changer** (recolors a gem).
- **5999 levels** with deterministic seeded generation (`forLevel(n)`): rising difficulty,
  varied goals (score / jelly / cherries / orders / blockers), varied move limits,
  boss stage every 25th level, locked gems (from level 120) and treasure chests
  (from level 40, +10 coins each). 240 worlds × 25 levels, including Crystal Valley,
  Candy Kingdom, Frozen Mountain, Rainbow Forest, Magic Desert, Golden Castle,
  Diamond Galaxy, Fantasy Island and more. Architecture supports 10000+ levels.
- **Full UI**: home screen, level map (100 levels per page), profile, settings (sound/music
  toggles **with volume sliders**), shop, daily rewards + spin wheel, 17 achievements,
  daily missions, weekly challenge, leaderboard, pause/victory/fail dialogs.
- **Economy**: coins, daily login streak, win streaks, level rewards, missions — no
  payments, no ads SDK.
- **Sound & music**: fully synthesized SFX + looping music, independent toggles and volumes.

## Build it (short version)

See [docs/BUILD_GUIDE.md](docs/BUILD_GUIDE.md) for the full CodeOnTheGo walkthrough and
[docs/APK_GUIDE.md](docs/APK_GUIDE.md) for release APK signing. If anything fails, check
[docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md).

```bash
./gradlew assembleDebug     # debug APK  -> app/build/outputs/apk/debug/
./gradlew assembleRelease   # release APK (needs signing config, see APK_GUIDE)
```

## Online features (optional, honest fallback)

Leaderboards, friends, cloud save and account login use `Api.java` against a small PHP
backend (default endpoint `https://abruar.online/gempop`, changeable in-app via Settings →
Account → server). When offline or unconfigured, every online screen silently falls back to
a local/offline state — nothing is faked. All gameplay, progress, economy and achievements
work fully offline forever.

## Project layout

```
GemCrush/
├── app/src/main/java/com/rumpa/gemduel/   MainActivity, GameView, Gems, Icons, Sfx, Api
├── app/src/main/res/                      anim, drawable, mipmap-*, values (styles)
├── app/src/main/AndroidManifest.xml
├── app/build.gradle.kts
├── build.gradle.kts / settings.gradle.kts / gradle.properties
├── gradle/wrapper/gradle-wrapper.properties
├── store_icon_512.png
└── docs/                                  BUILD_GUIDE, TROUBLESHOOTING, APK_GUIDE
```

Note: `gradle/wrapper/gradle-wrapper.jar` is intentionally not shipped (binary). CodeOnTheGo
and Android Studio supply the wrapper automatically; on a PC run `gradle wrapper` once if
you need the jar. See BUILD_GUIDE for details.
