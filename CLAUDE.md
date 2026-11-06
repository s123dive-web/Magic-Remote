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
- [x] P5 always-available+automation - [x] P6 voice - [x] P7 bubble+release
All phases are written and type-checked; none has run on real hardware yet (see "Hardware facts").

## Layout (single module `app`, package `in.s123dive.oneremote`)
- `core/` Command, Capability, DeviceInstance, Device/Transport, Room, Rooms (pure room edits), Macro, Log, AppState, Router
- `ir/` IrCapability, IrPort/AndroidIrPort, IrTransmitter (serialised), NecEncoder, SircEncoder, IrCode, IrTransport,
  CodesetScanner, `codesets/` (LgNec, SonySirc, Airtel STB2 + S8300 raw + irdb scan list, Codesets registry)
- `net/` Ssdp (pure), Discovery (SSDP + /24 probe), WakeOnLan, Arp, Http (3 s clients, per-host trust for wss)
- `lg/` SsapMessages (pure), LgClient (ws:3000→wss:3001, hello→getSystemInfo→register, pointer socket), LgTransport
- `sony/` SonyApi (pure JSON-RPC/SOAP builders+parsers), SonyClient (typed outcomes), SonyTransport
- `devices/` DeviceConfig (typed config keys), DeviceFactory (driver cache), WifiDrivers (LG/Sony registration + persistence)
- `data/` AppStateJson, AppStateStore (DataStore JSON), AppRepository, SettingsStore (Preferences), Backup
- `automation/` Macros (built-ins), MacroRunner, SleepTimer(+Receiver), IntentApi (+ pure IntentRouting)
- `voice/` IntentParser (pure), VoiceController (SpeechRecognizer), VoiceActivity
- `system/` CapabilityDetector, RemoteActionReceiver, RemoteNotificationService, RemoteTileService (TvPower/Mute),
  RemoteWidget (2x2/4x2), ActionActivity (shortcuts), BubbleLauncher, OverlayService
- `ui/` OneRemoteApp (NavHost), scene/ (SceneScreen dual-pane + pager, SceneViewModel, SceneAutomationRow),
  remote/ (RemoteButton with haptics+auto-repeat, pads, DeviceRemoteScreen/Page, TvExtras touchpad/text/apps),
  setup/ (AddDevice, CodesetScan, ExpertCodes, FindDevices, LgPair, SonyPair, DeviceSettings, WifiDeviceSection),
  rooms/, macros/, settings/ (Settings, Backup), debug/, bubble/ (MiniRemote, BubbleActivity)
- `AppGraph` hand-wired singletons; `OneRemoteApplication` owns it.
- Unit tests: `app/src/test` (JUnit4 + coroutines-test), 189 tests, pure code only.
- `tools/jvmcheck/` type-check harness for machines without the Android SDK (`run.sh`). Its `render` task (CI only,
  workflow "Render remotes") draws every physical remote through `FitScale` at OnePlus Open cover/inner sizes and
  publishes PNGs on the `renders` pre-release; check them after any layout change.

## Rules that are enforced in code
- Every send goes through `Router.send` (or `Router.diagnostic` for carrier test / pairing / scans) and lands in `SendLog`.
- A transport declares `capabilities`; the Router skips transports that cannot carry a command and
  returns `RouteResult.Unsupported` with a human-readable reason. Never a silent no-op. Wi-Fi first, IR fallback.
- `IrTransmitter` is the only caller of the IR HAL; sends are serialised; it refuses with the reason when
  there is no blaster or the carrier is outside the reported ranges.
- Every network call has a 3 s budget (OkHttp `Http.plain`; SSAP requests time out at 3 s; discovery probes 300 ms–1.5 s).
- Secrets (LG client-key, Sony PSK) live only in `DeviceInstance.config` inside DataStore; `allowBackup=false`;
  Backup export warns and can strip them.

## Decisions
- Sony auth: PSK only (no PIN flow). IRCC codes are fetched from the TV (`getRemoteControllerInfo`) and cached in config.
- LG power-on: Wake-on-LAN; ws:3000 -> wss:3001 auto-detect; wss trusts the TV's self-signed cert for that host only.
  Register manifest = pywebostv/lgtv2 signature + the spec permission list; hello and getSystemInfo precede register.
- LG Input key over Wi-Fi is deliberately unsupported (no SSAP picker call) so the Router falls back to IR 0x0B.
- Airtel codes: DS (irplus, Nokia32 38.0.38) first, STB2 (Flipper-IRDB, NECext 0xA1DE), S8300 raw, then irdb NEC STB Power codes; expert mode + key finder.
- Expert mode (`ui/setup/ExpertCodesScreen.kt`) is a protocol code lab: pick NEC/NECext/Nokia32/RC5/RC6/SIRC12-15-20/Samsung32,
  type address+command, or paste Pronto hex / Flipper `.ir` text (`ir/CodeText.kt`); Send once, Sweep commands from the typed
  base code (`NecSweep.commandSweepFor`), then assign the reacting code to a key (stored as `customCodes`; creates the device
  on first save). `IrCode.Samsung32` + `SamsungEncoder` exist for pasted codes; no device uses them by default.
- Kotlin package: `in` is a keyword, so sources use ``package `in`.s123dive.oneremote...``; manifest/applicationId plain.
- Persistence: one `AppState` JSON document in DataStore (lenient read, `type` discriminator, `version`); prefs in Preferences.
- Corrupt `app_state.json` is replaced with an empty state AND logged (role STORE).
- IR carrier ranges: unknown ranges are optimistic; the Debug carrier test is the truth.
- SIRC press = 3 frames @ 45 ms; NEC press = one frame ending in the 562 µs mark.
- Built-in macros are regenerated per room (ids `builtin-watch-<room>` / `builtin-off-<room>`); user edits survive.
- Watch TV switches the TV to the box's input only when the TV config has `stbInput` (set in device settings).
- Volume keys control the current room's TV only while the app is in front (Activity key events); hold = Mute.
- Notification remote = foreground service type `specialUse` (Android 14+) with a custom RemoteViews layout.
- Bubble: Android 11+ Bubbles API (conversation notification + shortcut); Android 8–10 overlay window (SYSTEM_ALERT_WINDOW, lazy).
- QR backup NOT implemented: needs a camera/QR dependency not on the approved list — ask the user before adding one.
- Lint: `abortOnError=false` so a lint finding cannot block a release build; the report is still produced.
- Dark theme always. Debug diagnostics: role `DEBUG`, room `-`.

## Field findings (2026-11-05, OnePlus Open)
- LG pairing: both ws:3000 and wss:3001 failed (timeout) on the first build; now ws/wss open in parallel, TLS 1.2 +
  all ciphers, no permessage-deflate, 8 s open budget, errors per port, Diagnose button logs TCP/HTTP/TLS/WS stages.
- Wi-Fi transports have a 15 s circuit breaker: after a failed connect, sends fail instantly so the Router falls to IR
  at once (previously every TV key waited 7–20 s). PowerOn bypasses the breaker (WoL).
- DeviceFactory rebuilds a driver only when ip/psk/codeset/custom codes change (driverSignature), so the client-key
  and model/firmware the TV reports no longer tear down the connection that just paired.
- NEC presses are frame + repeat (108 ms period) like a real remote; the Airtel known code sets did not react on the
  user's box, so the wizard gained a deep Power sweep (24 common commands × 256 addresses, 350 ms) with a verify step
  (replays the last 4 codes) and a per-key command finder (256 commands on the found address).
- Per-device remotes mirror the physical remotes (Airtel DTH remote, LG AN-MR19BA, Sony RMT-TX) from the user's photos.
  `FitScale` takes only a design width; it measures the remote's natural height and scales to fit (build 15 shifted the
  remote over the tabs because an oversized child was centred inside a smaller Box; build 17 measures instead).
- 2026-11-06: the Atomberg fan worked first time (proves the NEC-extended path and Flipper byte order). The Airtel box
  is NOT NEC: irplus `AIRTEL/DS.xml` is Nokia32 (36 kHz, D=38 S=0 X=38; digits = functions 0–9), decoded into
  `AirtelDsCodeset` and placed first. Nokia32/RC5/RC6 sends alternate the toggle bit (`IrToggle`). Deep sweep starts
  with a Nokia32 power pass. Scene tabs list every device in the room (incl. unreferenced) in a scrollable row.

## Atomberg BLDC fan (IR) and how to add another IR device
- Type `DeviceType.ATOMBERG_FAN`; commands `FanSpeed(1..5)`, `Boost`, `Timer`, `Light` (+ `Power`, `Sleep`) under `Capability.FAN`.
- Codes live in `ir/codesets/AtombergCodeset.kt` (NEC extended 0xF300; from Flipper-IRDB `Fans/Atomberg/Atomberg_fan.ir`,
  single public capture, UNVERIFIED on the user's unit). Overrides per key: device settings → "Learn / override codes"
  (`ui/setup/OverrideCodesScreen.kt`) store `customCodes` in the device config; override wins via `DeviceConfig.effectiveCodeset`.
- Screen: `ui/remote/layouts/AtombergRemote.kt` (one-hand; highlights are local state, IR is one-way). Added from
  Add device directly (no scan); appears as a room extra in the Scene tabs; greyed with `fanMessage` without a blaster.
- Voice: `VoiceIntent.Fan` ("fan on/off", "fan speed 3", "pankha band karo", "पंखा चालू") → room's fan via Router.
- Intent API: `--es role EXTRA --es cmd FAN_SPEED:3` (EXTRA = first extra of the room; the fan is one if it was added first).
- Recipe for the next IR-only device: add a `DeviceType`; add commands/capability if the keys are new; write a
  `Codeset` object (data) and register it in `Codesets.all` / `scanCandidates` / `defaultFor`; add the type to
  `SetupViewModel.assign`, `AddDeviceScreen`, the `when`s in `DeviceRemoteScreen` (design size + layout) and
  `RemoteViewModel.unavailableReason`; add a layout under `ui/remote/layouts/`; add `overrideKeysFor` keys. No transport
  or persistence work is needed: `IrTransport`, `DeviceFactory` and `AppState` are generic.

## Hardware facts learned (fill from user tests)
- Phone: OnePlus Open — IR present; 40 kHz support: ?
- LG model / webOS version: ?
- Sony KDL-43W6600: IRCC ok? setTextForm ok? WoL ok?
- Airtel HD codeset id: ? (the user's box is the HD HEVC "unlimited recording" model; STB2 did not react; RC5/RC6 sweep added)
- Atomberg fan: codes unverified

## Build / verification notes
- Written in a cloud container where `dl.google.com` (Android SDK, Google Maven, AGP) is blocked. The Android
  module has NOT been compiled with AGP here. All Kotlin (including Compose UI and Android components) type-checks
  in `tools/jvmcheck` against JetBrains Compose 1.6.11 + Robolectric android-all + small stubs, and 189 tests pass.
- AndroidX-only APIs are stubbed in the harness (DataStore, activity-compose, lifecycle-compose, navigation);
  their real signatures were matched from memory, so the first `assembleDebug` may need small fixes.
- To build: Android Studio with SDK 36; `./gradlew :app:assembleDebug`, `:app:testDebugUnitTest`, `:app:assembleRelease`.

## Open issues
- Nothing verified on devices yet: emulator "IR: none", carrier tests, LG pairing/pointer/WoL, Sony precheck/IRCC/WoL,
  Airtel code set, notification/tiles/widgets/bubble, voice recogniser availability per language.
- Pointer-socket button names GUIDE / digits ("1".."0") and LG `openChannel {channelNumber}` are from pywebostv/lgtv.js,
  unverified; digits fall back to pointer buttons + ENTER.
- Sony `IRCC_NAMES` alternates cover several firmware generations; unknown keys show as "not in TV's list".
