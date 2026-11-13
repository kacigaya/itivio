# Quality checks

## Local checks

1. Run `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`.
2. Read failures in `app/build/reports/`.
3. Read screenshot differences in `app/build/outputs/roborazzi/`.

The same checks run on each push and pull request. A manual run also uploads debug APKs. A failed run uploads test reports and screenshot differences for 14 days.

## Screenshot references

`ScreenshotTest` and `LargeScreenshotTest` compare screen panels with the PNG files in `app/src/test/screenshots/`. They use SDK 35, native graphics, US English, a 411dp by 891dp phone configuration, and xxhdpi density. Wide-layout cases use the app's side pane. The native map is excluded. The travel picker uses a fixed clock. Other time-sensitive panels receive a fixed time from their fixtures.

Each case runs with the light and dark fallback palettes at 100% and 200% text size. Cases include search, home, results, journey options, the time picker, itinerary, train choices, departures, disruptions, settings, about, and walking. Each capture also checks that visible click actions have accessible names. Palette tests check normal-text contrast for fallback and system dynamic color pairs. Line badges keep the provider text color only when it meets 4.5:1 contrast.

To accept an intentional visual change:

1. Run `./gradlew :app:testDebugUnitTest --tests 'app.itivio.ui.*ScreenshotTest' -Proborazzi.test.record=true`.
2. Inspect the changed PNG files. Check both text sizes and both themes.
3. Run the normal local checks again. They compare images without changing the references.
4. Commit the accepted references with the change.

Do not record new references to hide an unexplained failure. Rendering can differ across host architectures. Check the reference configuration before accepting a difference on another host.

## Manual accessibility check

Automated semantics checks do not replace TalkBack. Before the beta release:

1. Install TalkBack on a test device and enable it.
2. Open home, search, results, itinerary, train choices, the stop board, walking, disruptions, settings, and about.
3. Open the journey options, travel time, saved-place menus, and delete confirmation.
4. Move focus through each control. Check its name, role, selected state, and focus order.
5. Check that live and scheduled times are announced once. Check delay, cancellation, train-change errors, connection warnings, and new walking instructions.
6. Confirm that scrolling reaches all controls. Test each dialog's close and back actions.
7. Set text size to 200%. Check each screen and dialog again. Use light, dark, and system dynamic colors.
8. Restore the device settings after the check.

Current limit: neither connected test device has TalkBack installed. The manual TalkBack gate remains open in the roadmap. The devices run SDK 35, so runtime behavior on SDK 37 also needs a device check. The app compiles and targets SDK 37.
