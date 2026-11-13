# Architecture

itivio is one Android app module (`:app`). Packages give the layering. Split into Gradle modules only when build time or reuse needs it.

## Layers

```
ui/        Compose screens, ViewModels (StateFlow), navigation, theme
domain/    Plain Kotlin models and the RoutingRepository contract
data/      Transitous client, DTOs, mappers, caches, local storage, location
```

- `ui` depends on `domain`. It never imports `data` types.
- `data` implements `domain` interfaces. Koin binds them in `data/DataModule.kt`.
- `domain.PlacesRepository` holds saved places. `data/places` implements it with Room.
- `domain.JourneyOptionsRepository` holds the journey options. `data/options` implements it with DataStore.
- ViewModels are bound in `UiModule.kt`.

The planning session is in memory. The planner also keeps the minimal request in its `SavedStateHandle`: the origin, the destination, the travel time, and "arrive by". After process death, the planner reads the request and plans again. The results, the sort, and the selection are not saved. A request from "My location" waits up to 10 seconds for the first location fix. While the new plan loads, the itinerary and the walking view show a loading state. If the journey is not in the new results, the screen goes back to the results. Without a saved request, the screens of the session close. "Back" from the results deletes the request.

## Routing provider

`domain.RoutingRepository` is the only routing entry point. The Transitous implementation calls the MOTIS 2 API at `https://api.transitous.org/api/`:

| Feature | Endpoint |
|---|---|
| Autocomplete | `GET /v1/geocode` |
| Reverse geocode | `GET /v1/reverse-geocode` |
| Journey planning | `GET /v6/plan` |
| Departures | `GET /v6/stoptimes` |
| Departure board of a stop | `GET /v6/stoptimes` with `stopId` |
| Line times of an itinerary | `GET /v6/stoptimes` with `stopId` |
| Live times of an itinerary | `GET /v6/trip` |
| Map stops | `GET /v6/map/stops` |

To use a self-hosted MOTIS instance, change the base URL. To use another provider, add a new `RoutingRepository` implementation and bind it in `DataModule`.

## French address search

`IgnAddressSearch` supplements Transitous autocomplete with IGN Géoplateforme (BAN).
A query goes to IGN only when it looks like an address and the first Transitous match has country `FR`. This does not use the device language or a coordinate boundary to guess the country. If Transitous cannot identify France, search stays on Transitous. Add explicit country selection if this becomes a coverage problem.

IGN receives the query text, with no location bias or contact details. It returns up to five address matches. Results below score 0.5 are omitted. IGN addresses lead the merged list. Nearby address duplicates are removed; transit stops remain separate. The list keeps at most ten results. IGN has a five-second request timeout. A failure keeps the Transitous results and is not cached, so a later search can retry. Cancellation still propagates. Successful merged results use the existing memory cache.

The app footer links to IGN and credits BAN. No new library is needed.

## Saved places

Favorites, Home and Work, and recent searches are in one Room table, `saved_place`, in `places.db`. Each row has a role (`HOME`, `WORK`, `FAVORITE`, or `RECENT`) and a key. A place can have more than one role.

- The key is the stop ID for stops. For other places, the key is the kind, the provider ID, the name, and the point rounded to about 1 m. Geocoding IDs can be blank or repeated.
- Each row keeps a full copy of the place. A saved place needs no network request.
- Home and Work hold one place each.
- Recent searches keep the 10 newest places. A place picked from the suggestions or from the recent searches goes first. A pick of Home, Work, or a favorite does not change the recent searches.
- Schemas are exported to `app/schemas/`. A schema change needs a new version and a migration.

## Journey options

`domain.JourneyOptions` holds four options. Each `/v6/plan` request sends them as MOTIS parameters. An option at its default sends no parameter.

| Option | Parameter | Notes |
|---|---|---|
| Transport modes | `transitModes` | MOTIS cannot exclude a mode. The request lists all modes that stay on. |
| Transfer limit | `maxTransfers` | 0 to 3. `0` gives direct journeys only. |
| Wheelchair | `pedestrianProfile=WHEELCHAIR` and `useRoutedTransfers=true` | Without routed transfers, MOTIS applies the profile only to the first and last walk. |
| Walking speed | `pedestrianSpeed` | Slow is 0.9 m/s. Fast is 1.5 m/s. Normal sends nothing, so MOTIS uses 1.2 m/s. |

- The user can turn off nine modes: bus, coach, tram, metro, suburban train, train, high-speed train, ferry, and flight. One of them always stays on. Cable car, funicular, on-demand, and other modes have no switch and stay on.
- The walking speed is not sent with the wheelchair option. MOTIS then uses its wheelchair speed of 0.8 m/s.
- The wheelchair option changes walks and transfers only. MOTIS does not check if a vehicle is accessible. A region without routed transfer data uses the timetable transfers.
- The walking speed changes the first walk, the last walk, and walk-only routes. Transfer times between stops come from the timetable.

The options are in a Preferences DataStore file, `settings`. A value that the app does not know reads as its default. A damaged file is replaced with an empty one. The planner reads the options one time, before the first search. The options chip stays disabled until this read ends. A storage failure does not stop planning: the search uses the defaults, or the options of the current session.

"Earlier departures" and "Later departures" use the page cursors of the plan response. The request of a page is the same as the first request, with `pageCursor` added. Each direction continues from the cursor of its last page. Only one page loads at a time.

Limits:

- The options have one editor, the dialog on the results. The planner does not observe the store after the first read. Observe it when a second editor exists, such as the settings screen.
- The rare modes have no switch. Add one if users must exclude them.

## Journeys without transit

MOTIS returns walk-only journeys in `direct`, apart from `itineraries`. The mapper puts them after the transit journeys, so "Best" keeps the order of the provider. Their ID is blank, so the mapper derives one from the legs.

## Walking view

`domain.WalkRoute` makes turn-by-turn instructions from the steps of a walk (`steps` in the plan response). It needs no network request.

- Providers often give `CONTINUE` for every step and no street name. The turns come from the geometry: the bearing over 15 m before and after each step start. A change of 35° or more is a turn. One corner shows at several step starts near it, so only the sharpest start of a run on the same side counts.
- Stairs and elevators get their own instruction. A turn that the provider gives is kept.
- A turn less than 10 m after another turn joins it as one bend. A bend that comes out straight is dropped. A turn in the first or last 10 m is dropped.
- The street name of an instruction is the name that covers at least a third of it. Without one, the text names only the maneuver ("Turn left"). The start names the compass direction ("Head northwest").
- The position is projected on the nearest part of the route. More than 50 m from the route counts as off the route. The app does not plan a new route by itself.
- The remaining time uses the speed of the walk in the plan: its distance divided by its duration.

Limits: the projection picks the nearest part of the route, so a route that passes the same place twice can jump. The view keeps the screen on and works only while the app is on screen. The app asks for no background location.

## Transitous usage policy

- Every request sends `User-Agent: itivio/<version> (+<contact>)`. Set the contact with the `itivio.contact` Gradle property.
- The UI shows a link to `https://transitous.org/sources/`. The info button on the map opens the OpenStreetMap, OpenMapTiles, and OpenFreeMap credits. The credits start closed, the same as the default MapLibre Android control.
- Autocomplete waits for typing to pause, needs 2 or more characters, and cancels stale requests.
- `/plan` runs only on explicit user action. No background polling. The journey options dialog sends one request when the user confirms, not one for each change.
- `/stoptimes` refreshes once a minute, only while the nearby list is on screen and the app is in the foreground. A move of less than 150 m does not send a new request.
- The itinerary sends one `/stoptimes` request for each transit leg, once a minute, only while the itinerary is on screen and the app is in the foreground.
- The itinerary also sends one `/trip` request for each transit leg that has not ended, once a minute, under the same conditions.
- The departure board sends one `/stoptimes` request once a minute, only while the board is on screen and the app is in the foreground.
- `/map/stops` runs only on the home screen, at zoom 15 or closer, 0.5 seconds after the camera stops. The request asks for the view rounded out to a 0.01° grid. A view inside the area already loaded sends no request. A view wider than 0.05° sends no request.
- The walking view sends no request. "Plan a new route" sends one `/plan` request.
- Geocode results are cached in memory.

## Privacy

- No analytics, no crash reporting, no request logging.
- French address queries also go to IGN. No coordinates are sent to IGN.
- The search bias point is rounded to about 1 km before it is sent.
- The nearby departures center is rounded to about 10 m before it is sent.
- The origin of a journey is sent at full precision only when the user plans from their location.
- The walking view uses the device position on the device. Only "Plan a new route" sends it, as the origin of a new journey.
- The compass runs only while the walking view is open.
- Favorites and recent searches stay on the device. Backup and device transfer are off.
- Journey options stay on the device. Transitous gets them only as parameters of a journey request.
- Location comes from the Android framework provider. Google Play services is not required.

## Real-time data

`StopTime.estimated` is set only when the provider reports live data. The UI marks live times. It shows scheduled times without a live marker. Coverage differs by region.

The itinerary tracker uses the clock and the leg times. It does not use the device position. While the itinerary is open, `TripTrackerViewModel` asks `/v6/trip` for the trip of each transit leg once a minute. `Journey.withTrip` finds the boarding stop by stop ID and scheduled departure, so a loop trip boards at the correct visit. It then finds the alighting stop after it. It updates the times, platforms, stops, cancellation, and alerts of the leg. The walks after the leg move with its arrival. A trip that does not contain the stops of the leg changes nothing. A failed request keeps the last times. The planned journey in the results does not change.

`/stoptimes` has no line filter. The line times come from the next 40 departures of the boarding stop, filtered by route ID. A stop with many lines can show fewer than 4 times.

The line times keep only trips that stop at the alighting stop, or at another platform of its station. The request sets `fetchStops` to get the next stops of each trip. The direction ID is not enough: IDFM gives direction 0 to RER A trains in both directions. `fetchStops` is experimental in MOTIS. If the response has no next stops, the direction ID filters the list. The next stops make each refresh about 5 times larger (about 40 kB compressed at La Défense).

Some feeds put a mission code in the headsign, such as "QOKA" for trip "QOKA52" on the RER. When the trip short name starts with the headsign, the mapper shows the final stop of the trip (`tripTo`) and keeps the trip short name as `tripCode`. Other headsigns stay as they are, because `tripTo` names can have a city suffix, such as "U Turmstr. (Berlin)".

## Stack

Kotlin, Jetpack Compose, Material 3 Expressive, Navigation 3, MapLibre Compose with OpenFreeMap tiles, Ktor (OkHttp engine) with kotlinx.serialization, Koin, coroutines with StateFlow. Room stores saved places. DataStore stores the journey options.
