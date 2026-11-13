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

The time-picker images allow up to 0.005% differing pixels for clock-hand rasterization differences between ARM64 and x86_64. All other images require an exact match.

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

Current limits: the Docker Android device now has TalkBack and a speech engine.
The manual gate remains open for the cases listed below. Both connected device
entries use SDK 35. Runtime behavior on SDK 37 still needs a device check.

## Docker TalkBack check, 2026-11-13

The check used the Android 15 container described in [AGENTS.md](../AGENTS.md).
TalkBack came from Google's source at commit
`229212fdf5842191d0a93fc95d9ca1423b346866` (`TfPu_release_16_2`).
eSpeak NG 1.52.0 came from its official release APK.

The temporary TalkBack build used Gradle 8.14.3 and JVM target 17. Native
Braille builds and Braille display startup were disabled because the installed
NDK does not support this ARM64 host. These changes are outside the app source.
This build does not verify Braille support or the exact Play Store build.

The check used a virtual Linux keyboard exposed to Android. TalkBack handled
Meta+Right to move focus and Meta+Space to activate controls. Speech checks used
TalkBack's generated output and its successful calls to eSpeak. They did not
verify audible playback. Ordinary `adb input` injection bypassed TalkBack's
navigation, so it was not used as proof of gesture support.

### Coverage

1. Opened home, search, results, itinerary, the stop board, walking, disruptions,
   settings, and about with TalkBack active.
2. Traversed settings, about, departure lists, disruption cards, journey results,
   itinerary details, and walking instructions. Checked names, roles, and states.
3. Opened saved-place menus, the delete confirmation, journey options, and the
   travel-time picker. Checked their controls and back actions. Cancelled the
   delete confirmation without deleting app data.
4. Checked live and scheduled labels in departure lists and itineraries.
5. Repeated navigation at 200% text size on home, search, settings, journey
   options, itinerary, and walking. Checked journey options in the dark theme.
   This was not the full screen, theme, and text-size matrix.
6. Restored the system theme, font scale, accessibility settings, and speech
   engine selection after the check. Left TalkBack and eSpeak installed.

### Fixes

- Transfer choices no longer repeat their abbreviated number. TalkBack now
  reads, for example, "3 transfers, Radio button".
- Departures due now say "Now" instead of "In Now". French has its own label.
- Walking direction buttons no longer repeat "Directions" after their full name.
- Walking badges include minutes. Train arrival labels replace their visual text.

Seven focused semantics tests pass. Lint and the debug build pass. The earlier
full test run found 20 screenshot mismatches and two train-selection test
failures in the current worktree. Screenshot references were not changed.
The isolated documentation and accessibility commit then passed the full test
suite, lint, and the debug build without the separate UI changes.

### Remaining checks

1. Exercise train choices with available departures. The live train itinerary
   did not expose choice rows during this check.
2. Exercise cancellations, train-change errors, connection warnings, and new
   walking instructions as the device position changes.
3. Complete the screen, dialog, theme, and text-size matrix in the manual checklist.
4. Test physical touch gestures and audible speech output. Test on SDK 37 too.

A temporary audit helper also triggered a Compose UI 1.12.1 crash by sending a
scroll action to a virtual node that did not offer that action. The failure was
in `AndroidComposeViewAccessibilityDelegateCompat.performActionHelper`, while
reading bounds from a detached node. The helper now sends only actions listed
by the node. Supported scrolling then worked. The crash was not reproduced by
TalkBack keyboard navigation. Do not suppress app exceptions to hide it.
