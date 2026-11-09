# Design direction

Material 3 Expressive on Jetpack Compose. The map is the canvas. The UI floats on it in tonal surfaces.

## Principles

1. Map first. Search, results, and itineraries sit on a bottom sheet over the map. The map always shows the context.
2. One primary action per screen. The search bar on home. The route cards on results. The itinerary steps on detail.
3. Live data must be clear. Live times use the `tertiary` role and a live icon. Scheduled times use `onSurfaceVariant`. A delay shows the struck-through scheduled time beside the live time.
4. Line colors come from the agency. Line badges use the agency color and text color. When the feed has no color, badges use `secondaryContainer`.

## Color

- Dynamic color on Android 12+. Fallback scheme: tonal spot from seed `#1B6B5A`.
- Map style: OpenFreeMap Liberty (light) and Dark (dark theme).
- Route lines on the map: transit legs in the line color with a `surface` casing. Walking legs are dotted in `onSurfaceVariant`.

## Type

Default Material type scale (Roboto). Times and durations use `titleLarge` / `titleMedium` with tabular figures. Emphasized styles mark the selected route.

## Shape and spacing

- Search bar: full. Route cards: `large` (16dp). Bottom sheet: `extraLarge` top corners. Line badges: `small` (8dp).
- 4dp grid. Screen margin 16dp (compact), 24dp (medium and up). Card padding 16dp.

## Motion

`MotionScheme.expressive()`. Sheet and card changes use the theme springs. Screen changes use shared-axis fades. Loading uses the Expressive `LoadingIndicator`.

## Screens

| Screen | Compact | Medium and up |
|---|---|---|
| Home | Map, floating search bar at top, "locate me" FAB, bottom sheet with nearby departures | Map with sheet content in a 400dp side pane |
| Search | Full-screen search view: origin and destination fields, swap button, suggestions list | Same, in the side pane |
| Results | Sheet with route cards. Map shows the selected route | Side pane with route cards |
| Itinerary | Sheet with leg timeline. Map fits the route | Side pane timeline |

## Route card

```
[12:04 → 12:31]                       27 min
[walk 4] › [U2] › [walk] › [M41]   ●Live
Departs 12:08 from Alexanderplatz · 1 transfer · 450 m walk
```

## States

Each list has a loading, empty, offline, and error state. Offline and error states give a retry button. Loading uses the Expressive loading indicator, not skeleton boxes.

## Accessibility

- Touch targets 48dp or more.
- Line badges have content descriptions ("Bus M41").
- Live status is in text, not only color.
- All strings live in `res/values/strings.xml`.
