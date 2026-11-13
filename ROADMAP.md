# Roadmap

This file lists the work between v0.3.1 and v1.0.0. It is a plan, not a promise. The order can change. The roadmap has no dates.

All 0.x releases are pre-releases. Each release must pass tests, lint, and the debug build.

| Stage | Releases |
|---|---|
| Alpha | Up to v0.7.0 |
| Beta | v0.8.0 and v0.9.0 |
| Stable | v1.0.0 |

## Rules for every release

- No account, no analytics, no ads.
- New data stays on the device. Backup and device transfer stay off.
- A new network request needs an entry in the README privacy table.
- A new production dependency needs a clear reason.
- Each change in behavior updates `README.md` and `docs/`.

## v0.4.0: Saved places

This release adds local storage. All items are done.

- [x] Add favorites for places and stops.
- [x] Add Home and Work shortcuts.
- [x] Show recent searches in the search view.
- [x] Add an action that clears recent searches.
- [x] Store saved places in Room, as `docs/architecture.md` describes.

DataStore is not in this release. Saved places are list data, so Room stores all of them. DataStore comes in v0.5.0 with its first use.

## v0.5.0: Journey options

All items are done.

- [x] Add an "Earlier" button to the results. The provider already returns the cursor.
- [x] Let the user select transport modes.
- [x] Add a limit for transfers.
- [x] Add wheelchair-accessible routing.
- [x] Add a walking speed option.
- [x] Keep the selected options between app starts. Add DataStore for this.

The wheelchair option changes walks and transfers only. The provider does not check vehicles.

## v0.6.0: Stops, live tracking, and walking view

All items are done.

### Stops and live tracking

- [x] Open a departure board when the user taps a stop on the map.
- [x] Open a departure board from a stop in the search results.
- [x] Refresh the legs of an itinerary with `/v6/trip`, so the tracker follows new delays.
- [x] Restore the planning session after process death. Save only the minimal request.
- [x] Show service alerts on route cards. Today only the itinerary shows them.

### Walking view

A turn-by-turn view for the walking parts of a journey. It opens from a walking step of the itinerary and from a walk-only route.

- [x] Read the walking steps from the plan response. The response already has them, so the view needs no new network request.
- [x] Show the next instruction at the top: direction arrow, distance, and street name.
- [x] Show a preview of the instruction after the next one.
- [x] Calculate the turn direction from the step geometry. In the test fixture, the provider gives `CONTINUE` for almost all steps.
- [x] Use a fallback text when a step has no street name.
- [x] Draw the walking route as a dotted line and show the destination marker.
- [x] Make the map follow the device position and heading. Add a button that centers the map again.
- [x] Show the remaining time, the remaining distance, and the arrival time at the bottom.
- [x] Add a close button that goes back to the itinerary.
- [x] Tell the user when they are off the route. Give a button that plans a new route. Do not plan a new route automatically.

Limits for this release:

- The device position stays on the device during the walking view. Only the "plan a new route" button sends it.
- The walking view works only while the app is on screen. The app does not ask for background location.

## v0.7.0: Disruptions, languages, and settings

This is the last alpha release.

### Disruptions tab

A tab that lists the service disruptions ("Perturbations" in French) that affect the user's stops.

- [ ] Add top-level navigation with two destinations: Map and Disruptions. The app has no tabs today. Use a navigation bar on phones and a navigation rail on wide screens.
- [ ] Ask for alerts with the stop times. Today the app sets `withAlerts=false` on `/v6/stoptimes`.
- [ ] Read the severity, effect, cause, and impact period of an alert. Today the app reads only the header, the description, and the link.
- [ ] Show the disruptions in two groups: favorite stops, then stops near the user.
- [ ] Put the most severe disruptions first in each group.
- [ ] Show each disruption with its stop, its effect, its time period, and its link.
- [ ] Show an alert only one time when it applies to more than one stop.
- [ ] Add an empty state. Tell the user that some regions publish no alerts.
- [ ] Add a test fixture with alerts. The current fixtures have none.
- [ ] Add the new data to the README privacy table: the tab sends the IDs of the favorite stops to Transitous.

Limits for this release:

- The provider has no endpoint that lists all disruptions of a network or a line. The tab shows only the disruptions on the stops it asks about.
- The tab loads disruptions only while it is on screen. The app sends no background requests and no notifications.

### Languages and settings

- [ ] Add a French translation. English is the only language today.
- [ ] Add each new language to `locales_config.xml`, so Android shows the per-app language setting.
- [ ] Add a settings screen: theme, journey options, and stored data.
- [ ] Add an about screen: version, license, data credits, and privacy text.
- [ ] Add an action that deletes all app data: the Room database and DataStore.

## v0.8.0: Accessibility and quality

This is the first beta release.

- [ ] Test each screen with TalkBack. Fix the problems.
- [ ] Test each screen at 200% text size. Fix the problems.
- [ ] Check color contrast in light, dark, and dynamic color themes.
- [ ] Compare screenshots against goldens. Today Roborazzi only records them.
- [ ] Run tests, lint, and the build on each push and each pull request.
- [ ] Replace pre-release dependencies with stable versions: Material 3 `1.5.0-rc01` and Robolectric `4.18-beta-1`.
- [ ] Set `targetSdk` to 37.

## v0.9.0: Release candidate

Feature work stops here. Only fixes go in after this release.

- [ ] Sign the APKs with a release key. Today the releases use a debug key.
- [ ] Tell users that the key change needs one reinstall.
- [ ] Add a release workflow that builds, signs, and publishes the APKs with checksums.
- [ ] Add F-Droid metadata and send the app to F-Droid.
- [ ] Add `CHANGELOG.md`, `CONTRIBUTING.md`, and `SECURITY.md`.
- [ ] Add a privacy policy file.
- [ ] Add screenshots to the README.

## v1.0.0: Stable

v1.0.0 has no new features. It ships when all these conditions are true:

- [ ] No open bug causes a crash or data loss.
- [ ] v0.9.x has had no new blocker for two weeks.
- [ ] All validation gates pass on the release commit.
- [ ] `README.md` and `docs/` agree with the app behavior.
- [ ] The release-signed APK installs and updates correctly on Android 8.0 and on the newest Android version.
- [ ] The Transitous contact and usage policy are correct for a stable release.

## After v1.0.0

These items are not planned before v1.0.0:

- Home screen widget for departures.
- Departure reminders.
- Disruption notifications.
- Disruptions by line, and a list for the full network. This needs a provider endpoint that does not exist today.
- Voice instructions in the walking view.
- Alternative walking routes on the map.
- Walking view with the screen off.
- Saved journeys that work offline.
- Bicycle and car for the first or last part of a journey.
- A setting for a self-hosted MOTIS server.
- Country selection for address search. Add this only if the current France rule causes coverage problems.
- More address providers for other countries.
