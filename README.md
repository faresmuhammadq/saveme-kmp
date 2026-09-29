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
version in `AppDatabase` goes up. Sign-out clears every table.

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Android tests: `./gradlew :shared:testAndroidHostTest`
- Screenshots: record with `./gradlew :shared:recordRoborazziAndroidHostTest`, check with
  `./gradlew :shared:verifyRoborazziAndroidHostTest`
- iOS tests: `./gradlew :shared:iosSimulatorArm64Test`

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…