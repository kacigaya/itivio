# Train selection

Each transit leg has a list of departures for its line and alighting station. A row shows the destination, the platform, the departure time, the arrival time at the alighting station, and the time on board. A late train shows its planned time struck through and its delay in words. The row of the selected train has a "Your train" label.

1. Select a departure to follow that train.
2. Wait for the trip check to finish. The current train stays selected if the request fails or the trip no longer serves both stops.
3. Use **Reset** on that leg to return to its original train. Reset also checks the current trip data.

The selected train updates the times, platforms, intermediate stops, alerts, and adjacent walking times. The itinerary does not show trip codes. A Transilien mission code, such as GAMO, does not replace the destination.

The tracker refreshes the selected trip while the itinerary is visible. Selections survive a return from the background. They stay in memory for that itinerary entry. They do not change the original search results or survive process death.

Later trains keep their selection. A warning appears if the incoming train and transfer walk finish after the next train leaves. Select another departure on the next leg to repair that connection. The app does not send a new planning request by itself.

A platform change clears walking geometry and instructions that lead to the old platform. The app shows a notice to follow station signs. Walking times still come from the original plan. Rerouting inside stations would need confirmed accessible paths and platform transfer times from the provider.

Departure scans use at most eight pages of 80 station events. This bounds each refresh. A busy station or a line with little service can still give fewer than four matches. Selection makes one explicit trip request. The normal trip and departure refreshes then follow the changed itinerary.
