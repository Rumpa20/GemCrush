# Build Guide — CodeOnTheGo (phone only)

Gem Crush Ultimate Edition builds with **zero external dependencies**, so after the first
Gradle sync it can build fully offline.

## 1. Get the project onto the phone

1. Download `GemCrush-Ultimate-v15.zip`.
2. Extract it with your file manager (or CodeOnTheGo's extract option) to internal storage,
   e.g. `Android/media` or any writable folder. You should end up with a folder `GemCrush/`
   containing `settings.gradle.kts`, `app/`, `docs/`, etc.
3. In CodeOnTheGo choose **Open project** and select the extracted `GemCrush` folder
   (the folder that directly contains `settings.gradle.kts`).

## 2. Gradle sync

1. Open the project and let CodeOnTheGo run **Gradle sync**.
2. The first sync downloads Gradle 8.2 and AGP 8.2.2 from the network — this needs
   internet **once**. After that, builds work offline.
3. If CodeOnTheGo asks which JDK to use, pick its bundled JDK 17 (AGP 8.x requires 17).

## 3. Build the debug APK

```bash
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

CodeOnTheGo equivalent: the **Run / Build APK** button performs the same task.

## 4. Install and play

Enable *Install unknown apps* for CodeOnTheGo or your file manager, then install
`app-debug.apk`. The game is fully offline — airplane mode is fine.

## 5. Exact Gradle commands supported by this project

| Command | Result |
|---|---|
| `./gradlew assembleDebug` | Debug APK |
| `./gradlew assembleRelease` | Unsigned release APK (sign first, see APK_GUIDE) |
| `./gradlew clean` | Delete build outputs |
| `./gradlew tasks` | List all tasks |

On Windows use `gradlew.bat` instead of `./gradlew`.

## 6. Gradle wrapper note

`gradle/wrapper/gradle-wrapper.properties` pins Gradle **8.2**. The binary
`gradle-wrapper.jar` is not shipped in the ZIP (many file managers and upload pipelines
strip or corrupt binary jars). This is normal for on-device workflows:

- **CodeOnTheGo** provides its own Gradle runtime and does not need the jar.
- **Android Studio / PC**: open the project once; the IDE regenerates the wrapper
  automatically, or run `gradle wrapper --gradle-version 8.2` in the project folder.
- If a build complains about a missing wrapper jar, run `gradle wrapper` once wherever a
  Gradle installation is available, or build with a locally installed Gradle 8.2:
  `gradle assembleDebug`.

## 7. Version notes

- AGP 8.2.2 ↔ Gradle 8.2 ↔ JDK 17 — do not mix older JDKs.
- `gradle.properties` sets `-Xmx1536m` and `android.useAndroidX=false` (the app uses no
  AndroidX). Keep both as-is for on-device memory limits.
- minSdk 24 covers Android 7.0+; targetSdk 34.
