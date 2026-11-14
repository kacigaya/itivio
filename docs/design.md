# Design direction

Material 3 Expressive on Jetpack Compose. The map is the canvas. The UI floats on it in tonal surfaces.

## Principles

1. Map first. Search, results, and itineraries sit on a bottom sheet over the map. The map always shows the context.
2. One primary action per screen. The search bar on home, at the top of the sheet in reach of the thumb. The route cards on results. The itinerary steps on detail.
3. Live data must be clear. Live times use the `tertiary` role and a live icon. Scheduled times use `onSurfaceVariant`. A delay shows the struck-through scheduled time beside the live time.
4. Line colors come from the agency. Line badges use the agency color. They keep its text color when it meets 4.5:1 contrast. Otherwise they use black or white, whichever has more contrast. When the feed has no color, badges use `secondaryContainer`.

## Color

- Dynamic color on Android 12+. Fallback scheme: tonal spot from seed `#1B6B5A`.
- Map style: OpenFreeMap Liberty in the light theme. The dark theme uses `assets/map/dark.json`: the Liberty layers with dark colors, so both themes show the same roads, stops, and places. `tools/dark-map-style.py` builds this file.
- Route lines on the map: transit legs in the line color with a `surface` casing. Walking legs are dotted in `onSurfaceVariant`. A walk that the map draws alone is dotted in `primary`. The walking view draws round dots: the part ahead in `primary` with `surface` rings, the part walked in `outline`.
- The position dot on the map uses `primary` with a `surface` border, the same as the casing of the stop pins. A white border merges with the dot in a dark scheme that has a light `primary`.

## Type

Google Sans Flex on the Material type scale. The app bundles the variable font (`res/font/google_sans_flex.ttf`, SIL Open Font License 1.1, license text in `licenses/`). The theme sets the roundness axis (`ROND`) to 100.

Text pairs a heavy weight with a light weight:

- Numbers and names are heavy: durations, countdowns, distances, times, stop names, and line names use `Bold` or `ExtraBold`.
- Units and secondary text are light: "min", "km", "Towards", captions, and later departures use `Light`.
- `amountText` styles a formatted amount such as "45 min": heavy numbers, light units at a smaller size.
- A sentence with a bold part uses `<b>` markup in its string resource (`markedStringResource`), so the translator controls the word order.

## Shape and spacing

- Search bar: full. "Locate me" button: `largeIncreased` (20dp) with the height of the search bar (56dp), in `primaryContainer`, so it does not read as part of the bar. Route cards: `large` (16dp). Stop cards and transit leg cards: `largeIncreased`. Itinerary summary and walking instruction card: `extraLarge`. Side pane (wide windows): `extraLarge` (28dp). Bottom sheet: top corners with the radius of the screen corners (Android 12 and later), otherwise `extraLarge`. Line badges and tags: full.
- A card nested in the corner of a container has the radius of the container minus the margin between them, so the two curves stay parallel: `inner = outer - margin`. The itinerary summary in the side pane is 16dp from each edge, so its radius is 28 - 16 = 12dp. Leave the same margin on the top and the sides.

## Icons

Material Symbols Rounded, filled, as vector drawables. A row without a line color, such as a walk, shows its icon on a tonal tile (`IconTile`). A small fact, such as "1 transfer", is a tag: an icon and a label on a pill (`Tag`).
- 4dp grid. Screen margin 16dp (compact), 24dp (medium and up). Card padding 16dp.

## Motion

`MotionScheme.expressive()`. Sheet and card changes use the theme springs. The search view opens and closes with the default spatial spring, not the slow one. Screen changes use shared-axis fades. A list that loads shows a skeleton. An action in progress shows the Expressive `LoadingIndicator`. See "States".

### Back gesture

The app shows a preview during a back gesture, as in the Android [predictive back guidance](https://developer.android.com/design/ui/mobile/guides/patterns/predictive-back). The preview follows the finger. A release before the commit point puts the screen back.

- Between screens: the screen on top becomes a card, as in the system settings. The card shrinks to 90%, gets the corners of the screen, and moves away from the edge of the gesture. It stays 8dp from the other edge. The screen below and the map get the Material scrim, up to 32%. A release fades the card out and removes the scrim. On a map screen, the sheet and its header form the card.
- Disruptions page of the home sheet: the Departures page comes into view by 30% of its width.
- Search view, dialogs, and menus: the Material components supply their own behavior.
- Home: the system shows the launcher behind the app.

The preview uses the easing of the system back animations, `(0.1, 0.1, 0, 1)`. The card does not follow the vertical position of the finger. The back button of a screen and three-button navigation use the usual fade.

## Screens

| Screen | Compact | Medium and up |
|---|---|---|
| Home | Map, bottom sheet with the search bar and the "locate me" button at its top, the saved place buttons, then two pages: nearby departures and disruptions | Map with sheet content in a 400dp side pane. The pane has only the nearby departures |
| Search | Full-screen search view: origin and destination fields, swap button, saved places or suggestions list | Same, in the side pane |
| Results | Sheet with a travel time button, an options button, sort buttons, and route cards. The row of buttons scrolls to the two edges of the sheet. "Earlier departures" is above the cards and "Later departures" is below them. Map shows the selected route | Side pane with route cards |
| Itinerary | Sheet with leg timeline. Map fits the route | Side pane timeline |
| Stop | Sheet with the stop name, "Directions to here", and the departures. Map centers on the stop | Side pane |
| Walking | Instruction card at the top. Sheet with the remaining time, distance, and arrival, then all instructions. Map follows the user | Instruction card above the side pane |

## Navigation

A compact window has no navigation bar. Its home sheet has two pages, Departures and Disruptions. The two page titles are tabs above the list: a swipe on the list or a tap on a title changes the page. The title of the page on screen has the `onSurface` color, and the other title has `outline`. A badge on "Disruptions" gives the number of alerts. "Back" on the Disruptions page goes to the Departures page. The map, the search bar, and the saved places stay in place.

Medium and wider windows have two top-level destinations, Map and Disruptions, on a navigation rail at the start. Only these two screens show the rail: the results, the itinerary, the stop, the walking view, the settings, and the about screen have a back button. "Back" on Disruptions goes to the map. The change to the home pages for wide windows is deferred.

The settings button is at the end of the search bar on home.

Settings and About are full pages that cover the map. On a wide window, Disruptions is a full page too. A page has a heavy title, and its content is at most 840dp wide.

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
- Maximum transfers: one row of five connected buttons: an infinity icon for no limit, then 0 to 3. A line of text below the row gives the choice in words. A screen reader gets the full name of each button.
- Walking: a "Wheelchair accessible" switch with its icon and a text that gives its limits, then a row of connected buttons for the walking speed (Slow, Normal, Fast). Each speed has one, two, or three arrows. The wheelchair switch disables the walking speed, and a line of text says why.

A row of connected buttons (`ChoiceButtons`) is the single-choice control of the app. The selected button has the `secondary` color and a round shape, the same as a checked filter button. It has no check mark. At 150% text size and more, a row with icons and labels becomes a vertical radio list, so each label stays readable.

The dialog has no switch for flights. No journey uses a flight.

The dialog edits a copy. "Done" applies all changes with one new search. "Reset" puts the copy back to the defaults. "Cancel" drops the changes.

## Earlier and later departures

"Earlier departures" and "Later departures" are text buttons with an arrow icon at the two ends of the route cards. They are text buttons so the route cards stay the main content. Each one shows only when the provider gives a cursor for that direction. Both are disabled while a page loads. The button of the page that loads shows a loading indicator in place of its arrow, and a screen reader gets "Loading" as its state. The map keeps its route when a new page arrives.

## Itinerary

The itinerary is a trip tracker. The summary is a `primaryContainer` card with the duration as the largest text, the departure and arrival times, and the legs of the journey as a chain of badges. A status tag on the card shows "Leave in 4 min" before the departure and "Arrive in 12 min" during the journey. The tag is `tertiary` with the live icon when the journey has live times. Two more tags give the transfers and the walking distance.

Each transit leg is a `surfaceContainerHigh` card with the line color as a rail. A walk is a row with an icon tile, the distance in bold, and a "Directions" button. The step in progress has a `primary` outline. The service alerts of a leg are in one `errorContainer` banner: it shows the first alert and the number of the others, and a tap shows all of them. A tag gives the travel time of the leg. A second tag gives the number of stops; a tap shows or hides the stops.

Each transit leg shows the next 4 departures of its line at the boarding stop as a list of connected rows on `surfaceContainerHighest`, with a 2dp gap. The list has only trips that stop at the alighting stop. Each row shows the destination, not the trip code. Below it are the platform, the arrival time at the alighting stop, and the time on board: "Platform 2 · → 20:13 · 29 min". The arrival time comes with the departures, so it needs no other request. The clock time of the departure is the heavy number at the end of the row. A live departure in the next hour also shows its countdown with the live icon. A late train shows its planned time struck through above the new time, and its delay in words with a warning icon. The row of the selected train has the `secondary` color, a round shape, and a "Your train" label. On this row, the delay is a pill in `errorContainer`, because `error` text has too little contrast on `secondary`. The line times refresh once a minute while the itinerary is visible. The scan starts when the platform is reachable and pages past other lines. A departure row has the radio role for a screen reader, but shows no radio button. Selecting a train checks its trip and updates the tracked leg. A changed leg has a Reset button. The leg header shows the line badge and the direction, with no trip code and no operator name. A warning explains a missed onward connection or cleared walking directions after a platform change. See `train-selection.md` for the limits.

When the data gives one, a "Where to board" row sits below the line and the direction. It is a `surfaceContainerHighest` card with the part of the train in heavy text ("At the front"), the reason ("Transfer" and the badge of the next line, or "Near the exit"), and a drawing of a train of three parts. The rear is on the left and the front, with its nose, is on the right, in the direction of travel. The part to board uses `primary`; the other parts use `outline`. The text names the part, so color is not the only cue. For an exit, the reason names the exit number ("Near exit 3"). A screen reader gets one sentence.

When the data knows the exits of the alighting platform, a "Best exit" row sits below the alighting stop: an exit icon, the label, and the exit number and name in bold ("3 – r. M. Chasles"). It shows also when the exit has no single part of the train. See `architecture.md` for the data and its limits.

The rail and the stop dots draw on the same whole pixel, so the rail has no step below a dot.

The itinerary shows departure skeletons until all eligible legs finish their first departure response and the boarding hints are ready. The hints and departures then appear together. Refreshes keep the displayed times. An empty or failed first response completes that leg’s loading state.

## Disruptions

The list shows the service alerts in two groups: "Your saved stops" (Home, Work, and favorites that are stops), then "Near you" (stops within 400 m). An alert shows only one time, also when it applies to more than one stop or to both groups. The most severe disruptions come first in each group. The rating of the provider gives the severity. Without a rating, the effect gives it: "no service" is severe.

Each disruption is a card: a tag with the effect and the cause, the badges of the lines, the header, the time period, the stops, the description, and a "More information" button when the alert has a web link. A severe disruption uses `errorContainer`. The card shows 3 lines of the description; a tap shows all of it. The time period is the one in progress ("Until …") or the next one ("From … to …").

The empty state says that some regions publish no alerts. On a compact window, the list is the second page of the home sheet, without the page title and the scope text. It loads while home is open, because the home title shows the number of alerts. On a wide window, the list is a full page, and it loads only while the page is on screen. See `architecture.md` for the requests.

## Settings and about

At larger text sizes, the theme choices use a vertical radio group so each label stays readable. Each choice keeps its icon.

Settings has four groups of rows on cards: appearance (theme as a row of connected buttons with an icon and a label: System, Light, and Dark; language, which opens the Android setting on Android 13 and later), journeys (the journey options dialog of the results), stored data (clear recent searches; delete all app data, in `error` color, with a confirmation dialog), and about.

About shows the version, the license, the source code link, the data credits, the font and icon licenses, and the privacy text. Each credit opens its web page.

## Stops on the map

On home, the map shows the nearby stops as pins: a disc in the color of the next line that leaves the stop, with the icon of its mode in the text color of the line. At zoom 15 and closer, it also shows the other stops in view as rings in `outline`. A tap on a pin or a ring opens the stop. Each one has a touch padding of 11dp or more. In the search, the menu of a stop has "Show departures" first.

## Stop

The departure board is one card with the rows of the nearby departures: one row for each line and direction. It refreshes once a minute while it is visible. "Directions to here" plans a journey to the stop.

## Walking view

"Directions" is a filled button on each walk step of the itinerary. Its accessibility label is "Walking directions". A walk-only route has one walk step.

- The instruction card uses `primaryContainer`. It shows the maneuver icon on a `primary` tile, the distance to the maneuver as the largest text, and the instruction. The start shows the length of the first part.
- A "Then" tab hangs below the card, at its start, in the same color. It shows the word and the icon of the next maneuver. Its accessibility label gives the full instruction ("Then: Turn left").
- The card is a polite live region, so TalkBack reads a new instruction.
- Off the route, an `errorContainer` banner says so and gives "Plan a new route". Without location access, the banner gives "Allow location".
- The map is tilted and turns with the walker. The position is an arrow on a `primary` disc that lies flat on the map. The position sits low on the screen, so the walker sees the way ahead. Within 20 m of the route, the arrow sits on the route line.
- The arrow and the map turn to the heading of the device. Without a heading, they turn to the route ahead.
- A round compass button below the card turns the map between "north up" (flat) and "turn with you". Its arrow points north, in `error` red.
- A gesture on the map stops the camera, and a "Recenter" button shows.
- The summary row in the sheet has the close button at the start, in `errorContainer`. In the middle are the remaining time and, below it, the distance and the arrival time. At the end, a tonal button shows the full route; the "Recenter" button then shows.
- A wavy progress bar below the summary shows the part of the walk that is done.
- The walk has markers at its start and its end.

## Route card

```
12:04 – 12:31                         27 min
walk 4 › [U2] › [M41]
12:08 Alexanderplatz
1 transfer · 450 m walk
⚠ Diversion at Potsdamer Platz        [+1]
```

The departure and arrival times are heavy. The leg chain shows a badge for each line. A walk is an icon and its minutes, with no container. A first or last walk shows from 2 min. A walk between two lines shows from 5 min. The itinerary shows all walks. The itinerary summary uses the same chain.

The alert line shows the first alert of the journey on one line, and the number of the others on an `errorContainer` pill. It shows only when the journey has an alert.

The card of the route on the map uses `secondaryContainer`. It has no outline.

## States

Each list has a loading, empty, offline, and error state. Offline and error states give a retry button.

The loading state of a list is a skeleton (`Skeleton`): placeholders with the layout of the content to come, so the list does not jump when the content arrives.

- The results show three route cards. The nearby departures show two stop cards. The stop shows one departure board. The disruptions show a group title and two cards. The search shows five place rows. Each itinerary leg with a boarding stop shows three train rows under "Next departures".
- A placeholder card has the container color and the shape of the real card. Its blocks use `onSurface` at 8% to 16% opacity, on all card colors.
- A text block has the line height of its text style, so the skeleton grows with the text size.
- The blocks of one list pulse in step, 900 ms for each direction. They do not pulse when system animations are off.
- A screen reader gets one "Loading" label for the list, not one for each block.
- The skeleton of a component is next to the component in the source. Change the two together.

The itinerary loads the boarding hints and the first line times of all legs at the same time. They appear together: the skeletons fade out, and the boarding rows, exit rows, and train lists fade in as their space opens, with the springs of the theme. Live times do not reset the shown departures.

The Expressive loading indicator is for an action in progress on content that is on screen: a train change, and a page of earlier or later journeys. It is also the loading state when the layout to come is not known, as when the session plans again after process death.

## Accessibility

- Touch targets 48dp or more.
- Line badges have content descriptions ("Bus M41").
- Live status is in text, not only color.
- All strings live in `res/values/strings.xml`.

Automated checks cover visible click-action names, text contrast, and panel screenshots at 100% and 200% text size. The manual TalkBack check remains open. See `quality.md`.
