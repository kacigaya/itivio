# Itivio

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

- Release builds make APKs for `arm64-v8a`, `armeabi-v7a`, and `x86_64`, plus a universal APK.
- Signing needs `ITIVIO_KEYSTORE`, `ITIVIO_KEYSTORE_PASSWORD`, `ITIVIO_KEY_ALIAS`, and `ITIVIO_KEY_PASSWORD` (or `itivio.keystore`, `itivio.keystorePassword`, `itivio.keyAlias`, and `itivio.keyPassword` in `~/.gradle/gradle.properties`); environment variables take priority. If any value is missing, release APKs stay unsigned.
- `ui` must not import `app.itivio.data`. Use `domain` types.
- Do not log request URLs, coordinates, or journeys.
- Every Transitous request goes through the shared `HttpClient`, which sets the User-Agent.
- Unit tests use the JSON fixtures in `app/src/test/resources/transitous/`. Tests never call the live API.
- User-facing strings go in `res/values/strings.xml`.
- Icons are Material Symbols vector drawables in `res/drawable/` (`ic_<name>.xml`).
