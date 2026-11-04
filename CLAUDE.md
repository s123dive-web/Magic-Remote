# OneRemote
Native Android universal remote. Device types: Airtel Digital TV HD box (IR/NEC), LG webOS TV
(SSAP WS + IR/NEC), Sony Bravia (IRCC-IP PSK + ScalarWebAPI + IR/SIRC; first unit KDL-43W6600, Linux).
Rooms = TV + optional STB; unlimited instances. Dual-pane scene controls both at once.
Always-available: volume keys, notification remote, QS tiles, widgets, shortcuts, bubble (P7).
Voice = phone-side STT (EN/HI/MR) -> IntentParser -> Router. Macros, sleep timer, Intent API.
Capability-driven: works with or without IR. Debug console logs every send.
Stack: Kotlin, Compose M3, minSdk 26, OkHttp, kotlinx.serialization, DataStore. No ads/analytics/cloud.
Workflow: phased, STOP after each phase, wait for "proceed". Phases 2 and 3 may run together if told.

## Status
- [x] P0 scaffold - [x] P1 IR+Airtel - [x] P2 LG - [x] P3 Sony - [x] P4 scene/rooms
- [x] P5 always-available+automation - [x] P6 voice - [ ] P7 bubble+release

## Layout (single module `app`, package `in.s123dive.oneremote`)
- `core/` Command, Capability, DeviceInstance, Device/Transport, Room, Macro, Log (SendLog ring buffer), AppState, Router
- `ir/` IrCapability, IrPort (HAL abstraction), AndroidIrPort, IrTransmitter (serialised), NecEncoder, SircEncoder
- `data/` AppStateJson (one Json config), AppStateStore (DataStore<AppState> JSON file), AppRepository, SettingsStore (Preferences)
- `system/` CapabilityDetector (IR + Wi-Fi/IP snapshot)
- `ui/` OneRemoteApp (NavHost: scene / settings / debug), scene/, settings/, debug/
- `AppGraph` hand-wired singletons; `OneRemoteApplication` owns it.
- Unit tests: `app/src/test` (JUnit4 + coroutines-test). Pure code only; nothing Android in tests.

## Rules that are enforced in code
- Every send goes through `Router.send` (or `Router.diagnostic` for the carrier test) and lands in `SendLog`.
- A transport declares `capabilities`; the Router skips transports that cannot carry a command and
  returns `RouteResult.Unsupported` with a human-readable reason. Never a silent no-op.
- `IrTransmitter` is the only caller of the IR HAL; sends are serialised with a Mutex; it refuses
  (with the reason) when there is no blaster or the carrier is outside the reported ranges.
- Secrets (LG client-key, Sony PSK) live only in `DeviceInstance.config` inside DataStore (`app_state.json`).
  `android:allowBackup="false"` so they never go to cloud backup.

## Decisions
- Sony auth: PSK only (no PIN flow)
- LG power-on: Wake-on-LAN; ws:3000 -> wss:3001 auto-detect
- Airtel codes: candidates + scan list + expert mode (phone cannot learn IR)
- Kotlin package: `in` is a Kotlin keyword, so every file declares ``package `in`.s123dive.oneremote...``
  and imports use the same backticks. Manifest namespace / applicationId stay plain `in.s123dive.oneremote`.
- Persistence: a single `AppState` JSON document in DataStore (kotlinx.serialization, `ignoreUnknownKeys`,
  `classDiscriminator = "type"`, `version` field). Small prefs in Preferences DataStore. No Proto.
- Corrupt `app_state.json` is replaced with an empty state AND logged to the send log (role STORE) so it shows in Debug.
- IR carrier ranges: if the HAL reports none, `IrCapability.rangesKnown=false` and `supports()` is optimistic;
  the Debug carrier test is then the source of truth.
- SIRC single press = 3 frames padded to 45 ms each; NEC single press = one frame ending in the 562 µs mark
  (`framePadded` gives the 108 ms version for repeats, used from P1).
- Dark theme always for now (a light option can be a Settings toggle later).
- Debug tag for diagnostic sends: role `DEBUG`, room `-`.

## Hardware facts learned (fill from user tests)
- Phone: OnePlus Open — IR present; 40 kHz support: ?
- LG model / webOS version: ?
- Sony KDL-43W6600: IRCC ok? setTextForm ok? WoL ok?
- Airtel HD codeset id: ?

## Build / verification notes
- P0 was written in a cloud container where `dl.google.com` (Android SDK, Google Maven, AGP) is blocked by
  network policy. The Android module therefore has NOT been compiled with AGP here. The pure-Kotlin parts
  (`core/`, `ir/` except AndroidIrPort, `data/AppStateJson`) and all 42 unit tests were compiled and run
  on a JVM harness (Kotlin 2.2.21, serialization 1.9.0, coroutines 1.10.2) and pass.
- To build: Android Studio (or `./gradlew :app:assembleDebug`) with SDK 36 installed. If AGP 8.10.1 /
  compileSdk 36 / Compose BOM 2025.06.01 need bumping for the installed Studio, change only `gradle/libs.versions.toml`.
- Unit tests: `./gradlew :app:testDebugUnitTest`.

## Open issues
- Android build unverified in-container (see above). First real build may surface Compose/AGP version nits.
- Emulator check "IR: none" and the on-device carrier test are pending the user's run.
