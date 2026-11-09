# Architecture

Itivio is one Android app module (`:app`). Packages give the layering. Split into Gradle modules only when build time or reuse needs it.

## Layers

```
ui/        Compose screens, ViewModels (StateFlow), navigation, theme
domain/    Plain Kotlin models and the RoutingRepository contract
data/      Transitous client, DTOs, mappers, caches, local storage, location
```

- `ui` depends on `domain`. It never imports `data` types.
- `data` implements `domain` interfaces. Koin binds them in `data/DataModule.kt`.
- ViewModels are bound in `UiModule.kt`.

## Routing provider

`domain.RoutingRepository` is the only routing entry point. The Transitous implementation calls the MOTIS 2 API at `https://api.transitous.org/api/`:

| Feature | Endpoint |
|---|---|
| Autocomplete | `GET /v1/geocode` |
| Reverse geocode | `GET /v1/reverse-geocode` |
| Journey planning | `GET /v6/plan` |
| Departures | `GET /v6/stoptimes` |
| Map stops | `GET /v6/map/stops` |

To use a self-hosted MOTIS instance, change the base URL. To use another provider, add a new `RoutingRepository` implementation and bind it in `DataModule`.

## Transitous usage policy

- Every request sends `User-Agent: Itivio/<version> (+<contact>)`. Set the contact with the `itivio.contact` Gradle property.
- The UI shows a link to `https://transitous.org/sources/` and the OpenStreetMap attribution.
- Autocomplete waits for typing to pause, needs 2 or more characters, and cancels stale requests.
- `/plan` runs only on explicit user action. No background polling.
- `/stoptimes` refreshes once a minute, only while the nearby list is on screen and the app is in the foreground. A move of less than 150 m does not send a new request.
- Geocode results are cached in memory.

## Privacy

- No analytics, no crash reporting, no request logging.
- The search bias point is rounded to about 1 km before it is sent.
- The nearby departures center is rounded to about 10 m before it is sent.
- The origin of a journey is sent at full precision only when the user plans from their location.
- Favorites and recent searches stay on the device. Backup and device transfer are off.
- Location comes from the Android framework provider. Google Play services is not required.

## Real-time data

`StopTime.estimated` is set only when the provider reports live data. The UI marks live times. It shows scheduled times without a live marker. Coverage differs by region.

## Stack

Kotlin, Jetpack Compose, Material 3 Expressive, Navigation 3, MapLibre Compose with OpenFreeMap tiles, Ktor (OkHttp engine) with kotlinx.serialization, Koin, coroutines with StateFlow. Room and DataStore come with the favorites milestone.
