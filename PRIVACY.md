# Privacy policy

This policy describes the itivio Android app. itivio has no accounts, ads, analytics, or crash reporting. The app does not log your location, journeys, or request URLs.

## Data on your device

The app stores these items on your device:

- Favorites, Home and Work, and the 10 most recent places you selected.
- Journey options, including transport modes, transfer limit, wheelchair access, and walking speed.
- App settings, including the theme.
- The departure reminder you set: the journey, the time of the reminder, the minutes before departure, and the text of its notification. It stays until you remove it, until it goes off and the app starts again, or until you delete all app data.

Saved places include their names, coordinates, and provider IDs. They remain until you remove them. A new recent place can replace the oldest one.

Search results and journey results are kept in memory. Android can keep a minimal planning request to restore your session after it stops the app. This request includes the origin, destination, travel time, and arrival-time choice. When the session returns, the app sends the request to Transitous again.

The app disables Android backup and device transfer for its data.

## Location

Location access is optional. You can search for places and plan journeys without it. The app uses the Android location provider. It does not require Google Play services or request background location access.

With permission, the app uses your location to show your position, nearby departures, and nearby disruptions. It can also use your location as the start of a journey.

- Search suggestions send a location bias rounded to about 1 km to Transitous.
- Nearby departures and disruptions send a position rounded to about 10 m to Transitous.
- Journey planning sends the start and end points. Planning from your location sends that position at full precision.
- Walking directions use your position and heading on the device. Selecting "Plan a new route" sends your position to Transitous as the start of a new journey.
- With "Travel time to Home and Work" turned on in the settings, the app plans two journeys while the home screen is open: from your position at full precision to Home, and to Work. It sends them when home opens, and again after you move about 150 m, at most once every 20 seconds. The setting is off by default. These requests send your position and both places in the clear, as journey start and end points.

Map requests reveal the area you view, including when the map follows your location.

## Network services

The app uses these services to provide search, transport information, and maps:

| Service | Data sent | Purpose |
|---|---|---|
| [Transitous](https://transitous.org/) | Search text, search language, rounded location bias, journey start and end points, travel time, journey options, nearby position, your position with your Home and Work places when travel times are on, stop and trip IDs, and the map area at street-level zoom | Search, journey planning, departures, disruptions, live trip times, travel times to Home and Work, and stops on the map |
| [IGN Géoplateforme](https://cartes.gouv.fr/) | Address search text, without location bias | French address search, only when the text looks like an address and the first Transitous match identifies France |
| [OpenFreeMap](https://openfreemap.org/) | Requests for map icons, fonts, and tiles for the area you view | Map display |

The disruptions list sends the IDs of up to 10 saved stops to Transitous. On a phone, the list is a page of the home screen, and the home screen shows the number of alerts. The app then sends these IDs while the home screen is open. On a tablet, it sends them only while the Disruptions page is open. These can be Home, Work, or favorites. Saved places that are not stops are not sent for this feature. Selecting a saved place for a journey sends its stop ID or coordinates as part of the journey request.

Live departures, trip times, and disruptions refresh only while the relevant screen is visible and the app is in the foreground. The one exception is "Disruption alerts" in the settings. It is off by default. When you turn it on, the app checks your saved stops in the background about every 30 minutes, when a network is there: it sends the same stop IDs as the disruptions list to Transitous, and no location. It keeps the text keys of the alerts it showed, on the device, so each alert notifies once. Turning it off, or "Delete all app data", stops the checks. Another exception is a departures widget on the home screen: it sends the ID of its stop to Transitous when Android updates it, about every 30 minutes, and when you tap its refresh button. Remove the widget to stop this.

A departure reminder also reaches you while the app is closed, and it stays on your device. The app schedules it with an Android alarm, and an Android notification shows it. It sends no request and reveals nothing to a service. The app asks for the notification permission when you set the first reminder. Android 14 and later also ask whether the app may set exact alarms; without that permission, the reminder can come a few minutes late. After a device restart, the app sets the stored reminder again.

Each service also receives your IP address and normal connection information. Transitous requests identify the app version and include the publisher's contact in the User-Agent. IGN uses a separate client without contact details. The app adds no account ID or advertising ID to these requests.

These services process the requests under their own policies. This policy does not set their log retention periods. The app's data deletion action cannot delete records held by those services.

Boarding positions in Paris come from a table that ships with the app. Showing them sends no request.

Links to service alerts, data credits, and source code open outside the app. The destination site receives the connection information from your browser.

## Remove data or location access

1. Open Settings in itivio.
2. Select "Clear recent searches" to remove recent places only.
3. Select "Delete all app data" to remove saved places, journey options, and app settings.
4. Confirm the deletion.

The deletion action does not end the current journey or clear results already in memory. Close the planning session to remove its saved request. Stop the app to clear its in-memory results. You can also clear the app's storage or uninstall it through Android settings.

You can remove location permission at any time in Android settings.

## Questions and changes

Report privacy questions through the [project issue tracker](https://github.com/kacigaya/itivio/issues). Issues are public. Do not include private locations, journeys, or other personal data.

Changes to this policy are recorded in the [file history](https://github.com/kacigaya/itivio/commits/main/PRIVACY.md).
