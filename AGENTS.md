# itivio

Open-source, privacy-focused Android public transport app. Routing comes from Transitous (MOTIS 2).

Read `docs/architecture.md` and `docs/design.md` before changing code.

## Toolchain

- Gradle wrapper 9.9.0, AGP 9.5.1 (built-in Kotlin), Kotlin 2.4.21, JDK 21.
- Android SDK: `/home/ubuntu/android-sdk` (in `local.properties`, not committed).
- Host is aarch64 Linux. `~/.gradle/gradle.properties` overrides aapt2 with a native arm64 build.
- Disk space on this host is low. Do not add large dependencies without need.

## Commands

```sh
./gradlew :app:assembleDebug          # build
./gradlew :app:testDebugUnitTest      # unit and Robolectric tests
./gradlew :app:lintDebug              # lint
```

## Rules

- `.github/workflows/apk.yml` runs tests, lint, and the debug build on each push and pull request. A `workflow_dispatch` run also uploads debug APKs. It uses Ubuntu 24.04, JDK 21, Android platform `android-37.0`, and build-tools `36.0.0`. It runs all three checks above and uploads `itivio-debug-apks` for 14 days.

- Release builds make APKs for `arm64-v8a`, `armeabi-v7a`, and `x86_64`, plus a universal APK.
- Signing needs `ITIVIO_KEYSTORE`, `ITIVIO_KEYSTORE_PASSWORD`, `ITIVIO_KEY_ALIAS`, and `ITIVIO_KEY_PASSWORD` (or `itivio.keystore`, `itivio.keystorePassword`, `itivio.keyAlias`, and `itivio.keyPassword` in `~/.gradle/gradle.properties`); environment variables take priority. If any value is missing, release APKs stay unsigned.
- `ui` must not import `app.itivio.data`. Use `domain` types.
- Do not log request URLs, coordinates, or journeys.
- Every Transitous request goes through the shared `HttpClient`, which sets the User-Agent.
- Send each time to Transitous with whole seconds (`motisTime()` in `TransitousRoutingRepository`). With microseconds, which `Instant.now()` gives on Android 15, MOTIS starts at the beginning of the service day.
- French address search uses `https://data.geopf.fr/geocodage/search` only for address-like queries whose first Transitous match has country `FR`. IGN uses a separate client with no contact details and receives no location bias.
- IGN fixtures are in `app/src/test/resources/ign/`. Tests never call the live IGN API.
- Screenshot tests compare against `app/src/test/screenshots/` by default. To update references, run `./gradlew :app:testDebugUnitTest --tests 'app.itivio.ui.*ScreenshotTest' -Proborazzi.test.record=true`, inspect the images, then run the normal checks. See `docs/quality.md`.
- Unit tests use the JSON fixtures in `app/src/test/resources/transitous/`. Tests never call the live API.
- Saved places use Room through KSP. Commit the exported schemas in `app/schemas/`. A schema change needs a new database version and a migration.
- Journey options use Preferences DataStore, in the `settings` file. Read each value with a fallback to its default.
- User-facing strings go in `res/values/strings.xml`. Each translatable string also needs its French text in `res/values-fr/strings.xml`. A new language needs an entry in `res/xml/locales_config.xml`.
- The `settings` DataStore file has one instance, bound in `DataModule`. The journey options and the app settings share it.
- Icons are Material Symbols Rounded, filled (`<name>_fill1_24px.xml` in google/material-design-icons, without the `android:tint` attribute), as vector drawables in `res/drawable/` (`ic_<name>.xml`).
- The typeface is Google Sans Flex, bundled unmodified in `res/font/google_sans_flex.ttf` under the SIL Open Font License 1.1. Keep `licenses/GoogleSansFlex-OFL.txt` with it.
- A string with `<b>` markup is read with `markedStringResource`. Keep the markup escaped (`&lt;b>`).

## Docker Android accessibility checks

- Container: `velin-android`, image `redroid/redroid:15.0.0_64only-latest`, Android 15 (SDK 35), arm64. Use `sudo -n docker` on this host. Do not restart the container for an app check.
- ADB target: `127.0.0.1:5555`. Confirm the connected targets with `adb devices -l`.
- TalkBack test package: `com.android.talkback`, service `com.google.android.marvin.talkback.TalkBackService`. It is a local 16.2 source build without native Braille support. Speech engine: `com.reecedunn.espeak` 1.52.0.
- Save and restore font scale, accessibility services, and the default speech engine. Leave installed test tools available. See `docs/quality.md` for coverage and limits.
- Ordinary `adb input` events bypass TalkBack navigation. Use a real input device or a virtual Linux keyboard exposed to the container. Send accessibility actions only when the node lists them.
