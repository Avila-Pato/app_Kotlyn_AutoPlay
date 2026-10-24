# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

AutoPlay: a single Android app (module `autoapp`, package `org.example.autoplay`) that connects phones to a car head unit/tablet so phones can play music through the car without waiting ads or accounts. The same APK runs in two roles (`DeviceRole.Car` / `DeviceRole.Phone`), chosen by the user or auto-detected from hardware (`DeviceRoles` in `DeviceRole.kt`). Code comments and UI strings are partly in Spanish.

## Commands

Gradle wrapper, Kotlin DSL; run from the repo root (on Windows use `gradlew.bat` or `./gradlew` in Git Bash).

- Build debug APK: `./gradlew :autoapp:assembleDebug`
- Install on device: `./gradlew :autoapp:installDebug`
- All unit tests: `./gradlew :autoapp:test` (JUnit 5 / Jupiter, `useJUnitPlatform`)
- Single test: `./gradlew :autoapp:test --tests "org.example.autoplay.SessionTest"` (append `.methodName` for one method)

No lint/formatter is configured. Tests live in `autoapp/src/test/kotlin`; sources use `src/main/kotlin` (not `java`).

## Build setup

- `buildSrc` only supplies plugin classpaths (Kotlin, Compose compiler, AGP, Hilt, KSP) from `gradle/libs.versions.toml`. Plugin versions are changed there; library dependencies (Compose BOM, CameraX, ZXing, Hilt, coroutines) are declared inline in `autoapp/build.gradle.kts`, with Hilt's version duplicated in both places.
- compileSdk/targetSdk 37, minSdk 26, JVM 17. DI is Hilt via KSP; UI is Jetpack Compose + Material3.

## Architecture

Packages by role, glued by a shared protocol:

- `net/` — transport and wire protocol, used by both sides. `Protocol.kt` defines the sealed `Message` hierarchy (Hello/Resume/Accepted/Roster/Volume/Audio/AudioAac/media-control, etc.) and its framing: `[type: 1 byte][payload length: 4 bytes][payload]`. `Protocol.VERSION` must be bumped when the wire format changes. `Connection` abstracts TCP (Wi-Fi) vs RFCOMM (Bluetooth) so the rest of the code is transport-agnostic. `CarDiscovery` does mDNS (`_autoplay._tcp.`, default port 47800); `AacCodec` encodes/decodes audio (PCM 48 kHz stereo 16-bit, or AAC 128 kbps).
- `car/` — server side. `CarServer` accepts Wi-Fi and Bluetooth connections, authenticates (6-digit session code from `session/SessionCode`, a resume token, or a per-phone trust token persisted by `TrustedPhones`), asks the driver to approve new phones (`JoinRequest`), keeps the priority-ordered device roster, and decides which phone is "playing" (first online one that shares audio). `AudioPlayer` plays incoming audio; `CarService` keeps the server alive as a foreground service; `CarViewModel`/`CarScreen` are the UI. The driver can mute a phone or lock volume.
- `phone/` — client side. `PhoneClient` connects to the car, `PhoneService` is the foreground service holding the connection, `AudioCapture` captures device audio (MediaProjection) and streams it to the car, `PhoneMedia` plus the notification-listener service (`MediaListener`) report the current track and relay play/pause/skip, `CarVolumeRouter` maps phone volume to car volume, `KnownCars` remembers cars, `QrScanner` (CameraX) reads the join QR.
- `session/JoinLink` — QR contents: `autoplay://join?h=<host>&p=<port>&c=<code>`. The car renders it (ZXing, `ui/QrCode.kt`); the phone parses it.
- `ui/` — shared Compose components/theme/home screen; `MainActivity` routes to the car or phone screen by role. Supports Picture-in-Picture.

Two join modes: Wi-Fi via QR/discovery, and classic Bluetooth, where pairing replaces the session code. The manifest requests many runtime-sensitive permissions (RECORD_AUDIO, CAMERA, Bluetooth, foreground-service types for media projection/playback/connected device, notification listener); changes touching services should keep the manifest's foreground-service types in sync.
