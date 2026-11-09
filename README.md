# Itivio

Itivio is a public transport app for Android. It plans journeys by bus, tram, metro, train, and ferry. It is open source and keeps your data on your device.

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
| [OpenFreeMap](https://openfreemap.org/) | Map tiles | The map area you look at |

## Data sources

Routing comes from [Transitous](https://transitous.org/), a community service that runs [MOTIS](https://github.com/motis-project/motis). Transitous combines timetables from many transit agencies. See the [list of data sources](https://transitous.org/sources/).

Map data © [OpenStreetMap](https://www.openstreetmap.org/copyright) contributors. Map tiles from [OpenFreeMap](https://openfreemap.org/).

Coverage differs by region. Some regions have no live data.

## Build

Requirements: JDK 21 and the Android SDK (compile SDK 37).

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

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

Itivio is free software. You can redistribute it and change it under the terms of the [GNU General Public License](LICENSE), version 3 or any later version.
