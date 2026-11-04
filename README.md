# OneRemote

A native Android universal remote for Indian living rooms: **Airtel Digital TV HD box** (IR),
**LG webOS TV** (Wi-Fi SSAP with IR fallback) and **Sony Bravia** (Wi-Fi IRCC-IP + ScalarWebAPI
with IR fallback, PSK auth). One screen controls the TV and the set-top box at the same time, any
number of rooms, and the remote stays reachable from the notification shade, Quick Settings, the
phone's volume keys, home-screen widgets, app shortcuts, a floating bubble and voice.

LAN-only. No account, no ads, no analytics, no cloud. Package `in.s123dive.oneremote`.

## Build

* Android Studio with SDK 36 (or `./gradlew :app:assembleDebug`). Versions live in `gradle/libs.versions.toml`.
* Unit tests: `./gradlew :app:testDebugUnitTest` (155 tests: protocol encoders, parsers, router, voice phrases …).
* Release: `./gradlew :app:assembleRelease` (R8 + resource shrinking). To sign with your own key, create
  `keystore.properties` in the repo root:

  ```
  storeFile=release.jks
  storePassword=…
  keyAlias=oneremote
  keyPassword=…
  ```

  Without that file the release APK is signed with the debug key so it still installs.
* No Android SDK at hand? `tools/jvmcheck/run.sh` type-checks all Kotlin and runs the tests on a plain JVM.

The debug build has the application id `in.s123dive.oneremote.debug` (note this for the Intent API).

## TV-side setup

**LG webOS**
* Pair once: the app connects (`ws://tv:3000`, then `wss://tv:3001` on newer firmware) and the TV asks
  "Allow this remote?". Choose Yes. The client-key is stored, so there is no second prompt.
* Power-on over Wi-Fi needs Wake-on-LAN: Settings → General → *Mobile TV On* / *Turn on via Wi-Fi*
  (name varies by year). The MAC is read from the TV at pairing when it exposes it; otherwise enter
  it in the device settings. "Test power-on" there proves it.
* Everything else (volume, channels, inputs, apps, pointer touchpad, typing) works over Wi-Fi; when
  the TV is unreachable the app falls back to IR automatically (LG NEC address 0x04) if the phone has
  a 38 kHz blaster.

**Sony Bravia (Linux models such as KDL-43W6600)**
* Settings → Network → Home Network → **IP Control**: Authentication = *Normal and Pre-Shared Key*,
  set a PSK; *Simple IP control* = On; **Remote start** = On (power-on over Wi-Fi).
* The app checks `getSystemInformation` first and tells you exactly what is wrong: *wrong PSK* or
  *IP Control not enabled / unreachable*. It then downloads the TV's own IRCC key list (nothing is
  hard-coded) and probes `setTextForm`; typing is offered only when the TV supports it.
* IR fallback: SIRC @ 40 kHz, address 1 (and the address-26 media keys as a second code set).

**Airtel Digital TV HD box (IR only)**
* Add device → the wizard sends each candidate's Power code once a second; tap *It responded* when
  the box reacts. Candidates: Airtel STB2 (extended NEC 0xA1DE, from Flipper-IRDB), Airtel S8300
  (raw captures), then a list of other set-top-box NEC Power codes from probonopd/irdb.
* Expert mode: type the NEC address and a command byte per key, test live, save. The phone cannot
  learn IR codes (no receiver), so this is the manual path.
* On a phone without an IR blaster the Airtel remote is greyed with "This box is IR-only; this
  phone has no IR blaster". Sony IR fallback is disabled on blasters that cannot do 40 kHz.

## Intent API (Tasker, Automate, adb)

```
adb shell am broadcast -a in.s123dive.oneremote.SEND -p in.s123dive.oneremote \
    --es room "Hall" --es role TV --es cmd VOL_UP
adb shell am broadcast -a in.s123dive.oneremote.SEND -p in.s123dive.oneremote --es macro "Watch TV"
```

* `room`: room name or id (default: last-used room). `role`: `TV` | `STB` | `EXTRA` (default `TV`).
* `cmd`: a command key — `POWER`, `POWER_ON`, `POWER_OFF`, `VOL_UP`, `VOL_DOWN`, `MUTE`, `CH_UP`, `CH_DOWN`,
  `DIGIT:5`, `CHANNEL:120`, `OK`, `BACK`, `HOME`, `MENU`, `GUIDE`, `INFO`, `EXIT`, `UP`/`DOWN`/`LEFT`/`RIGHT`,
  `INPUT`, `INPUT_SELECT:HDMI_1`, `PLAY`/`PAUSE`/`STOP`/`REW`/`FWD`, `RED`/`GREEN`/`YELLOW`/`BLUE`,
  `LAUNCH_APP:youtube.leanback.v4`, `TEXT:hello`, `POINTER_MOVE:3,4`, `POINTER_CLICK`, `SCROLL:0,5`, `RAW:0408`.
* `macro`: a macro name in that room (`Watch TV`, `All off`, or your own).
* Use `-p in.s123dive.oneremote.debug` for debug builds. Results appear in Settings → Debug console.

## Voice

Phone-side speech only (Android `SpeechRecognizer`, on-device when the phone has it). English, Hindi /
Hinglish and Marathi: "channel 120", "channel ek sau bees", "volume badhao", "awaaz band", "चॅनल पाच",
"open YouTube", "search cricket", "switch to HDMI 2", "sleep in 30 minutes", "watch tv", "sab band".
The transcript and the parsed action are always shown; anything else says "Didn't get that".
Neither TV exposes a native voice search, and the app does not pretend otherwise.

## Debug console

Settings → Debug: every send (time, room, role, command, transport, payload such as `NEC 0x04/0x08` or
`ssap://audio/volumeUp`, result, latency), filters, export via the share sheet; IR capability and a
carrier test (LG Power @ 38 kHz, Sony Power @ 40 kHz); discovery results with raw SSDP; device info
(webOS version, Sony model/firmware, Airtel code set id).

## Known limitations

* QR backup transfer is not implemented: it needs a camera/QR library outside the approved dependency
  list. Use Backup → Share / file and Import on the other phone (secrets can be stripped from exports).
* LG "Input" over Wi-Fi: SSAP has no call that opens the input picker, so that key falls back to IR;
  the Inputs chips switch inputs directly over Wi-Fi.
* Airtel code sets come from public databases; the S8300 raw set and the irdb scan list are unverified
  on real boxes. Expert mode covers the rest.
* Sony `setTextForm` and the address-26 SIRC keys are unverified on the KDL-43W6600.
* Bubbles need Android 11+; older versions get an overlay window that needs "Display over other apps".
* This codebase was written where the Android SDK could not be downloaded: all Kotlin type-checks on a
  JVM harness and the unit tests pass, but the first real `assembleDebug` may surface AndroidX version nits.
