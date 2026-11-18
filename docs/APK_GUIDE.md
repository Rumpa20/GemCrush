# APK Generation Guide (release)

## Option A — CodeOnTheGo (phone only)

1. Open the project, menu → **Build release APK** (or run `./gradlew assembleRelease`).
2. `assembleRelease` produces an **unsigned** APK:
   `app/build/outputs/apk/release/app-release-unsigned.apk`.
3. To sign on-device, add a signing config. Generate a keystore once (CodeOnTheGo's
   keystore tool, or `keytool` if available):

```
keytool -genkeypair -v -keystore gemcrush.jks -alias gemcrush \
  -keyalg RSA -keysize 2048 -validity 10000
```

4. Create `keystore.properties` next to `app/build.gradle.kts` (do NOT commit it):

```
storeFile=gemcrush.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=gemcrush
keyPassword=YOUR_KEY_PASSWORD
```

5. Add to `app/build.gradle.kts` inside `android { }`:

```kotlin
signingConfigs {
    create("release") {
        val p = java.util.Properties()
        val f = rootProject.file("keystore.properties")
        if (f.exists()) {
            p.load(f.inputStream())
            storeFile = file(p["storeFile"] as String)
            storePassword = p["storePassword"] as String
            keyAlias = p["keyAlias"] as String
            keyPassword = p["keyPassword"] as String
        }
    }
}
buildTypes {
    release {
        signingConfig = signingConfigs.getByName("release")
    }
}
```

6. Run `./gradlew assembleRelease` again → signed
   `app/build/outputs/apk/release/app-release.apk`, installable and shareable.

**Keep `gemcrush.jks` and its passwords safe** — losing them means you can never update the
installed app (Android requires the same signing key for updates).

## Option B — Android Studio / PC

Open the project, **Build → Generate Signed Bundle / APK → APK**, follow the wizard with the
same keystore. Output lands in `app/release/`.

## Version bumps for each release

Edit `app/build.gradle.kts`:
- `versionCode` — increase by 1 every release (currently 15)
- `versionName` — user-visible string (currently "15.0")

## Store listing asset

`store_icon_512.png` in the project root is the 512×512 Google Play icon. All launcher
icons are generated from it (mipmap-mdpi…xxxhdpi, 48–192 px, plus adaptive icons from
`ic_launcher_background`/`ic_launcher_foreground` drawables).
