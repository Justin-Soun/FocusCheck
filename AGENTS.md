# Focus Check

Single-module Android app (`:app`), Kotlin/Jetpack Compose/Material3; package `com.justin.focuscheck`.

## Build and verification

- Use `./gradlew assembleDebug` to build and `./gradlew installDebug` to install. The only tests are template `ExampleUnitTest` and `ExampleInstrumentedTest`, not meaningful coverage.
- This WSL environment has no usable JDK or Android SDK: `local.properties` targets the Windows SDK. Build and run through Android Studio on the Windows host.
- Gradle 9.5.0, AGP 9.3.1, Kotlin 2.2.10; `compileSdk` is API 36.1, and Java source/target is 11. Release shrinking is intentionally disabled in `app/build.gradle.kts` while physical-device validation continues.

## Application structure

- `MainActivity.kt` contains the sole activity and all private Compose UI (about 5,000 lines). `FocusCheckApp` switches screens through `AppScreen`; extend it rather than adding navigation infrastructure or another activity/fragment.
- MVVM boundaries: `data/` is Room plus repositories, `viewmodel/` exposes UI state, `settings/` owns reminder settings DataStore, and `notifications/` owns alarms, notification delivery, and runtime reminder state.

## Persisted data

- Room is version 4 in `data/FocusCheckDatabase.kt` with hand-written migrations 1->2, 2->3, and 3->4. For a schema change, increment the version, add and register a new migration; never alter shipped migrations.
- Task due dates are `LocalDate.toEpochDay()` values in `dueDateEpochDay`, not timestamps. Task status is stored as the literal string constants in `TaskStatus`, not an enum.
- Reminder configuration and pending-check-in runtime state use separate DataStores: `reminder_settings` and `reminder_runtime_state`.

## Reminder safety

- Keep `ReminderScheduler`'s alarm actions and request codes stable: regular `3001`, test `3002`, retry `3003`. Changing either prevents previously scheduled alarms from being found and cancelled.
- Route ordinary reminder state changes through `ReminderCoordinator.reconcile(context, reason)`; use its `acknowledgeCheckIn` or `stopEverything` paths for those specific transitions. The coordinator applies settings, notification-permission, active-window, and unfinished-task checks together.
- A regular alarm posts a notification, marks a pending check-in, then schedules retries at the missed-reminder interval until acknowledgement. `BootCompletedReceiver` restores state for both `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED`.
