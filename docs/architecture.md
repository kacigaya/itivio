# Architecture

itivio is one Android app module (`:app`). Packages give the layering. Split into Gradle modules only when build time or reuse needs it.

## Layers

```
ui/        Compose screens, ViewModels (StateFlow), navigation, theme
domain/    Plain Kotlin models and the RoutingRepository contract
data/      Transitous client, DTOs, mappers, caches, local storage
```

- `ui` depends on `domain`. It never imports `data` types.
- `data` implements `domain` interfaces. Koin binds them in `data/DataModule.kt`.
- `domain.PlacesRepository` holds saved places. `data/places` implements it with Room.
- `domain.JourneyOptionsRepository` holds the journey options. `data/options` implements it with DataStore.
- `domain.SettingsRepository` holds the app settings. `domain.AppData` deletes all stored data. `data/settings` implements both.
- Device location lives in `ui/common/UserLocation.kt` and the MapLibre provider in `ItivioRoot.kt`.
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
| Disruptions | `GET /v6/stoptimes` with `withAlerts=true` |
| Line times of an itinerary | `GET /v6/stoptimes` with `stopId` |
| Live times of an itinerary | `GET /v6/trip` |
| Map stops | `GET /v6/map/stops` |

To use a self-hosted MOTIS instance, change the base URL. To use another provider, add a new `RoutingRepository` implementation and bind it in `DataModule`.

## French address search

`IgnAddressSearch` supplements Transitous autocomplete with IGN Géoplateforme (BAN).
A query goes to IGN only when it looks like an address and the first Transitous match has country `FR`. This does not use the device language or a coordinate boundary to guess the country. If Transitous cannot identify France, search stays on Transitous. Add explicit country selection if this becomes a coverage problem.

IGN receives the query text, with no location bias or contact details. It returns up to five address matches. Results below score 0.5 are omitted. IGN addresses lead the merged list. Nearby address duplicates are removed; transit stops remain separate. The list keeps at most ten results. IGN has a five-second request timeout. A failure keeps the Transitous results and is not cached, so a later search can retry. Cancellation still propagates. Successful merged results use the existing memory cache.

Settings → About links to IGN and credits BAN. It also contains the French address search privacy notice. No new library is needed.

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
| Transport modes | `transitModes` | MOTIS cannot exclude a mode. The request lists all modes that stay on. Each request sends this list, because it also keeps flights out. |
| Transfer limit | `maxTransfers` | 0 to 3. `0` gives direct journeys only. |
| Wheelchair | `pedestrianProfile=WHEELCHAIR` and `useRoutedTransfers=true` | Without routed transfers, MOTIS applies the profile only to the first and last walk. |
| Walking speed | `pedestrianSpeed` | Slow is 0.9 m/s. Fast is 1.5 m/s. Normal sends nothing, so MOTIS uses 1.2 m/s. |

- The user can turn off eight modes: bus, coach, tram, metro, suburban train, train, high-speed train, and ferry. One of them always stays on. Cable car, funicular, on-demand, and other modes have no switch and stay on. Flights have no switch and stay off (`JourneyOptions.UNUSED_MODES`): no journey uses a flight.
- The walking speed is not sent with the wheelchair option. MOTIS then uses its wheelchair speed of 0.8 m/s.
- The wheelchair option changes walks and transfers only. MOTIS does not check if a vehicle is accessible. A region without routed transfer data uses the timetable transfers.
- The walking speed changes the first walk, the last walk, and walk-only routes. Transfer times between stops come from the timetable.

The options are in a Preferences DataStore file, `settings`. A value that the app does not know reads as its default. A damaged file is replaced with an empty one. The planner reads the options one time, before the first search. The options chip stays disabled until this read ends. A storage failure does not stop planning: the search uses the defaults, or the options of the current session.

The planner sends one plan request for each search and does not page. The travel time button sets the time of the request.

The options have two editors: the dialog on the results and the settings screen. The planner observes the store after the first read, so a change in the settings applies to the next search. A change in the settings starts no search.

Limit: the rare modes have no switch. Add one if users must exclude them.

## Settings and stored data

The `settings` DataStore file holds the journey options and the theme (`ThemeMode`: system, light, or dark). `DataModule` binds one `DataStore` instance for the file; two instances for one file fail. `MainActivity` reads the theme before the first frame, so the app does not flash the wrong theme. The icons of the system bars and the map style follow the theme of the app.

The language uses the per-app language setting of Android (`locales_config.xml`). The app has no language picker of its own. Each request reads the language at call time, so a change applies without a restart.

"Delete all app data" (`domain.AppData`) deletes all rows of `saved_place` and clears the `settings` file. The search cache, the live data cache, and the planning session are in memory only and end with the process.

## Departure reminders

`domain.ReminderScheduler` holds one reminder at a time, on the device. The itinerary offers "When it is time to leave" and 2, 5, or 10 minutes before the departure (`ReminderScheduler.LEAD_MINUTES`). While the itinerary is on screen, the live times of the trip move the reminder (`update`). A new time does not set a reminder again after it went off, and a reminder whose time has passed is neither shown nor set.

`AlarmReminderScheduler` sets an `AlarmManager` alarm with `setExactAndAllowWhileIdle` when `canScheduleExactAlarms()` allows it, and with `setAndAllowWhileIdle` otherwise, including when Android refuses the exact call with a `SecurityException`, so a reminder is never lost. An inexact alarm can come a few minutes late in doze. Android 14 and later deny exact alarms by default, and the reminder menu then offers `Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM`.

The reminder is stored in the `settings` DataStore file: the journey ID, the alarm time, the lead time, and the notification text as it was written when the reminder was set. The keys need no migration; a file without them holds no reminder. `ReminderReceiver` shows the notification and reads no data of the app. A reboot clears every alarm, so `ReminderBootReceiver`, on the `BOOT_COMPLETED` broadcast (`RECEIVE_BOOT_COMPLETED`), sets the stored reminder again; a reminder that passed while the device was off is dropped. The app asks for `POST_NOTIFICATIONS` before it sets a reminder and sets none when the rider refuses, because a reminder that shows nothing is not a reminder.

Limits: one reminder at a time. "Delete all app data" clears the `settings` file, which removes it.

## Disruptions

MOTIS has no endpoint that lists the disruptions of a network or a line. Alerts come with stop times: with `withAlerts=true`, each stop time carries the alerts for its trip at that stop. The disruptions list sends:

- One `/v6/stoptimes` request with `stopId` and 20 departures for each saved stop: Home, Work, then the favorites. Saved places that are not stops are not sent. The tab asks for at most 10 stops.
- One `/v6/stoptimes` request around the device position, the same as the nearby departures, when the position is known.

`domain.disruptionsOf` makes one `Disruption` for each alert, with all the stops and lines that carry it. The nearby group leaves out the alerts of the saved group.

The mapper reads the severity, the effect, the cause, and the impact periods. Many feeds give no severity; `Alert.level` then uses the effect. A value that the app does not know reads as "unknown". The description can contain HTML. The app shows its text and follows no link in it. The "More information" button opens only `http` and `https` links.

A request that fails leaves out its stop. The tab shows an error only when no request of the first load gives an answer. The stop departure board also asks with `withAlerts=true`, so it can show the alerts of the stop. Other screens keep `withAlerts=false`.

Limits:

- The tab shows only the disruptions on the stops it asks about, and only for trips among the next departures. A line with no departure soon shows no alert.
- The app does not filter on the communication period of an alert. The provider sends the alerts that are current.
- The tab does not ask for location access. Without it, only the saved stops show.

## Boarding positions

The RATP publishes the train part nearest to each transfer and exit for metro platforms and some RER platforms. Île-de-France Mobilités hosts "Positionnement dans la rame" under the Licence Ouverte 2.0. Its second dataset, "Accès" (`acces`), gives exit numbers, names, and coordinates under the same Licence Ouverte 2.0. Transitous has no boarding data.

`tools/boarding-positions.py` downloads these datasets with a generic User-Agent. It writes offline tables in `app/src/main/assets/boarding/`. `idfm.tsv` holds the platform, line, target (a transfer stop ID or `exit:<access ID>`), train part, and lift flag. `exits.tsv` holds only referenced exits with coordinates: ID, optional number, name, latitude, and longitude. `stations.tsv` maps boarding and transfer platforms to their stop areas and parent stations. It comes from the IDFM "Arrêts" (`arrets`) and "Zones d'arrêts" (`zones-d-arrets`) datasets under the Licence Ouverte 2.0. The tool rounds coordinates to six decimals and reports exits without coordinates. Run it again when the datasets change.

`IdfmBoardingRepository` loads the three files once, off the main thread, on first use. It sends no request. It matches the IDFM suffix of Transitous stop and route IDs (`…_IDFM:22194`, `…_IDFM:C01384`). The boarding table also gives the line IDs of the transfer platforms.

An exact arrival platform match takes priority. When the arrival stop ID names a stop area or parent station, the repository uses only named exits listed for every mapped platform of the same line in that area or station. These exits have no train part, because the arrival platform and train orientation are unknown. A specific platform ID with no data does not fall back to its parent station. Station matches give no transfer positions. The exit nearest to the destination can still show without a boarding hint.

`domain.hints` uses the next transit leg as the transfer target. An exact target platform match takes priority. If the target ID names a stop area or parent station, the rule uses the listed transfer platforms for the next line in that area or station. Unknown line membership gives no station match. A transfer gets a train part only when its positions agree. For example, Metro 9 at Nation gives "middle" for a transfer to RER A, even when the RER stop ID names the station. An unlisted transfer gets no hint. A known platform with no match does not fall back to its parent station. For the last transit leg, the rule chooses the exit nearest to the final journey destination. It uses only that exit's positions to choose the train part. The hint keeps the exit even when those positions disagree. Without exit coordinates, all exit positions must agree. Wheelchair routing prefers lift rows. Other routing prefers rows without lifts. If the preferred rows are absent, all rows for the target count.

Limits: the nearest exit is a guess. It may differ from the exit that the walk uses. Exit choice does not check street access or lift availability. Station-level arrivals show no train part. They also show no exit when the mapped platforms have no named exit in common. Unmapped stations remain unsupported. The dataset has no RER A or B positions at Châtelet.

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
- The map cuts the walk at the projected position (`WalkRoute.split`): the part walked and the part ahead. Off the route, nothing counts as walked.
- The arrow and the camera turn to the heading or the course that MapLibre measures. Without one, they turn to the route ahead of the position (`WalkRoute.directionAt`). One camera update sets the center, zoom, bearing, and pitch, because a second animation cancels the first.

Limits: the projection picks the nearest part of the route, so a route that passes the same place twice can jump. The arrow and the camera show the measured position and never snap to the route: a snap can move the walker to the other side of the street. Instead, the walk line moves sideways to the walker: `WalkProgress.rightMeters` is the signed distance from the nearest part of the route, and the map uses it, clamped to 15 m and rounded to whole metres, as a zoom-scaled MapLibre `line-offset`. The whole line moves by the same distance, so a walker who crosses the street later sees the line follow them, not the crossing ahead. The instructions and the walked part use the nearest point of the route. Off the route, the banner shows. The off-route limit is the accuracy of the fix, from 15 m (`WalkRoute.MIN_OFF_ROUTE_METERS`) to 50 m (`WalkRoute.OFF_ROUTE_METERS`); a fix without accuracy uses 50 m. A walker who moves at 0.5 m/s or faster in a direction more than 60° from the route, both here and 15 m ahead, is off the route after 10 m (`WalkRoute.LEAVING_METERS`). The direction comes from the GPS course of the fix, not the compass. Once off the route, the walker stays off it until they are back within 10 m, so a stop to read the banner does not clear it. The app root computes this state once, and the map and the walking view both use it. A GPS course that is wrong while the walker is 10 m or more from the route shows the banner until the walker is back within 10 m.

Off the route, `WalkRerouteViewModel` plans a walk from the position to the end of the walk leg and replaces the leg in the walking view. It reuses the journey request of Transitous and takes the first walk-only journey, so it keeps the journey options. It sends at most one request every 20 seconds (`WalkRerouteViewModel.MIN_INTERVAL`) while the walker stays off the route. A failed request or a plan without a walk keeps the leg. Requests run only while the app is started; a request still waiting is cancelled when the walker is back on the route, leaves the walking view, or puts the app in the background. The new walk is kept in memory only, for the walk it replaces, and only while the end of that walk (stop and point) and the journey options stay the same: a new boarding platform drops it; the rest of the journey does not change, so a longer walk can miss a connection. The view keeps the screen on and works only while the app is on screen. The app asks for no background location.

## Transitous usage policy

- Every request sends `User-Agent: itivio/<version> (+<contact>)`. Set the contact with the `itivio.contact` Gradle property.
- Settings → About shows the Transitous and IGN/BAN credits and links to `https://transitous.org/sources/`. Other screens have no provider footer. The info button on the map opens the OpenStreetMap, OpenMapTiles, and OpenFreeMap credits. The credits start closed, the same as the default MapLibre Android control.
- Autocomplete waits for typing to pause, needs 2 or more characters, and cancels stale requests.
- `/plan` runs only on explicit user action, except for "Travel time to Home and Work". No background polling. The journey options dialog sends one request when the user confirms, not one for each change.
- The only background requests come from "Disruption alerts", which the rider turns on. `DisruptionAlertWorker` is a WorkManager periodic task (30 min, network required). It asks `/v6/stoptimes` with `withAlerts=true` for each saved stop, the same requests as the disruptions list, and notifies alerts whose text key (`Alert.notificationKey`, a digest of the header and the description) it has not seen. It stores the keys of the current alerts in the `settings` DataStore, so an alert that ends is forgotten. Only the alerts it showed count as seen: the ones over the cap of three notifications wait for the next check, and a denied notification permission leaves them all for later. A check that gets no answer from any stop ends as a success, so the next period tries again instead of backing off for hours against a community server. Turning the setting off or "Delete all app data" cancels the task. The journey options dialog sends one request when the user confirms, not one for each change.
- The volume of the background check: one `/stoptimes` request for each saved stop, at most 10, for 20 departures each, so at most 10 requests per 30 minutes and about 480 per day if the rider saves 10 stops. Each answer holds the alerts of those departures. The work requires a network (`NetworkType.CONNECTED`), not an unmetered one, because an alert is only useful soon and one answer is under a few hundred kB; Android also batches the periodic work into a maintenance window.
- With "Travel time to Home and Work" turned on (off by default), `ShortcutTimesViewModel` sends two `/plan` requests while home is open: from the device position to Home, and to Work. They go out when home opens, and after a move of 150 m, at most once every 20 seconds (`ShortcutTimesViewModel.MIN_INTERVAL`). A new place or a new option plans at once. The requests run only while home is on screen and the app is in the foreground.
- `/stoptimes` refreshes once a minute, only while the nearby list is on screen and the app is in the foreground. A move of less than 150 m does not send a new request.
- The itinerary scans up to eight `/stoptimes` pages for each transit leg, once a minute, only while the itinerary is on screen and the app is in the foreground. It stops when it has four matching departures.
- The itinerary also sends one `/trip` request for each transit leg that has not ended, once a minute, under the same conditions.
- The departure board sends one `/stoptimes` request once a minute, only while the board is on screen and the app is in the foreground.
- The disruptions list sends one `/stoptimes` request for each saved stop (at most 10) and one for the device position, once every five minutes, only while the app is in the foreground. On a compact window, the list is a page of the home sheet, and the home title shows the number of alerts. The requests then go out while home is open, not only while the list is on screen. On a wide window, they go out only while the Disruptions page is on screen.
- `/map/stops` runs only on the home screen, at zoom 15 or closer, 0.5 seconds after the camera stops. The request asks for the view rounded out to a 0.01° grid. A view inside the area already loaded sends no request. A view wider than 0.05° sends no request.
- The walking view sends no request. "Plan a new route" sends one `/plan` request.
- Geocode results are cached in memory.
- Journey pages, departure boards (one stop or nearby), line times, and trips are cached in memory for 30 seconds: less than the shortest refresh (one minute), so each refresh asks again. A screen that opens again shows the last answer with no request. The disruptions read the same boards. A journey page is for one request and language. A nearby board is for the position rounded as sent (4 decimals). A line-times entry is for one stop, line, direction, alighting stop, language, and exact start time. A failure is not cached, so "Try again" always asks.

## Privacy

- No analytics, no crash reporting, no request logging.
- French address queries also go to IGN. No coordinates are sent to IGN.
- The search bias point is rounded to about 1 km before it is sent.
- The nearby departures center is rounded to about 10 m before it is sent. The disruptions list uses the same rounding.
- The disruptions list sends the IDs of the saved stops. Otherwise, saved places go to Transitous only as the start or the end of a journey request. That includes the two requests of "Travel time to Home and Work", which send the position at full precision and do not wait for a tap.
- The origin of a journey is sent at full precision only when the user plans from their location, including the opt-in travel times above.
- The walking view uses the device position on the device. It sends it to Transitous in two cases: "Plan a new route", as the origin of a new journey, and an automatic new walk when the walker is off the route, at most once every 20 seconds.
- The compass runs only while the walking view is open.
- The walking view asks for the best fix twice a second and a compass reading five times a second. Other screens use the library standard: one fix a second and one compass reading a second.
- Favorites and recent searches stay on the device. Backup and device transfer are off.
- A departure reminder stays on the device: an `AlarmManager` alarm and a notification, with no request. The stored reminder holds the journey ID, its time, and the notification text.
- Journey options stay on the device. Transitous gets them only as parameters of a journey request.
- Location comes from the Android framework provider. Google Play services is not required.

## Real-time data

`StopTime.estimated` is set only when the provider reports live data. The UI marks live times. It shows scheduled times without a live marker. Coverage differs by region.

The itinerary tracker uses the clock and the leg times. It does not use the device position. While the itinerary is open, `TripTrackerViewModel` asks `/v6/trip` for the trip of each transit leg once a minute. `Journey.withTrip` finds the boarding stop by stop ID and scheduled departure, so a loop trip boards at the correct visit. It then finds the alighting stop after it. It updates the times, platforms, stops, cancellation, and alerts of the leg. The walks after the leg move with its arrival. A trip that does not contain the stops of the leg changes nothing. A failed request keeps the last times. The planned journey in the results does not change.

`/stoptimes` has no line filter. The line times scan pages of 80 departures from the boarding stop, filtered by route ID. A refresh stops after four matching departures, the last page, or eight pages. This bound can still leave fewer than four times at very busy stations or on lines with little service. Duplicate events and repeated cursors do not extend the scan. A later page failure keeps the matches already found.

The line times publish each leg as its request completes. An eligible leg with no entry is still waiting for its first response. A present but empty entry means the first request completed without times, including a failed request. The itinerary uses this distinction for its initial loading indicator. Refreshes keep the last times visible. A leg without a boarding stop ID makes no request and shows no loading state.

The first leg lists departures after the remaining initial walk, starting now. It does not inherit the wait that the planner put before that walk. Later legs start after the previous transit arrival and the transfer walk.

The line times keep only trips that stop at the alighting stop, or at another platform of its station. The request sets `fetchStops` to get the next stops of each trip. The direction ID is not enough: IDFM gives direction 0 to RER A trains in both directions. `fetchStops` is experimental in MOTIS. If the response has no next stops, the direction ID filters the list. The next stops make each refresh about 5 times larger (about 40 kB compressed at La Défense).

Some feeds put a mission code in the headsign, such as "QOKA" for trip "QOKA52" on the RER. When the trip short name starts with the headsign, the mapper shows the final stop of the trip (`tripTo`) and keeps the trip short name as `tripCode`. Other headsigns stay as they are, because `tripTo` names can have a city suffix, such as "U Turmstr. (Berlin)".

## Stack

Kotlin, Jetpack Compose, Material 3 Expressive, Navigation 3, MapLibre Compose with OpenFreeMap tiles, Ktor (OkHttp engine) with kotlinx.serialization, Koin, coroutines with StateFlow. Room stores saved places. DataStore stores the journey options. WorkManager runs the opt-in disruption check in the background.
