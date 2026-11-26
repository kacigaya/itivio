# Changelog

All 0.x versions are pre-releases. Entries below follow the Git tags. See the
[roadmap](ROADMAP.md) for planned work.

## Unreleased

- Show more disruptions at once on home. Each one is a short row that opens into the full card.

- Make "Done" easier to find in the travel time and journey options sheets. It is now a filled button, and the sheet titles are heavy like the page titles.
- Move the travel time forward in one tap with "+15 min", "+30 min", and "+1 h" in the travel time sheet.
- Show how many journey options differ from the defaults on the Reset button, for example "Reset 3 changes".
- Keep long stop names readable in the itinerary. When the name and the time do not fit on one line, the time moves below the name. At large text, names no longer break into pieces.
- Fix the itinerary duration at large text. "26 min" broke into "2", "6", and "min" on 3 lines. The departure and arrival times now move below it.
- Retry a failed train change in one tap. The error is now a banner with a "Try again" button that picks the same train again.
- Say which way the train goes in "Where to board", with a "Front →" label under the nose of the drawing. "Best exit" now uses the same card, so the two hints read together.
- Say how far you are from the route when you walk off it. The off-route banner also gives "Plan a new route" its own line, so the text no longer wraps into a narrow column.
- Show how far away the next turn is in the walking view: "Then in 20 m". The "Recenter" button is now round like the compass button, so the two map buttons form one group.
- See what changed in your version: "What’s new" on the about page opens its release notes in the browser.
- Show the current language and journey options in Settings. The rows now say what is set, for example "2 modes off · At most 1 transfer".
- Without location access, home now shows a short card with the "Allow location" button in view, in place of a long introduction that pushed the button off the sheet.
- Make the disruptions list shorter and clearer. Alerts with the same lines and effect, such as works in two periods, share one card. Long titles show 2 lines until you tap. "More information" now names the website it opens.
- Pick a nearby stop in one tap. The search on home lists the 3 nearest stops, with their lines and distance.
- Start each route card with its clock times, such as "8:58 AM – 9:24 AM", so you can compare when routes leave and arrive at a glance.
- Keep the results buttons in view after you pick a time on another day: the day moves to a second line. With "Arrive by", each route says how much time it leaves you, such as "3 min to spare".

## 0.14.2

Beta prerelease for Android 8.0 and later. It updates 0.14.1 in place.

- Open the search without a stutter. The search now opens in the app's own window, not in a new one, so the first frame of the animation is short.
- Make the search bar and other screens smoother after an install or an update. The app now ships a baseline profile, so Android compiles its code ahead of time, the next time it optimizes apps in the background.

## 0.14.1

Beta prerelease for Android 8.0 and later. It updates 0.14.0 in place.

- Fix a double tap that opened a screen twice. A double tap on a saved place no longer leaves an empty results screen after Back.

## 0.14.0

Beta prerelease for Android 8.0 and later.

**This release needs one reinstall.** From now on, the APKs are signed with the project key. Versions up to 0.13.1 used a debug key, and Android cannot update across a key change. Uninstall itivio, then install this version. Uninstalling deletes your saved places, recent searches, and settings. Later releases update in place. See the README for the certificate fingerprint.

- Show one card for each route: journeys on the same lines from the same stations share a card. The card shows the next departures of the first line, with a live countdown, and the arrival time.
- Remove "Earlier departures" and "Later departures". The travel time button sets the time of the search.
- When you pick another train on a leg, each later leg moves to the first train you can still catch. Reset puts the planned trains back when you can still catch them.
- Publish signed APKs and checksums from a release workflow. The asset names have no `-debug` suffix any more.
- Add F-Droid store metadata.

## 0.13.1

Beta prerelease for Android 8.0 and later.

- Keep the map covered when Settings and About slide between screens.
- Move the journey cards before updating the map when sorting. Fit the itinerary map in one step when leaving walking directions.
- Keep the map pane steady when opening Disruptions in the wide layout.
- Update the Android build tools and dependencies. The travel-time picker shows more of the adjacent hours and minutes.

## 0.13.0

Beta prerelease for Android 8.0 and later.

- Put live countdowns on a pill, so a live time is the strongest number on its row. Timetable countdowns stay plain.
- Show "Leave in 4 min" on each route card, with a bar that shows each leg by its time: lines in their color, walks as dots, and waits as gaps.
- Keep the red of a severe disruption on its header only, so the description is easy to read. Dates within a week show the weekday, and dates later this year have no year.
- Save a stop to your favorites from its sheet with the star button. Saved stops show in "Your saved stops" of the disruptions.
- Close the walking view with a grey button. Red is now only for the off-route banner.
- Group the search results. Stops name their modes, so stops with the same name differ, and the part that matches your search is bold. "Set home" and "Set work" show in the search when they are empty.
- Show a calm light map in the colors of the app, so the lines of routes and stops stand out. The app now has the map style built in.
- Show the launch screen in the colors of the app, with no white flash.

## 0.12.0

Beta prerelease for Android 8.0 and later.

- Open the travel time and journey options as bottom sheets that come up from the bottom edge. Swipe down to close them without changes. A back gesture pulls the sheet down, the same as a screen, and "Done" and "Cancel" slide it down.
- Pick the travel time with the scroll time picker. "Leave at" and "Arrive by" are connected buttons, the same as the walking speed.
- Show the transport modes in a grid of equal chips: three on each row on most phones, fewer on a narrow screen or with large text. Each chip has a short name, and a screen reader gets the full name.

## 0.11.0

Beta prerelease for Android 8.0 and later.

- Move each new screen up from the bottom edge as a card. "Back" sends the card down again. A back gesture pulls the card down and shows the screen below.
- Draw the walk on your side of the street in the walking view. The line moves sideways to pass under your position, up to 15 m from the street centre.

## 0.10.1

- Show your real position in the walking view. The arrow no longer jumps to the route line, so it stays on your side of the street.
- Plan a new walk from your position when you leave the route, at most once every 20 seconds while the app is open. Your position goes to Transitous for this request. The rest of the journey does not change.

## 0.10.0

- Keep the walking arrow on the route line while you are on the route. The off-route limit follows the GPS accuracy, from 15 m to 50 m.
- Show a wrong turn after 10 m when you walk away from the route. The off-route banner stays until you are back within 10 m.
- Update the walking position twice a second and the compass five times a second, so the arrow follows your steps.
- Zoom the walking map closer to your position.
- Move the compass and "Recenter" buttons of the walking view to the bottom left, above the sheet.

## 0.9.1

- Keep train choices correct when the requested time, direction, or arrival platform changes.

- Fix the next departures of itinerary legs, which stayed empty on recent Android versions. The request time had microseconds, so Transitous started at the beginning of the service day.
- Show a skeleton of the next departures while an itinerary loads. The boarding hints and the departures of all legs appear together, and live times no longer reset the list.
- Load the line times and live trip times of all legs at the same time.
- Keep journey results, departure boards, nearby departures, disruptions, line times, and live trip times in memory for 30 seconds, so a screen that opens again shows them at once.
- Give the itinerary summary the same margin on each side of the side pane, and a corner radius that follows the pane corner.

## 0.9.0

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
