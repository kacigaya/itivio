# Contributing

Use the [issue tracker](https://github.com/kacigaya/itivio/issues) for bugs and
feature requests. Use [SECURITY.md](SECURITY.md) for security problems.
Do not put private locations, journeys, credentials, or signing keys in issues,
logs, screenshots, or test fixtures.

## Set up

1. Install JDK 21 and the Android SDK for compile SDK 37.
2. Clone the repository.
3. Set `sdk.dir` in an untracked `local.properties` file, or set `ANDROID_HOME`.
4. Read [architecture](docs/architecture.md), [design](docs/design.md), and [quality checks](docs/quality.md).
5. Run `./gradlew :app:assembleDebug`.

Use the Gradle wrapper. The project uses Gradle 9.9.0 and AGP 9.5.1 with built-in
Kotlin. See [AGENTS.md](AGENTS.md) for host-specific setup.

Before a build that sends Transitous requests, set `itivio.contact` to your own
contact address or project URL. See the [README](README.md#transitous-contact).
Never use another person's contact details for a fork.

## Make a change

1. Keep the change focused. Discuss large features and production dependencies in an issue first.
2. Keep UI code dependent on domain types. Do not import data types into UI code.
3. Keep requests in the shared clients. Do not log URLs, coordinates, or journeys.
4. Put user text in `res/values/strings.xml` and add its French text in `res/values-fr/strings.xml`.
5. Add focused tests for changed behavior. Use local fixtures, not live transport or address APIs.
6. Update the README, relevant docs, and the Unreleased changelog entry when behavior changes.

Room schema changes need a database version increase, a migration, and exported
schemas in `app/schemas/`. Journey options and app settings share one DataStore
instance. Keep location access optional and stored data on the device.

## Verify and submit

1. Run `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`.
2. Inspect reports in `app/build/reports/` and screenshot differences in `app/build/outputs/roborazzi/`.
3. Follow [quality checks](docs/quality.md) for intentional screenshot updates and manual TalkBack tests.
4. Open a pull request with the problem, the resulting behavior, and the checks you ran.
5. State any checks you could not run and why.

Do not update screenshot references to hide a failure. Do not commit
`local.properties`, signing keys, passwords, or unrelated changes.

Contributions use the project's [GPL-3.0-or-later license](LICENSE).
Keep the license notices for bundled fonts and icons.
