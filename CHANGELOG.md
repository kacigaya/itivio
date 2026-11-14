# Changelog

All 0.x versions are pre-releases. Entries below follow the Git tags. See the
[roadmap](ROADMAP.md) for planned work.

## Unreleased

- Show skeleton placeholders while the journey results, nearby departures, stop board, disruptions, and place suggestions load.
- Show a loading indicator on the "Earlier departures" or "Later departures" button while its page loads.
- Show a preview during a back gesture. The screen on top becomes a card over the dim screen below. The Disruptions page shows the Departures page, and home shows the launcher.
- Give the bottom sheet the corner radius of the screen.
- Show the walking view as a navigation map: tilted, turned with you, and with your position as an arrow low on the screen. Near the route, the arrow stays on the route line. The part of the walk that is done is gray.
- Add a compass button that turns the walking map between north up and "turn with you", and a button that shows the full route.
- Show the next maneuver of the walking view on a "Then" tab below the instruction card.
- Change the app icon to a destination pin that walks, in flat colors.

## 0.8.3

- Match station IDs for supported Metro-to-RER boarding guidance, including Metro 9 to RER A at Nation.
- Show shared RER station exits when the arrival platform is unknown. Keep the train position hidden in this case.
- Keep Transitous and IGN/BAN credits and the French address privacy notice in Settings → About only.
- Show a loading indicator while each itinerary leg loads its first departures. Show completed legs without waiting for the others.

## 0.8.2

- Show the best exit of the station below the alighting stop, with its number and name. Pick the exit nearest to the destination, so the boarding hint also shows at large stations.
- Fix a one-pixel step in the itinerary rail below the stop dots.

## 0.8.1

- Add a privacy policy, README screenshots, contribution guidance, and a security reporting policy.
- Add this changelog.
- Include minutes in spoken walking badges.
- Remove duplicate text from spoken walking direction buttons.
- Say "Now" instead of "In Now" for departures due now.
- Show departures and disruptions as two pages of the home sheet on phones, with an alert count. Remove the navigation bar. Phones now request the alerts while home is open.
- Show the delay, the arrival time, and the time on board for each train choice. Show the destination without the trip code. Remove the radio buttons, the trip code in the leg header, and the operator name.
- Make route cards shorter: heavy times, a compact leg chain, and one alert line with a count.
- Let the results filter row scroll to the screen edges.
- Add icons to the theme picker, the walking speed, and the maximum transfers.
- Remove the flight mode. No journey uses a flight.
- Fix the position marker in dark color schemes with a light primary color.
- Show where to board the metro in Paris, for the next transfer or the exit. The RATP data ships with the app.
- Move the MapLibre credit from the map to the About screen. The map credits button stays.

## 0.8.0

- Add train selection and reset for each transit leg.
- Track the selected train and warn about missed connections and platform changes.
- Page through line departures from the time the platform is reachable.
- Improve screen-reader labels, text contrast, and layouts at 200% text size.
- Add screenshot comparisons and run tests, lint, and builds in CI.
- Target Android SDK 37.

The manual TalkBack check is still open. Test devices use SDK 35. Runtime checks
on SDK 37 are also pending. See [quality checks](docs/quality.md).

## 0.7.0

- Refresh the interface with Material 3 Expressive, Google Sans Flex, and rounded icons.
- Add disruptions for saved stops and nearby stops.
- Add settings, theme selection, data deletion, and an about screen.
- Add French text.

## 0.6.0

- Add stop departure boards from the map and search.
- Refresh itinerary times from live trip data.
- Restore the planning request after process death.
- Add walking directions and route alerts on result cards.

## 0.5.0

- Add earlier departures and saved journey options.
- Add transport mode selection, a transfer limit, wheelchair routing, and walking speed.

Wheelchair routing applies to walks and transfers. It does not check vehicles.

## 0.4.0

- Store favorites, Home, Work, and recent searches on the device.
- Add an action to clear recent searches.

## 0.3.1

- Fix search state after a place selection.
- Correct countdowns and remove departures that have left.
- Improve endpoint names, map padding, and search transitions.

## 0.3.0

- Add IGN address search for French address queries.
- Cover the map below the navigation bar.

## 0.2.0

- Add a readable dark map and live line times in itineraries.
- Move home search controls within thumb reach.
- Improve session recovery and control layouts.

## 0.1.0

- Add maps, place search, journey planning, and itinerary details through Transitous.
- Add nearby departures and travel time selection.
- Add APK builds for supported CPU types and a universal APK.
