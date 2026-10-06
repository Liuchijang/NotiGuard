# NotiGuard

Fix delayed notifications on Xiaomi HyperOS China ROM. **FCM Guard** keeps GMS unrestricted,
plus toggleable background tweaks via [Shizuku](https://shizuku.rikka.app/).

## Why

HyperOS (PowerKeeper/Greezer) periodically rebuilds the vendor key
`Settings.System.MILLET_NO_RESTRICT_APP` and drops `com.google.android.gms` from it. GMS then
loses its FCM connection and push notifications arrive late or not at all.

## Features

- **FCM Guard** (no root, no Shizuku): watches `MILLET_NO_RESTRICT_APP` and re-adds GMS as soon as
  it is removed. Existing entries are kept. After a repair it sends an MCS heartbeat so FCM
  reconnects. Restarts after boot, and can run with or without a persistent notification.
- **FCM app scanner**: lists apps that use FCM/GCM and shows their HyperOS Autostart state (read-only).
- **System tweaks** (Shizuku): disable App Freezer, Doze, adaptive battery and App Standby, allow
  wakelocks for GMS, and more. Each tweak has an on/off switch that reflects the real device state,
  a one-line description and a battery impact tag. Turning a tweak off restores the ROM default.
- **No-restrict list editor**: view `MILLET_NO_RESTRICT_APP` and add or remove apps (no Shizuku
  needed). GMS stays pinned.
- **App check**: one line per app (issues first) covering notification permission, Autostart,
  background run, battery optimization (Doze whitelist), standby bucket, background data and more.
  Fixes and shortcuts to the matching settings page are one tap away. Works partly without Shizuku.
  Pop-up and lock-screen permissions are marked optional (only needed for incoming-call screens).
- **Debloat**: remove known Chinese bloatware for the current user, with restore.
- Every change is verified by reading the value back from the device. Without Shizuku, tweak states
  that are plain settings are still shown (read-only).
- First-run setup screen that grants each permission from its system settings page.
- Monochrome UI inspired by Nothing OS, in English or Vietnamese (VI | EN switch in the top bar).

## Requirements

- Xiaomi / Redmi / POCO phone on HyperOS (China ROM), Android 8.0+.
- Shizuku for tweaks and fixes. FCM Guard, the no-restrict list editor and part of the app check
  work without it.

The app targets **SDK 22** on purpose: legacy target apps get `WRITE_SETTINGS` on install and can write
the non-public vendor key without root. Do not raise `targetSdk`. Xiaomi China ROMs install it
directly. Stock Android 14+ needs `adb install --bypass-low-target-sdk-block`.

## Build

Requires JDK 17+ and Android SDK 35. Create `local.properties` with `sdk.dir=<path to SDK>`, then run:

```sh
./gradlew :fcmcore:testDebugUnitTest :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/`. AGP warns that minSdk (26) > targetSdk (22).
This is expected.

## Project layout

- `app/`: Kotlin + Jetpack Compose app
  - `fcmguard/`: FCM Guard service, boot receiver, scanner
  - `shizuku/`: Shizuku user service and command runner
  - `data/TweakCatalog.kt`: all tweaks and checks. To add a tweak, add a `Tweak` here.
  - `ui/`: screens and components
- `fcmcore/`: FCM list logic, with unit tests

## Credits

FCM Guard is ported from [FCMGuard-HyperOS](https://github.com/ReedGAOOO/FCMGuard-HyperOS)
(MIT, © Yijie Gao). See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). The original idea comes
from [hyperos-fcm-fix](https://github.com/dingwen07/hyperos-fcm-fix).
