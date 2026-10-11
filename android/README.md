# Mirage Android

Android VPN client for [Mirage](../README.md). This is the Android counterpart to the `apple/` client and shares the same Rust core.

## Overview

- Lives in `android/`. It drives the same Rust core as the iOS app, through the `mirage-ffi` crate. The crate is compiled to a native `.so` and called over JNI.
- Single-screen Jetpack Compose UI. Paste a TOML config, tap **Connect** or **Disconnect**, and watch live status and metrics: uptime, bytes sent and received, and the active protocol.

## Architecture

```
 +---------------------------+         +-----------------------------+
 |  MirageVpnService         |  JNI    |  mirage-ffi (Rust)          |
 |  (android.net.VpnService) | ------> |  com.mirage.vpn.MirageNative|
 |                           | <------ |  src/android.rs             |
 |  TUN fd <-> packet pump   | callback|  shared runtime (same as iOS)|
 +---------------------------+         +-----------------------------+
```

- **`mirage-ffi`** (Rust) exposes the JNI functions on `com.mirage.vpn.MirageNative`: `nativeCreate`, `nativeStart`, `nativeSendPacket`, `nativeStop`, `nativeDestroy`, `nativeGetStatus`, `nativeGetMetrics`, `nativeGetActiveProtocol`, and `nativeLastError`. The Android-specific code lives in `mirage-ffi/src/android.rs`, gated to `target_os = "android"`. It reuses the same runtime the iOS build uses.
- **`MirageVpnService`** is a Kotlin `android.net.VpnService`.
  - On the native `onTunnelConfig` callback, it builds the TUN with `VpnService.Builder`, using the addresses, routes, DNS, and MTU sent by the server.
  - It calls `addDisallowedApplication(<own package>)` so the Rust client's own socket to the server bypasses the tunnel. This prevents a routing loop. iOS does this automatically. On Android it must be done explicitly.
  - It then pumps packets between the TUN file descriptor and Rust.
- **Packets**
  - A reader thread reads the TUN fd and calls `nativeSendPacket`.
  - Rust delivers outbound packets through the `onPacketWrite` callback. The service writes them back to the TUN fd.

## Requirements

- **Android SDK** with **NDK r26** (`26.3.11579264`)
- **JDK 17**
- **Rust** (stable) with these targets:
  - `aarch64-linux-android`
  - `armv7-linux-androideabi`
  - `x86_64-linux-android`
- **cargo-ndk**
- **cmake**, **golang**, and **nasm**. BoringSSL is built from source and needs them.
- `ANDROID_NDK_HOME` and `ANDROID_SDK_ROOT` must point at the NDK and SDK so `cargo-ndk` can find the NDK.

Build targets:

- `minSdk` 24
- `targetSdk` and `compileSdk` 34
- ABIs: `arm64-v8a`, `armeabi-v7a`, `x86_64`

## Building

### Option 1: GitHub Actions (recommended)

The `.github/workflows/android.yml` workflow cross-compiles the Rust `.so` and builds a debug APK. It runs on every push to the `android-app` branch and on pull requests. It can also be started manually with **Run workflow** (`workflow_dispatch`).

Download the APK from the run's **Artifacts** section, under `mirage-android-debug-apk`.

### Option 2: Local build

Run these commands from the repo root.

1. Install `cargo-ndk` and add the Rust targets:

   ```bash
   cargo install cargo-ndk
   rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
   ```

2. Build the native libraries. They go into `android/app/src/main/jniLibs/`, which is gitignored:

   ```bash
   cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 --platform 24 \
     -o android/app/src/main/jniLibs build --release -p mirage-ffi
   ```

3. Build the APK from `android/`:

   ```bash
   cd android
   ./gradlew assembleDebug
   ```

   The APK is written to `android/app/build/outputs/apk/debug/`.

The Gradle wrapper's `gradle-wrapper.jar` is not committed. If `./gradlew` reports that the jar is missing, generate it once:

```bash
gradle wrapper --gradle-version 8.9
```

Alternatively, open the `android/` folder in Android Studio, which regenerates the wrapper.

## Usage

1. Install the APK:

   ```bash
   adb install android/app/build/outputs/apk/debug/app-debug.apk
   ```

2. Open the app and paste your Mirage client config. It uses the same TOML format as `examples/client.toml`. The app ships a commented sample as its default.
3. Tap **Connect**. Android shows a VPN consent dialog the first time. Accept it.

While connected, the app runs as a foreground service and shows an ongoing notification.

## Configuration

The config is the standard Mirage client TOML. See [`examples/client.toml`](../examples/client.toml) for the full set of options, including:

- server host and port
- authentication
- transport protocols
- camouflage
- obfuscation
- network routes and DNS

## Limitations

- The debug APK is unsigned for release. It is meant for testing only.
- There is no Play Store packaging.
- Per-app split tunneling is limited to excluding this app. Arbitrary excluded routes are also limited: `excludeRoute` requires API 33 or later, so on Android 12 and older they are not available.
- The build has not yet run on CI. Treat the first CI run as the verification step.
