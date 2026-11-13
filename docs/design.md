# Design direction

Material 3 Expressive on Jetpack Compose. The map is the canvas. The UI floats on it in tonal surfaces.

## Principles

1. Map first. Search, results, and itineraries sit on a bottom sheet over the map. The map always shows the context.
2. One primary action per screen. The search bar on home, at the top of the sheet in reach of the thumb. The route cards on results. The itinerary steps on detail.
3. Live data must be clear. Live times use the `tertiary` role and a live icon. Scheduled times use `onSurfaceVariant`. A delay shows the struck-through scheduled time beside the live time.
4. Line colors come from the agency. Line badges use the agency color and text color. When the feed has no color, badges use `secondaryContainer`.

## Color

- Dynamic color on Android 12+. Fallback scheme: tonal spot from seed `#1B6B5A`.
- Map style: OpenFreeMap Liberty in the light theme. The dark theme uses `assets/map/dark.json`: the Liberty layers with dark colors, so both themes show the same roads, stops, and places. `tools/dark-map-style.py` builds this file.
- Route lines on the map: transit legs in the line color with a `surface` casing. Walking legs are dotted in `onSurfaceVariant`.

## Type

Default Material type scale (Roboto). Times and durations use `titleLarge` / `titleMedium` with tabular figures. Emphasized styles mark the selected route.

## Shape and spacing

- Search bar: full. "Locate me" button: a circle with the height of the search bar (56dp). Route cards: `large` (16dp). Bottom sheet: `extraLarge` top corners. Line badges: `small` (8dp).
- 4dp grid. Screen margin 16dp (compact), 24dp (medium and up). Card padding 16dp.

## Motion

`MotionScheme.expressive()`. Sheet and card changes use the theme springs. The search view opens and closes with the default spatial spring, not the slow one. Screen changes use shared-axis fades. Loading uses the Expressive `LoadingIndicator`.

## Screens

| Screen | Compact | Medium and up |
|---|---|---|
| Home | Map, bottom sheet with the search bar and the "locate me" button at its top, the saved place chips, then nearby departures | Map with sheet content in a 400dp side pane |
| Search | Full-screen search view: origin and destination fields, swap button, saved places or suggestions list | Same, in the side pane |
| Results | Sheet with a travel time chip, an options chip, sort chips, and route cards. "Earlier departures" is above the cards and "Later departures" is below them. Map shows the selected route | Side pane with route cards |
| Itinerary | Sheet with leg timeline. Map fits the route | Side pane timeline |
| Stop | Sheet with the stop name, "Directions to here", and the departures. Map centers on the stop | Side pane |
| Walking | Instruction card at the top. Sheet with the remaining time, distance, and arrival, then all instructions. Map follows the user | Instruction card above the side pane |

## Saved places

A row of assist chips sits below the search bar on home: Home, Work, then the favorites in the order the user added them. A tap on a chip plans a journey to the place. When Home or Work is empty, the chip reads "Set home" or "Set work". A tap opens the search view, and the picked place becomes the shortcut. The row keeps its height while storage loads.

With an empty query, the search view shows Home and Work, then "Favorites", then "Recent searches" with a "Clear" button. With a query, it shows the suggestions. Each place row has a menu button with three actions: add to or remove from favorites, set or remove as home, and set or remove as work. Clear needs no confirmation, because it removes only search history.

## Nearby departures

Departures within 400 m, grouped by stop name. Groups are in order of their first departure. Each row shows the line badge, headsign, platform, and a countdown with the clock time. Departures 60 min or more away show only the clock time. Live countdowns use the tertiary role and the live icon, the same as `TimeText`. Without location access, the sheet shows an intro and an "Allow location" button. The list refreshes once a minute while it is visible, and when the user moves more than 150 m. Nearby stops show as small rings on the map.

## Travel time

A filter chip before the sort chips opens a time picker dialog. The dialog has a segmented button (Leave at, Arrive by), day chips for the next 7 days, and the M3 time picker with a keyboard input toggle. "Leave now" resets to the current time. Times use the device time zone. The chip shows as selected when the time is not "now".

## Journey options

A filter chip after the travel time chip opens a dialog with the same layout as the travel time dialog. The chip shows as selected when an option is not at its default.

- Transport modes: one filter chip for each mode, with the mode icon. A tap on the last selected mode changes nothing, and a line of text says why.
- Maximum transfers: single-choice chips (No limit, Direct, 1 to 3 transfers).
- Walking: a "Wheelchair accessible" switch with a text that gives its limits, then a segmented button for the walking speed (Slow, Normal, Fast). The wheelchair switch disables the walking speed, and a line of text says why.

The dialog edits a copy. "Done" applies all changes with one new search. "Reset" puts the copy back to the defaults. "Cancel" drops the changes.

## Earlier and later departures

"Earlier departures" and "Later departures" are outlined buttons at the two ends of the route cards. Each one shows only when the provider gives a cursor for that direction. Both are disabled while a page loads. The map keeps its route when a new page arrives.

## Itinerary

The itinerary is a trip tracker. A status line above the summary shows "Leave in 4 min" before the departure and "Arrive in 12 min" during the journey. The step in progress has a `primary` outline. Each transit leg shows the next 4 departures of its line at the boarding stop on a `surfaceContainerHigh` card. The list has only trips that stop at the alighting stop. Each row shows the trip code when the feed has one, then the destination: "QOKA52 · Marne-la-Vallée - Chessy". The planned trip is in medium weight. A live departure in the next hour shows as a countdown with the live icon. Other departures show the clock time. The line times refresh once a minute while the itinerary is visible.

## Stops on the map

On home, the map shows the nearby stops as rings in `primary`. At zoom 15 and closer, it also shows the other stops in view as rings in `outline`. A tap on a ring opens the stop. Each ring has a 12dp touch padding. In the search, the menu of a stop has "Show departures" first.

## Stop

The departure board uses the rows of the nearby departures. It refreshes once a minute while it is visible. "Directions to here" plans a journey to the stop.

## Walking view

"Walking directions" is an outlined button on each walk step of the itinerary. A walk-only route has one walk step.

- The instruction card uses `primaryContainer`. It shows the maneuver icon, the distance to the maneuver, and the instruction. The start shows the length of the first part. A "Then" line shows the instruction after it.
- The card is a polite live region, so TalkBack reads a new instruction.
- Off the route, an `errorContainer` banner says so and gives "Plan a new route". Without location access, the banner gives "Allow location".
- The map follows the position and the heading. A gesture on the map stops this, and a "Recenter" button shows.
- The close button sits at the start of the summary row in the sheet.
- The walk is dotted on the map, with markers at its start and its end.

## Route card

```
[12:04 → 12:31]                       27 min
[walk 4] › [U2] › [walk] › [M41]   ●Live
Departs 12:08 from Alexanderplatz · 1 transfer · 450 m walk
⚠ Diversion at Potsdamer Platz · 1 more alert
```

The alert line shows the first alert of the journey and the number of the others. It shows only when the journey has an alert.

## States

Each list has a loading, empty, offline, and error state. Offline and error states give a retry button. Loading uses the Expressive loading indicator, not skeleton boxes.

## Accessibility

- Touch targets 48dp or more.
- Line badges have content descriptions ("Bus M41").
- Live status is in text, not only color.
- All strings live in `res/values/strings.xml`.
