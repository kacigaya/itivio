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
- Route lines on the map: transit legs in the line color with a `surface` casing. Walking legs are dotted in `onSurfaceVariant`. A walk that the map draws alone, as in the walking view, is dotted in `primary`.
- The position dot on the map uses `primary`.

## Type

Google Sans Flex on the Material type scale. The app bundles the variable font (`res/font/google_sans_flex.ttf`, SIL Open Font License 1.1, license text in `licenses/`). The theme sets the roundness axis (`ROND`) to 100.

Text pairs a heavy weight with a light weight:

- Numbers and names are heavy: durations, countdowns, distances, times, stop names, and line names use `Bold` or `ExtraBold`.
- Units and secondary text are light: "min", "km", "Towards", captions, and later departures use `Light`.
- `amountText` styles a formatted amount such as "45 min": heavy numbers, light units at a smaller size.
- A sentence with a bold part uses `<b>` markup in its string resource (`markedStringResource`), so the translator controls the word order.

## Shape and spacing

- Search bar: full. "Locate me" button: `largeIncreased` (20dp) with the height of the search bar (56dp), in `primaryContainer`, so it does not read as part of the bar. Route cards: `large` (16dp). Stop cards and transit leg cards: `largeIncreased`. Itinerary summary and walking instruction card: `extraLarge`. Bottom sheet: `extraLarge` top corners. Line badges and tags: full.

## Icons

Material Symbols Rounded, filled, as vector drawables. A row without a line color, such as a walk, shows its icon on a tonal tile (`IconTile`). A small fact, such as "1 transfer", is a tag: an icon and a label on a pill (`Tag`).
- 4dp grid. Screen margin 16dp (compact), 24dp (medium and up). Card padding 16dp.

## Motion

`MotionScheme.expressive()`. Sheet and card changes use the theme springs. The search view opens and closes with the default spatial spring, not the slow one. Screen changes use shared-axis fades. Loading uses the Expressive `LoadingIndicator`.

## Screens

| Screen | Compact | Medium and up |
|---|---|---|
| Home | Map, bottom sheet with the search bar and the "locate me" button at its top, the saved place buttons, then nearby departures | Map with sheet content in a 400dp side pane |
| Search | Full-screen search view: origin and destination fields, swap button, saved places or suggestions list | Same, in the side pane |
| Results | Sheet with a travel time button, an options button, sort buttons, and route cards. "Earlier departures" is above the cards and "Later departures" is below them. Map shows the selected route | Side pane with route cards |
| Itinerary | Sheet with leg timeline. Map fits the route | Side pane timeline |
| Stop | Sheet with the stop name, "Directions to here", and the departures. Map centers on the stop | Side pane |
| Walking | Instruction card at the top. Sheet with the remaining time, distance, and arrival, then all instructions. Map follows the user | Instruction card above the side pane |

## Navigation

The app has two top-level destinations: Map and Disruptions. Compact windows show a navigation bar below the content. Medium and wider windows show a navigation rail at the start. Only the two top-level screens show it: the results, the itinerary, the stop, the walking view, the settings, and the about screen have a back button. "Back" on Disruptions goes to the map.

The settings button is at the end of the search bar on home.

Disruptions, Settings, and About are full pages that cover the map. A page has a heavy title, and its content is at most 840dp wide.

## Saved places

A row of tonal buttons with filled icons sits below the search bar on home: Home, Work, then the favorites in the order the user added them. A tap on a button plans a journey to the place. When Home or Work is empty, the button reads "Set home" or "Set work". A tap opens the search view, and the picked place becomes the shortcut. The row keeps its height while storage loads.

With an empty query, the search view shows Home and Work, then "Favorites", then "Recent searches" with a "Clear" button. With a query, it shows the suggestions. Each place row has a menu button with three actions: add to or remove from favorites, set or remove as home, and set or remove as work. Clear needs no confirmation, because it removes only search history.

## Nearby departures

Departures within 400 m, on one card for each stop name. The cards are in order of their first departure. The card header shows the stop name and the straight-line distance to the nearest platform. A card has one row for each line and direction, not one row for each departure. Each row shows the line badge, headsign, platform, and the countdown to the next departure, with a heavy number and a light unit. A line below the countdown gives up to 3 later departures: "then 4 · 8 min". Departures 60 min or more away show the clock time. Live countdowns use the tertiary role and the live icon, the same as `TimeText`. A live "Now" is a `tertiary` pill. A screen reader also gets the clock time of the next departure. Without location access, the sheet shows an intro and an "Allow location" button. The list refreshes once a minute while it is visible, and when the user moves more than 150 m. Nearby stops show as small rings on the map.

## Travel time

The results filters are tonal toggle buttons, the same as the saved places on home. A checked button has the `secondary` color. The travel time button, before the sort buttons, opens a time picker dialog. The dialog has a segmented button (Leave at, Arrive by), day chips for the next 7 days, and the M3 time picker with a keyboard input toggle. "Leave now" resets to the current time. Times use the device time zone. The button shows as checked when the time is not "now".

## Journey options

The options button, after the travel time button, opens a dialog with the same layout as the travel time dialog. The button shows as checked when an option is not at its default.

- Transport modes: one filter chip for each mode, with the mode icon. A tap on the last selected mode changes nothing, and a line of text says why.
- Maximum transfers: single-choice chips (No limit, Direct, 1 to 3 transfers).
- Walking: a "Wheelchair accessible" switch with a text that gives its limits, then a segmented button for the walking speed (Slow, Normal, Fast). The wheelchair switch disables the walking speed, and a line of text says why.

The dialog edits a copy. "Done" applies all changes with one new search. "Reset" puts the copy back to the defaults. "Cancel" drops the changes.

## Earlier and later departures

"Earlier departures" and "Later departures" are outlined buttons at the two ends of the route cards. Each one shows only when the provider gives a cursor for that direction. Both are disabled while a page loads. The map keeps its route when a new page arrives.

## Itinerary

The itinerary is a trip tracker. The summary is a `primaryContainer` card with the duration as the largest text, the departure and arrival times, and the legs of the journey as a chain of badges. A status tag on the card shows "Leave in 4 min" before the departure and "Arrive in 12 min" during the journey. The tag is `tertiary` with the live icon when the journey has live times. Two more tags give the transfers and the walking distance.

Each transit leg is a `surfaceContainerHigh` card with the line color as a rail. A walk is a row with an icon tile, the distance in bold, and a "Directions" button. The step in progress has a `primary` outline. The service alerts of a leg are in one `errorContainer` banner: it shows the first alert and the number of the others, and a tap shows all of them. A tag gives the travel time of the leg. A second tag gives the number of stops; a tap shows or hides the stops.

Each transit leg shows the next 4 departures of its line at the boarding stop on a `surfaceContainerHighest` card. The list has only trips that stop at the alighting stop. Each row shows the trip code when the feed has one, then the destination: "QOKA52 · Marne-la-Vallée - Chessy". The planned trip is bold on a `tertiaryContainer` row. A live departure in the next hour shows as a countdown with the live icon. Other departures show the clock time. The line times refresh once a minute while the itinerary is visible.

## Disruptions

The page lists the service alerts in two groups: "Your saved stops" (Home, Work, and favorites that are stops), then "Near you" (stops within 400 m). An alert shows only one time, also when it applies to more than one stop or to both groups. The most severe disruptions come first in each group. The rating of the provider gives the severity. Without a rating, the effect gives it: "no service" is severe.

Each disruption is a card: a tag with the effect and the cause, the badges of the lines, the header, the time period, the stops, the description, and a "More information" button when the alert has a web link. A severe disruption uses `errorContainer`. The card shows 3 lines of the description; a tap shows all of it. The time period is the one in progress ("Until …") or the next one ("From … to …").

The empty state says that some regions publish no alerts. The list loads only while the page is on screen.

## Settings and about

Settings has four groups of rows on cards: appearance (theme as a segmented button with System, Light, and Dark; language, which opens the Android setting on Android 13 and later), journeys (the journey options dialog of the results), stored data (clear recent searches; delete all app data, in `error` color, with a confirmation dialog), and about.

About shows the version, the license, the source code link, the data credits, the font and icon licenses, and the privacy text. Each credit opens its web page.

## Stops on the map

On home, the map shows the nearby stops as pins: a disc in the color of the next line that leaves the stop, with the icon of its mode in the text color of the line. At zoom 15 and closer, it also shows the other stops in view as rings in `outline`. A tap on a pin or a ring opens the stop. Each one has a touch padding of 11dp or more. In the search, the menu of a stop has "Show departures" first.

## Stop

The departure board is one card with the rows of the nearby departures: one row for each line and direction. It refreshes once a minute while it is visible. "Directions to here" plans a journey to the stop.

## Walking view

"Directions" is a filled button on each walk step of the itinerary. Its accessibility label is "Walking directions". A walk-only route has one walk step.

- The instruction card uses `primaryContainer`. It shows the maneuver icon on a `primary` tile, the distance to the maneuver as the largest text, and the instruction. The start shows the length of the first part. A "Then" line shows the instruction after it.
- The card is a polite live region, so TalkBack reads a new instruction.
- Off the route, an `errorContainer` banner says so and gives "Plan a new route". Without location access, the banner gives "Allow location".
- The map follows the position and the heading. A gesture on the map stops this, and a "Recenter" button shows.
- The close button sits at the end of the summary row in the sheet, in `errorContainer`.
- A wavy progress bar below the summary shows the part of the walk that is done.
- The walk is dotted in `primary` on the map, with markers at its start and its end.

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
