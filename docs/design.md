# Design direction

Material 3 Expressive on Jetpack Compose. The map is the canvas. The UI floats on it in tonal surfaces.

## Principles

1. Map first. Search, results, and itineraries sit on a bottom sheet over the map. The map always shows the context.
2. One primary action per screen. The search bar on home, at the top of the sheet in reach of the thumb. The route cards on results. The itinerary steps on detail.
3. Live data must be clear. Live times use the `tertiary` role and a live icon. Scheduled times use `onSurfaceVariant`. A delay shows the struck-through scheduled time beside the live time.
4. Line colors come from the agency. Line badges use the agency color. They keep its text color when it meets 4.5:1 contrast. Otherwise they use black or white, whichever has more contrast. When the feed has no color, badges use `secondaryContainer`.

## Color

- Dynamic color on Android 12+. Fallback scheme: tonal spot from seed `#1B6B5A`.
- Map style: the OpenFreeMap Liberty layers with the colors of each theme, in `assets/map/light.json` and `assets/map/dark.json`, so both themes show the same roads, stops, and places. `tools/map-style.py` builds both files. Both drop the shaded relief. The light theme has calm neutral land (`#eef1ee`), white streets on grey casings, a pale warm tint on major roads, and muted blue-grey water (`#b9d5e4`). Labels are grey with white halos; stop labels use the `tertiary` hue. Thus the line colors of routes and stops are the strongest colors on the map, and the map matches the cool tonal surfaces of the app.
- Route lines on the map: transit legs in the line color with a `surface` casing. Walking legs are dotted in `onSurfaceVariant`. A walk that the map draws alone is dotted in `primary`. The walking view draws round dots: the part ahead in `primary` with `surface` rings, the part walked in `outline`.
- The position dot on the map uses `primary` with a `surface` border, the same as the casing of the stop pins. A white border merges with the dot in a dark scheme that has a light `primary`.

## Launch screen

The Android 12+ splash screen shows the walking pin (the launcher foreground) on a `#1B6B5A` brand icon background, in the icon shape of the device. Its background is the fallback `surface` of the theme (`#F5FBF7` light, `#0E1513` dark), and the window background before the first frame has the same color on all versions. Thus the launch has no white flash. The splash follows the theme of the device: the window starts before the app reads its own theme setting. `res/values/colors.xml` holds the colors; `Theme.kt` keeps the full schemes.

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

`MotionScheme.expressive()`. Sheet and card changes use the theme springs. The search view opens and closes with the default spatial spring, not the slow one. A list that loads shows a skeleton. An action in progress shows the Expressive `LoadingIndicator`. See "States".

### Screen changes

The screen on top is a card. The same motion serves a new screen, the back button, and the back gesture.

- A new screen comes up from the bottom edge in 450 ms, with the Material emphasized decelerate easing. A spring would go past the rest position and show the map below the card.
- "Back" sends the card down in 300 ms, with the emphasized accelerate easing.
- Between two full-screen pages, the lower page stays opaque throughout the transition and keeps the map covered. The wide-layout Disruptions page slides over the steady map pane. In other transitions with a map screen, the lower screen fades during the first half of the transition.
- The first set of results flies to the route. Sorting moves the cards first, then the map fits the new route in one step. When Back leaves walking directions, the map fits the itinerary in one step during the screen change.
- On a map screen, the card is the sheet or the side pane. The header and the map buttons of a compact window move with the card, but they show only in the last eighth of the way. Thus they fade in near their position and do not cross the map.
- A full-screen page gets the top corners of the screen while it moves.
- "Back" on the results resets the journey search at once. The card keeps its last content while it goes down.
- When system animations are off, the screens change at once.

`ScreenMotion.kt` holds this motion. NavDisplay gives only the direction and the time.

### Back gesture

The app shows a preview during a back gesture, as in the Android [predictive back guidance](https://developer.android.com/design/ui/mobile/guides/patterns/predictive-back). The preview follows the finger. A release before the commit point puts the screen back.

- Between screens: the card on top goes down by up to 15% of the screen height. If either screen shows the map, the screen below fades in. A release sends the card down below the edge of the screen. The content of the card does not update during the gesture.
- Disruptions page of the home sheet: the Departures page comes into view by 30% of its width.
- Travel time and journey options sheets: the sheet goes down by up to 15% of the screen height, and the scrim fades out. A release sends the sheet down below the edge of the screen in the time and easing of "Back" between screens.
- Search view, dialogs, and menus: the Material components supply their own behavior.
- Home: the system shows the launcher behind the app.

The preview uses the easing of the system back animations, `(0.1, 0.1, 0, 1)`. The card does not follow the edge or the vertical position of the finger.

## Screens

| Screen | Compact | Medium and up |
|---|---|---|
| Home | Map, bottom sheet with the search bar and the "locate me" button at its top, the saved place buttons, then two pages: nearby departures and disruptions | Map with sheet content in a 400dp side pane. The pane has only the nearby departures |
| Search | Full-screen search view: origin and destination fields, swap button, saved places or suggestions list | Same, in the side pane |
| Results | Sheet with a travel time button, an options button, sort buttons, and route cards, one card for each route. The row of buttons scrolls to the two edges of the sheet. The travel time button is the only control of the time: the results have no "earlier" or "later" buttons. Map shows the selected route | Side pane with route cards |
| Itinerary | Sheet with leg timeline. Map fits the route | Side pane timeline |
| Stop | Sheet with the stop name, "Directions to here", the favorite toggle, and the departures. Map centers on the stop | Side pane |
| Walking | Instruction card at the top. Sheet with the remaining time, distance, and arrival, then all instructions. Map follows the user | Instruction card above the side pane |

## Navigation

A compact window has no navigation bar. Its home sheet has two pages, Departures and Disruptions. The two page titles are tabs above the list: a swipe on the list or a tap on a title changes the page. The title of the page on screen has the `onSurface` color, and the other title has `outline`. A badge on "Disruptions" gives the number of alerts. "Back" on the Disruptions page goes to the Departures page. The map, the search bar, and the saved places stay in place.

Medium and wider windows have two top-level destinations, Map and Disruptions, on a navigation rail at the start. Only these two screens show the rail: the results, the itinerary, the stop, the walking view, the settings, and the about screen have a back button. "Back" on Disruptions goes to the map. The change to the home pages for wide windows is deferred.

The settings button is at the end of the search bar on home.

Settings and About are full pages that cover the map. On a wide window, Disruptions is a full page too. A page has a heavy title, and its content is at most 840dp wide.

## Saved places

A row of tonal buttons with filled icons sits below the search bar on home: Home, Work, then the favorites in the order the user added them. When "Travel time to Home and Work" is on in the settings (off by default), Home and Work add the time from now to the first arrival in a light weight ("Home · 32 min"). It needs a location fix; a failed request leaves out the time, and so does a time under a minute, so standing at Home shows no "0 min". A screen reader gets "Travel time to Home: 32 min" instead of the separator. A tap on a button plans a journey to the place. When Home or Work is empty, the button reads "Set home" or "Set work". A tap opens the search view, and the picked place becomes the shortcut. The row keeps its height while storage loads.

The search view lists its rows in groups, in the M3 Expressive list style: `surfaceContainerHigh` rows on `surfaceContainerLow`, 2dp apart, with 20dp outer corners and 4dp inner corners. With an empty query, the first group is "My location", Home, and Work. An empty Home or Work shows "Set home" or "Set work" with an outlined icon. A tap on it makes the next pick the shortcut: the placeholder asks for the place, and the search stays open, so the rider sees the new row. Then come "Favorites" and "Recent searches" with a "Clear" button. With a query, it shows the suggestions. The part of each name that matches the query is bold. A stop row names its modes after the area ("Paris 12e · Metro · Bus"), so stops with the same name differ. Each place row has a menu button with three actions: add to or remove from favorites, set or remove as home, and set or remove as work. Clear needs no confirmation, because it removes only search history.

## Nearby departures

Departures within 400 m, on one card for each stop name. The cards are in order of their first departure. The card header shows the stop name and the straight-line distance to the nearest platform. A card has one row for each line and direction, not one row for each departure. Each row shows the line badge, headsign, platform, and the countdown to the next departure, with a heavy number and a light unit. A line below the countdown gives up to 3 later departures: "then 4 · 8 min". Departures 60 min or more away show the clock time. A live countdown sits on a `tertiaryContainer` pill with the live icon, so it is the strongest number on its row. A timetable countdown is plain, in `onSurfaceVariant`. A live "Now" is a `tertiary` pill. A screen reader also gets the clock time of the next departure. Without location access, the sheet shows an intro and an "Allow location" button. The list refreshes once a minute while it is visible, and when the user moves more than 150 m. Nearby stops show as small rings on the map.

## Travel time

The results filters are tonal toggle buttons, the same as the saved places on home. A checked button has the `secondary` color. The travel time button, before the sort buttons, opens a time picker bottom sheet. The sheet has a row of connected buttons (Leave at, Arrive by), the same as the walking speed, day chips for the next 7 days, and the M3 Expressive scroll time picker with a keyboard input toggle. "Leave now" resets to the current time. Times use the device time zone. The button shows as checked when the time is not "now".

## Journey options

The options button, after the travel time button, opens a bottom sheet with the same layout as the travel time sheet. Both sheets come up from the bottom edge, and a swipe down or a tap outside closes them without changes. "Done", "Cancel", and "Leave now" send the sheet down in the time and easing of "Back" between screens, then apply their action. The button shows as checked when an option is not at its default.

- Transport modes: one filter chip for each mode, with the mode icon. The chips are in a grid of equal columns: three on a 411 dp phone, fewer on a narrow screen or with large text. Each chip has one line with a short name ("Suburban", "Express"; in French "Banlieue", "TGV"). A screen reader gets the full name. The chips of a row have one height, at least 48 dp, with 8 dp between rows. A tap on the last selected mode changes nothing, and a line of text says why.
- Maximum transfers: one row of five connected buttons: an infinity icon for no limit, then 0 to 3. A line of text below the row gives the choice in words. A screen reader gets the full name of each button.
- Walking: a "Wheelchair accessible" switch with its icon and a text that gives its limits, then a row of connected buttons for the walking speed (Slow, Normal, Fast). Each speed has one, two, or three arrows. The wheelchair switch disables the walking speed, and a line of text says why.

A row of connected buttons (`ChoiceButtons`) is the single-choice control of the app. The selected button has the `secondary` color and a round shape, the same as a checked filter button. It has no check mark. At 150% text size and more, a row with icons and labels becomes a vertical radio list, so each label stays readable.

The dialog has no switch for flights. No journey uses a flight.

The dialog edits a copy. "Done" applies all changes with one new search. "Reset" puts the copy back to the defaults. "Cancel" drops the changes.

## Itinerary

The itinerary is a trip tracker. The summary is a `primaryContainer` card with the duration as the largest text, the departure and arrival times, and the legs of the journey as a chain of badges. A status tag on the card shows "Leave in 4 min" before the departure and "Arrive in 12 min" during the journey. The tag is `tertiary` with the live icon when the journey has live times. Two more tags give the transfers and the walking distance.

Each transit leg is a `surfaceContainerHigh` card with the line color as a rail. A walk is a row with an icon tile, the distance in bold, and a "Directions" button. The step in progress has a `primary` outline. The service alerts of a leg are in one `errorContainer` banner: it shows the first alert and the number of the others, and a tap shows all of them. A tag gives the travel time of the leg. A second tag gives the number of stops; a tap shows or hides the stops.

Each transit leg shows the next 4 departures of its line at the boarding stop as a list of connected rows on `surfaceContainerHighest`, with a 2dp gap. The list has only trips that stop at the alighting stop. Each row shows the destination, not the trip code. Below it are the platform, the arrival time at the alighting stop, and the time on board: "Platform 2 · → 20:13 · 29 min". The arrival time comes with the departures, so it needs no other request. The clock time of the departure is the heavy number at the end of the row. A live departure in the next hour also shows its countdown with the live icon. A late train shows its planned time struck through above the new time, and its delay in words with a warning icon. The row of the selected train has the `secondary` color, a round shape, and a "Your train" label. On this row, the delay is a pill in `errorContainer`, because `error` text has too little contrast on `secondary`. The line times refresh once a minute while the itinerary is visible. The scan starts when the platform is reachable and pages past other lines. A departure row has the radio role for a screen reader, but shows no radio button. Selecting a train checks its trip and updates the tracked leg. Each later leg then takes the first train it can still catch; see `train-selection.md`. A changed leg has a Reset button. The leg header shows the line badge and the direction, with no trip code and no operator name. A warning explains a missed onward connection or cleared walking directions after a platform change. See `train-selection.md` for the limits.

When the data gives one, a "Where to board" row sits below the line and the direction. It is a `surfaceContainerHighest` card with the part of the train in heavy text ("At the front"), the reason ("Transfer" and the badge of the next line, or "Near the exit"), and a drawing of a train of three parts. The rear is on the left and the front, with its nose, is on the right, in the direction of travel. The part to board uses `primary`; the other parts use `outline`. The text names the part, so color is not the only cue. For an exit, the reason names the exit number ("Near exit 3"). A screen reader gets one sentence.

When the data knows the exits of the alighting platform, a "Best exit" row sits below the alighting stop: an exit icon, the label, and the exit number and name in bold ("3 – r. M. Chasles"). It shows also when the exit has no single part of the train. See `architecture.md` for the data and its limits.

The rail and the stop dots draw on the same whole pixel, so the rail has no step below a dot.

The itinerary shows departure skeletons until all eligible legs finish their first departure response and the boarding hints are ready. The hints and departures then appear together. Refreshes keep the displayed times. An empty or failed first response completes that leg’s loading state.

## Disruptions

The list shows the service alerts in two groups: "Your saved stops" (Home, Work, and favorites that are stops), then "Near you" (stops within 400 m). An alert shows only one time, also when it applies to more than one stop or to both groups. The most severe disruptions come first in each group. The rating of the provider gives the severity. Without a rating, the effect gives it: "no service" is severe.

Each disruption is a `surfaceContainerHigh` card: a tag with the effect, the badges of the lines, the header, the time period and the cause ("From Sunday, 3:45 AM · Works"), the stops, the description, and a "More information" button when the alert has a web link. A severe disruption has `errorContainer` only on its header band, with the tag in `error`. Its body keeps the card colors, so the long text stays easy to read. Within the next week a date is the weekday ("Sunday, 3:45 AM"). Later dates this year have no year. The weekday reads well in the "From" and "Until" sentences of each language; "today" does not in French. The card shows 3 lines of the description; a tap shows all of it. The time period is the one in progress ("Until …") or the next one ("From … to …").

The empty state says that some regions publish no alerts. On a compact window, the list is the second page of the home sheet, without the page title and the scope text. It loads while home is open, because the home title shows the number of alerts. On a wide window, the list is a full page, and it loads only while the page is on screen. See `architecture.md` for the requests.

## Settings and about

At larger text sizes, the theme choices use a vertical radio group so each label stays readable. Each choice keeps its icon.

Settings has four groups of rows on cards: appearance (theme as a row of connected buttons with an icon and a label: System, Light, and Dark; language, which opens the Android setting on Android 13 and later), journeys (the journey options dialog of the results), stored data (clear recent searches; delete all app data, in `error` color, with a confirmation dialog), and about.

About shows the version, the license, the source code link, the data credits, the font and icon licenses, and the privacy text. Each credit opens its web page.

## Stops on the map

On home, the map shows the nearby stops as pins: a disc in the color of the next line that leaves the stop, with the icon of its mode in the text color of the line. At zoom 15 and closer, it also shows the other stops in view as rings in `outline`. A tap on a pin or a ring opens the stop. Each one has a touch padding of 11dp or more. In the search, the menu of a stop has "Show departures" first.

## Stop

The departure board is one card with the rows of the nearby departures: one row for each line and direction. It refreshes once a minute while it is visible. "Directions to here" is a filled button: it is the one main action of the sheet. It plans a journey to the stop. A star toggle button beside it adds the stop to the favorites or removes it, so the stop shows in "Your saved stops" of the disruptions. A saved stop has a `secondary` button, the checked color of the app. The toggle is disabled while storage loads.

## Walking view

"Directions" is a filled button on each walk step of the itinerary. Its accessibility label is "Walking directions". A walk-only route has one walk step.

- The instruction card uses `primaryContainer`. It shows the maneuver icon on a `primary` tile, the distance to the maneuver as the largest text, and the instruction. The start shows the length of the first part.
- A "Then" tab hangs below the card, at its start, in the same color. It shows the word and the icon of the next maneuver. Its accessibility label gives the full instruction ("Then: Turn left").
- The card is a polite live region, so TalkBack reads a new instruction.
- Off the route, an `errorContainer` banner says so and gives "Plan a new route". The walk is replaced by a new walk from the position by itself; the banner then goes. Without location access, the banner gives "Allow location".
- The map is tilted and turns with the walker. The position is an arrow on a `primary` disc that lies flat on the map. The position sits low on the screen, so the walker sees the way ahead. The arrow shows the measured position and does not snap to the route line, so it stays on the correct side of the street. The routes follow street centre lines, so the walk line moves to the side of the walker, up to 15 m, and passes under the arrow. With a good GPS fix, a wrong turn shows after 10 to 15 m; with a poor fix, after up to 50 m.
- The arrow and the map turn to the heading of the device. Without a heading, they turn to the route ahead.
- A round compass button at the bottom start of the map, above the sheet, turns the map between "north up" (flat) and "turn with you". Its arrow points north, in `error` red.
- A gesture on the map stops the camera, and a "Recenter" button shows below the compass button.
- The summary row in the sheet has the close button at the start, in `surfaceContainerHighest`. Ending a walk is normal, so it is not red: red is for the off-route banner. In the middle are the remaining time and, below it, the distance and the arrival time. At the end, a tonal button shows the full route; the "Recenter" button then shows.
- A wavy progress bar below the summary shows the part of the walk that is done.
- The walk has markers at its start and its end.

## Route card

```
∙∙∙ 4 › [U2] › [M41]                  26 min
∙∙∙∙  ━━━━━━━━  ∙∙∙  ━━━━━━━━
(•) 4 min  then 9 · 14 min  from Alexanderplatz
Arrive at 12:31 · 1 transfer · 450 m walk
⚠ Diversion at Potsdamer Platz        [+1]
```

A card is a route, not one departure. Journeys that use the same lines, boarded at the same stations, share one card. A journey with no line, such as a walk, groups by its modes. The journeys of a route are in order of departure, and the first one stands for the route: its legs, duration, facts, and alerts show on the card, it decides the place of the card in the sort, and a tap opens it. The card of the journey on the map uses `secondaryContainer`. It has no outline.

The leg chain comes first, with the duration at the end. The leg chain shows a badge for each line. A walk is an icon and its minutes, with no container. A first or last walk shows from 2 min. A walk between two lines shows from 5 min. The itinerary shows all walks. The itinerary summary uses the same chain.

A bar below places each leg in proportion to its time: a line in its color (`secondary` without one), a walk as dots in the muted text color, a wait as a gap. The bar is for the eye only; the leg chain gives the same facts to a screen reader.

The next row gives the next departures of the first line, from the journeys of the route, up to three: a countdown for the first one, the same as the nearby departures (a `tertiaryContainer` pill with the live icon for a live time), then "then 9 · 14 min", then the boarding stop. A departure an hour or more away, or gone, shows its clock time. A screen reader also gets the clock time of the first departure. The departures come only from the journeys of one plan request, so a route can show only one. The facts line starts with the arrival time of the first journey ("Arrive at 12:31"), then the transfers and the walking distance. The card shows no departure clock time of the trip; the departures row and the itinerary have it.

The alert line shows the first alert of the journey on one line, and the number of the others on an `errorContainer` pill. It shows only when the journey has an alert.

## States

Each list has a loading, empty, offline, and error state. Offline and error states give a retry button.

The loading state of a list is a skeleton (`Skeleton`): placeholders with the layout of the content to come, so the list does not jump when the content arrives.

- The results show three route cards: the leg chain and the duration, the bar, the departures, and the facts. The nearby departures show two stop cards. The stop shows one departure board. The disruptions show a group title and two cards. The search shows five place rows. Each itinerary leg with a boarding stop shows three train rows under "Next departures".
- A placeholder card has the container color and the shape of the real card. Its blocks use `onSurface` at 8% to 16% opacity, on all card colors.
- A text block has the line height of its text style, so the skeleton grows with the text size.
- The blocks of one list pulse in step, 900 ms for each direction. They do not pulse when system animations are off.
- A screen reader gets one "Loading" label for the list, not one for each block.
- The skeleton of a component is next to the component in the source. Change the two together.

The itinerary loads the boarding hints and the first line times of all legs at the same time. They appear together: the skeletons fade out, and the boarding rows, exit rows, and train lists fade in as their space opens, with the springs of the theme. Live times do not reset the shown departures.

The Expressive loading indicator is for an action in progress on content that is on screen: a train change. It is also the loading state when the layout to come is not known, as when the session plans again after process death.

## Accessibility

- Touch targets 48dp or more.
- Line badges have content descriptions ("Bus M41").
- Live status is in text, not only color.
- All strings live in `res/values/strings.xml`.

Automated checks cover visible click-action names, text contrast, and panel screenshots at 100% and 200% text size. The manual TalkBack check remains open. See `quality.md`.
