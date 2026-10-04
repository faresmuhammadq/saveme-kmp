This is a Kotlin Multiplatform project targeting Android, iOS.

* [/iosApp](./iosApp/iosApp) contains an iOS application. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

* [/shared](./shared/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - [commonMain](./shared/src/commonMain/kotlin) is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    the [iosMain](./shared/src/iosMain/kotlin) folder would be the right place for such calls.
    Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./shared/src/jvmMain/kotlin)
    folder is the appropriate location.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and options:

- Android app: `./gradlew :androidApp:assembleDevDebug` (flavors: `dev`, `staging`, `prod`)
- iOS app: open the [/iosApp](./iosApp) directory in Xcode and run it from there.

### Development backend

The `dev` flavor talks to the backend running from `../saveme-backend` in its container, over
plain HTTP on your LAN. Set its address once, outside git, in `local.properties` or
`~/.gradle/gradle.properties`:

```properties
devApiHost=192.168.1.20:8080
```

The value is `host:port` only (the build fails on a path or scheme). Pick the host for how you run:

- Phone over USB: `devApiHost=localhost:<port>`, then `adb reverse tcp:<port> tcp:<port>` each
  time the phone reconnects.
- Emulator: `devApiHost=10.0.2.2:<port>`.
- Phone over Wi-Fi: your machine's LAN IP (`ip -4 addr show`); the container port must be
  published beyond `127.0.0.1`.

Without it the app falls back to `10.0.2.2:8080`. Full validation steps:
[specs/001-design-system-auth/quickstart.md](specs/001-design-system-auth/quickstart.md) and
[specs/002-account-security/quickstart.md](specs/002-account-security/quickstart.md).

### On-device database

Syncable data (profile, devices signed in; later wallets and transactions) lives in a Room
database, `saveme.db`, which screens read from (constitution P-04). The schema is exported to
`shared/schemas/` on every build — commit changes to it, and add a Room migration whenever the
version in `AppDatabase` goes up (now v9: budgets and the progress figures read for them; the person's own feed place is forgotten once
so budgets created earlier are read; v8 added an integer feed position stored beside each place; v7 added recurring rules and
occurrences). Sign-out clears every table. Budgets are computed on the phone from its own transactions and shown with the server's figures once
nothing has changed since they were read. Sync reads only what changed since the last position it stored per scope (the
person's own, and one per wallet), and sends waiting changes in batches of up to 50. A refresh first asks the server where
the followed wallets stand (`GET /sync/wallets/heads`) and reads only the wallets that moved.

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Android tests: `./gradlew :shared:testAndroidHostTest`
- Screenshots: record with `./gradlew :shared:recordRoborazziAndroidHostTest`, check with
  `./gradlew :shared:verifyRoborazziAndroidHostTest`
- iOS tests: `./gradlew :shared:iosSimulatorArm64Test`

### E2E tests

Each feature's acceptance scenarios run as [Maestro](https://maestro.mobile.dev) flows under
`.maestro/<feature>/`, against the dev build and the local backend.

- Needs: Maestro (`curl -fsSL https://get.maestro.mobile.dev | bash`), one emulator or phone in
  `adb devices` with its language set to English, and the saveme-backend dev container on port 43871.
- Generate or update the flows and run them: `/speckit-e2e [feature]`. This also runs as an
  optional hook after `/speckit-implement`. The results go to `specs/<feature>/e2e-report.md`.
- Run the existing flows only: `.specify/scripts/bash/run-e2e.sh <feature> [--skip-install] [--flow <name>]`.
  It creates a fresh `e2e+…@example.com` account for each flow in the dev database.
- Quit Maestro Studio first, including its tray icon. It holds the on-device driver, so CLI runs
  fail with `DeviceServerDiedException`.
- Xiaomi (HyperOS) phones block Maestro from clearing app data and from USB installs unless
  "USB debugging (Security settings)" and "Install via USB" are on. An emulator is simpler.

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…