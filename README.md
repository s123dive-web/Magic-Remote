# OneRemote

Native Android universal remote for one or more rooms, each with an LG webOS TV or Sony Bravia
plus an Airtel Digital TV HD set-top box. LAN-only: no account, no ads, no analytics, no cloud.

Phase 0 (this build): project scaffold, core model, persistence, phone capability detection,
Debug console with IR carrier test. Device drivers and the dual-pane Scene remote follow in later phases.

## Build

Android Studio with SDK 36, or:

```
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Package / applicationId: `in.s123dive.oneremote` (Kotlin sources escape the `in` keyword with backticks).

## Where things are

See `CLAUDE.md` for the layout, the rules the code enforces, decisions and status.
