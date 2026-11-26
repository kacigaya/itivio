# itivio

Open-source, privacy-focused Android public transport app. Routing comes from Transitous (MOTIS 2).

Read `docs/architecture.md` and `docs/design.md` before changing code.

## Toolchain

- Gradle wrapper 9.9.2, AGP 9.5.2 (built-in Kotlin), Kotlin 2.4.21, JDK 21.
- Verified local Java runtime: OpenJDK 21.0.13.1 on aarch64 Linux (2026-11-21).
- Android SDK: `/home/ubuntu/android-sdk` (in `local.properties`, not committed). The app compiles against `android-37.2`.
- Host is aarch64 Linux. `~/.gradle/gradle.properties` overrides aapt2 with a native arm64 build.
- Disk space on this host is low. Do not add large dependencies without need.

## Commands

```sh
./gradlew :app:assembleDebug          # build
./gradlew :app:testDebugUnitTest      # unit and Robolectric tests
./gradlew :app:lintDebug              # lint
```

## Release packaging

- Releases from v0.14.0 on are release-signed. Pushing a tag `vX.Y.Z` runs `.github/workflows/release.yml`: it checks that `versionName` is `X.Y.Z` and that `CHANGELOG.md` has a `## X.Y.Z` section, runs tests and lint, builds `:app:assembleRelease`, checks that each APK has the release certificate (SHA-256 `e7154a733defb1dcb68a80fdc83556ff88b4ea2f836d36a3306adfa0c68a2981`), and publishes `itivio-X.Y.Z-<abi>.apk` for `universal`, `arm64-v8a`, `armeabi-v7a`, and `x86_64`, plus `SHA256SUMS.txt`. A `v0.*` tag makes a pre-release. The release notes are the changelog section. A separate `website` job then starts `pages.yml` on the default branch, because a release made with the workflow token starts no other workflow. Re-running that failed job does not try to create the release again.
- The release key is a PKCS12 keystore with the alias `itivio` and the subject `CN=itivio`, valid until 2054. The repository secrets `ITIVIO_KEYSTORE_BASE64`, `ITIVIO_KEYSTORE_PASSWORD`, `ITIVIO_KEY_ALIAS`, and `ITIVIO_KEY_PASSWORD` hold it. Its local copy and password file are outside the repository, in `~/.config/itivio/` on the development host.
- The release build uses R8 (`isMinifyEnabled` and `isShrinkResources`). Check a release APK on a device after a dependency or serialization change.
- `app/src/main/baseline-prof.txt` is a hand-written baseline profile: all app code and all Material 3 code that R8 keeps. `profileinstaller` (from Compose) installs it on first launch, and Android compiles it at the next background dexopt. Material 3 ships almost no profile of its own, and the APKs are sideloaded, so no cloud profile exists. Without this file, the first search bar expands ran in JIT (measured 2026-11-23: main-thread time of the first expand frame went from 65 ms to 22 ms on the test device). Generate a profile with Macrobenchmark only if startup or other journeys need more precise coverage.
- The open place search (`SearchOverlay` in `LocationSearch.kt`) is drawn in the activity window, not with Material 3's `ExpandedFullScreenSearchBar`, which opens a dialog window on each expand. On a Pixel 7 Pro that window cost 15 to 20 ms in the first frame (measured 2026-11-23). While closed, the overlay stays composed but is not measured or placed. `CoveredBySearch` hides the screen under an open search from screen readers and keyboard focus. It does not cover the navigation rail on wide windows.
- Prereleases v0.9.0 to v0.13.1 used debug-signed APKs named `itivio-X.Y.Z-<abi>-debug.apk`.

## Rules

- `.github/workflows/apk.yml` runs tests, lint, and the debug build on each push and pull request. A `workflow_dispatch` run also uploads debug APKs. It uses Ubuntu 24.04, JDK 21, Android platform `android-37.2`, and build-tools `36.0.0`. It runs all three checks above and uploads `itivio-debug-apks` for 14 days.

- Release builds make APKs for `arm64-v8a`, `armeabi-v7a`, and `x86_64`, plus a universal APK.
- MapLibre has no 32-bit `x86` runtime. Declare the supported ABIs directly in both Gradle filters so lint can detect `x86_64`. Keep the filters and APK version-code map in sync.
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

## Website

- `site/` is a Next.js 16 static export (`output: "export"`), npm, Node 24 in CI (verified locally on Node 26.13.1). No Tailwind or UI library: plain CSS with the app's Material 3 color tokens from `Theme.kt`.
- Commands, in `site/`: `npm ci`, `npm test` (node:test on `app/*.test.ts`), `npm run build` (writes `out/`), `npm run dev`.
- The build reads the newest non-draft GitHub release (prereleases included) and links its universal APK. It fails when that release has no universal APK. Set `GITHUB_TOKEN` to avoid the API rate limit. `PAGES_BASE_PATH` sets the base path (`/itivio` on GitHub Pages).
- In the browser, `DownloadCard` (`site/app/download.tsx`) gets the release list again from the GitHub API, without a token, and shows the newest release. If the request fails (offline, or the limit of 60 requests per hour per IP address), the card keeps the link from the build. The card shows the one-time reinstall note only for a release-signed release, which has no `-debug` APK.
- `.github/workflows/pages.yml` deploys to GitHub Pages on pushes to `main` that change `site/`, `assets/logo.svg`, or the workflow. The repository Pages source must be "GitHub Actions".
- The `github-pages` environment accepts deploys from `main` only. A release event runs on its tag, so the `redeploy` job starts the workflow again on the default branch (`gh workflow run`) and does not build.
- `site/app/fonts/google-sans-flex.woff2` is Google Sans Flex instanced to `ROND=100`, `wdth=100`, `opsz=18`, `slnt=0`, weights 300 to 800, and subset to Latin (fontTools `varLib.instancer` and `pyftsubset`). `site/public/demo.mp4` is the `demo.mp4` asset of release v0.10.1, scaled to 1280x720 with ffmpeg (H.264 CRF 24, AAC 96 kbit/s, `+faststart`). `demo-poster.webp` is its frame at 13.5 s. Make them again when the demo changes. The homepage hero loops only the light demo part of `demo.mp4` (3.5 s to 19 s): `demo.tsx` skips the dark brand cards at the start and end of the video, which read as an empty box on the dark site theme.
- `npm run build` and `npm run dev` copy `assets/logo.svg` into `site/public/`.

## README images

- `tools/readme-art.py` writes every SVG in `assets/readme/light/` and `assets/readme/dark/` (banner, buttons, badges, section headings, feature cards) and deletes the old ones there. Run it from the repository root with `uv run tools/readme-art.py`; uv installs its one dependency, `uharfbuzz`, from the script header.
- GitHub shows README SVGs as images, which cannot load fonts. The script writes text as Google Sans Flex outlines (`ROND=100`) from `res/font/google_sans_flex.ttf`, uses the `Theme.kt` colors, and takes icons from the app's vector drawables. It stops when a text does not fit.
- The banner and the feature cards have a phone variant (`-m.svg`), picked with `max-width: 640px` in `<picture>`. Screenshots in `assets/readme/shots/` are copies of the screenshot test references.

## Docker Android accessibility checks

- Container: `velin-android`, image `redroid/redroid:15.0.0_64only-latest`, Android 15 (SDK 35), arm64. Use `sudo -n docker` on this host. Do not restart the container for an app check.
- ADB target: `127.0.0.1:5555`. Confirm the connected targets with `adb devices -l`.
- Screen recordings use `adb shell screenrecord`. The test display runs at 15 Hz with ANGLE and SwiftShader. A 720×1600 display override at 280 dpi preserves the default 1080×2400 layout at 420 dpi. Restore display and navigation settings after recording.
- TalkBack test package: `com.android.talkback`, service `com.google.android.marvin.talkback.TalkBackService`. It is a local 16.2 source build without native Braille support. Speech engine: `com.reecedunn.espeak` 1.52.0.
- Save and restore font scale, accessibility services, and the default speech engine. Leave installed test tools available. See `docs/quality.md` for coverage and limits.
- Ordinary `adb input` events bypass TalkBack navigation. Use a real input device or a virtual Linux keyboard exposed to the container. Send accessibility actions only when the node lists them.
