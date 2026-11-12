<p align="center">
  <img src="assets/logo.svg" alt="itivio app icon" width="140">
</p>

<h1 align="center">itivio</h1>

<p align="center">
  <strong>A public transport app for Android that keeps your data on your device.</strong><br>
  <em>Plan journeys by bus, tram, metro, train, and ferry. Open source, no account, no ads.</em>
</p>

<p align="center">
  <a href="https://kotlinlang.org"><img alt="Kotlin 2.4.21" src="https://shieldcn.dev/badge/Kotlin-2.4.21-7f52ff.svg?variant=secondary&amp;logo=kotlin"></a>
  <a href="https://developer.android.com"><img alt="Android compile SDK 37" src="https://shieldcn.dev/badge/Android-SDK_37-3ddc84.svg?variant=secondary&amp;logo=android&amp;logoColor=171717"></a>
  <a href="https://m3.material.io"><img alt="Material 3 Expressive" src="https://shieldcn.dev/badge/Material_3-Expressive-6750a4.svg?variant=secondary"></a>
  <a href="https://gradle.org"><img alt="Gradle 9.9" src="https://shieldcn.dev/badge/Gradle-9.9-02303a.svg?variant=secondary&amp;logo=gradle"></a>
  <a href="https://github.com/kacigaya/itivio/blob/main/LICENSE"><img alt="GPL-3.0-or-later License" src="https://shieldcn.dev/github/license/kacigaya/itivio.svg?variant=secondary"></a>
</p>

## Features

- Map with your current location.
- Place and stop search with suggestions.
- Departures from stops near you, with live countdowns.
- Journey planning with all public transport modes. Leave now, leave at a time, or arrive by a time.
- Sort routes by duration, transfers, or walking distance.
- Journey details: stops, platforms, times, and the route on the map.
- Live times where the transit agency publishes them. Scheduled times have no live marker.
- Light and dark themes with Material You colors.
- Layout for phones, tablets, and foldables.

## Privacy

- No account, no analytics, no ads.
- The app does not log your journeys or your location.
- The app rounds the location it sends for search suggestions to about 1 km.
- Your location does not need Google Play services.
- The app turns off Android backup and device transfer for its data.

The app sends requests to these services:

| Service | Use | Data sent |
|---|---|---|
| [Transitous](https://transitous.org/) | Search, routes, departures | Search text, start and end points of a journey |
| [IGN Géoplateforme](https://cartes.gouv.fr/) | French address search | Search text, without your location |
| [OpenFreeMap](https://openfreemap.org/) | Map tiles | The map area you look at |

## Data sources

Routing comes from [Transitous](https://transitous.org/), a community service that runs [MOTIS](https://github.com/motis-project/motis). Transitous combines timetables from many transit agencies. See the [list of data sources](https://transitous.org/sources/).

French address search also uses IGN Géoplateforme and the [Base Adresse Nationale](https://adresse.data.gouv.fr/). Address-like queries go to IGN when the first Transitous match identifies France. IGN failures leave Transitous suggestions available.

Map data © [OpenStreetMap](https://www.openstreetmap.org/copyright) contributors. Map tiles from [OpenFreeMap](https://openfreemap.org/). The map styles derive from [OSM Liberty](https://github.com/maputnik/osm-liberty) (BSD 3-Clause code, CC BY design) with [OpenMapTiles](https://openmaptiles.org/) data.

Coverage differs by region. Some regions have no live data.

## Build

Requirements: JDK 21 and the Android SDK (compile SDK 37).

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

### APK builds on GitHub

1. Open [Build APK](https://github.com/kacigaya/itivio/actions/workflows/apk.yml).
2. Select **Run workflow** on `main`.
3. Wait for the build to pass.
4. Download `itivio-debug-apks` from the run's **Artifacts** section.
5. Extract the archive. Install the universal APK, or select the APK for your CPU type.

The workflow runs tests and lint before uploading the APKs. These are debug builds signed with a temporary debug key. Later builds can need a reinstall because the key can change. Artifacts expire after 14 days. A GitHub login is required to download them.

### Release builds

Run `./gradlew :app:assembleRelease` to build one APK per CPU type (`arm64-v8a`, `armeabi-v7a`, and `x86_64`) plus a universal APK.

Set all four environment variables to sign the release APKs:

- `ITIVIO_KEYSTORE`: path to the keystore file.
- `ITIVIO_KEYSTORE_PASSWORD`: keystore password.
- `ITIVIO_KEY_ALIAS`: signing key alias.
- `ITIVIO_KEY_PASSWORD`: signing key password.

You can also set `itivio.keystore`, `itivio.keystorePassword`, `itivio.keyAlias`, and `itivio.keyPassword` in `~/.gradle/gradle.properties`. Environment variables take priority. If any value is missing, the release APKs stay unsigned. Keep keys and passwords outside the repository.

### Transitous contact

Transitous asks each app to send contact details with every request. The app puts the `itivio.contact` Gradle property in its User-Agent. If you publish a fork, change the contact to your own:

```sh
./gradlew :app:assembleRelease -Pitivio.contact=you@example.com
```

Read the [Transitous usage policy](https://transitous.org/api/) before you publish a fork. The service is for open-source, non-commercial apps only.

## License

itivio is free software. You can redistribute it and change it under the terms of the [GNU General Public License](LICENSE), version 3 or any later version.
