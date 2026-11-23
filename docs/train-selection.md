# Train selection

Each transit leg has a list of departures for its line and alighting station. A row shows the destination, the platform, the departure time, the arrival time at the alighting station, and the time on board. A late train shows its planned time struck through and its delay in words. The row of the selected train has a "Your train" label.

1. Select a departure to follow that train.
2. Wait for the trip check to finish. The current train stays selected if the request fails or the trip no longer serves both stops.
3. Use **Reset** on that leg to return to its original train. Reset also checks the current trip data.

The selected train updates the times, platforms, intermediate stops, alerts, and adjacent walking times. The itinerary does not show trip codes. A Transilien mission code, such as GAMO, does not replace the destination.

The tracker refreshes the selected trip while the itinerary is visible. Selections survive a return from the background. They stay in memory for that itinerary entry. They do not change the original search results or survive process death.

A change moves the later legs. Each later leg takes the first departure of its line that leaves after the incoming train and the transfer walk, earlier or later than its train before. When all trains before a leg are the planned ones again, as after **Reset**, the leg takes its planned train again if it can still catch it. A late planned train can miss the planned connection; the leg then takes the first departure it can catch. A train that you selected on a later leg is replaced only when a leg before it changes. While a later leg changes, it shows the loading indicator, and no train can be selected.

If no departure is known for a later leg, or its trip check fails, that leg keeps its train. A warning then appears if the incoming train and transfer walk finish after that train leaves. The app does not send a new planning request by itself.

A platform change clears walking geometry and instructions that lead to the old platform. The app shows a notice to follow station signs. Walking times still come from the original plan. Rerouting inside stations would need confirmed accessible paths and platform transfer times from the provider.

Departure scans use at most eight pages of 80 station events. This bounds each refresh. A busy station or a line with little service can still give fewer than four matches. Selection makes one trip request for the selected train, then one departure request for each later leg and one trip request for each later leg that changes. The normal trip and departure refreshes then follow the changed itinerary.
