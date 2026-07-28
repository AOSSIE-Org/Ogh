# Ogh

Ogh is a local-first Android broadcasting app. It captures a screen or camera,
combines the selected audio, encodes once, and publishes to one or more enabled
RTMP destinations. It has no ads, analytics, watermarks, Ogh account, hosted
media relay, or embedded client secret.

Ogh is meant to work without Google Play Services: manual RTMP is fully local,
YouTube authorization uses the system browser with PKCE, and Twitch uses its
public device flow. A capable browser is still required for provider sign-in.

The name **Ogh** comes from Sanskrit *ogha* (ओघ): a stream, current, torrent, or
continuous flow. It is pronounced approximately “ohg.”

## Features

- Full-screen camera preview plus screen, back-camera, or front-camera video.
- Natural-aspect camera preview whose viewport follows the preview surface and
  whose camera image remains physically upright independently of Android
  auto-rotate.
- Camera streams use a stable landscape encoder canvas. Physical device
  orientation keeps the encoded camera image upright, while preview orientation
  is corrected independently so the local viewfinder remains stable.
- Screen/camera and front/back camera switching during a live session without
  rebuilding healthy RTMP outputs.
- Microphone and system-audio controls may be changed while live. Enabling a
  new capture capability may request RECORD_AUDIO permission or MediaProjection
  consent. A denied or failed change leaves the previous audio source active.
- Simultaneous manual RTMP, YouTube, and Twitch destinations through one encode
  pipeline. Use RTMPS whenever the destination supports it. RTMP does not encrypt the
  stream or stream key while they travel across the network.
- **Hide Video** without ending the broadcast or its audio: Ogh shows its logo
  or a selected local image until video is shown again.
- Persistent video, audio, pause-image, and stream-metadata settings.
- Dropdown presets from 360p through 2160p, 15–60 fps, and 0.8–35 Mbps.
- Android Keystore encryption for OAuth tokens and manual stream keys.
- Foreground streaming with explicit Android capture indicators.

Playback audio requires Android 10 or newer and the source app must permit
capture. Ogh supports Android 7.0 (API 24) and newer.

## Install

Tagged releases publish a signed APK and SHA-256 checksum on GitHub Releases.
Normal CI runs also upload their tested debug APK as a workflow artifact. To
build a debug APK locally:

```bash
nix develop --command ./gradlew :androidApp:assembleDebug
```

The APK is written under `androidApp/build/outputs/apk/debug/`.
Maintainers can create a release from **GitHub Actions → Release → Run
workflow** after configuring the signing secrets in
[CONTRIBUTING.md](CONTRIBUTING.md).

## Configure and use

Manual RTMP needs the server URL and stream key supplied by the destination.
Maintainers enabling built-in account connections must register the tracked
public client IDs described in [OAUTH_SETUP.md](OAUTH_SETUP.md). End users do not
provide an application client secret.

1. A fresh install opens Settings. Connect YouTube or Twitch, or add a manual
   RTMP target; Preview becomes available once a destination exists.
2. In Settings, enter stream details and choose quality, orientation, and the
   optional pause image.
   Choose stream quality and whether the app interface orientation is locked.
   The orientation lock affects the interface only, not camera-frame orientation.
3. Open Preview, choose **Screen** or **Camera**, select **Microphone** and/or
   **System Audio**, then tap **Go Live** and grant the permissions Android
   requests.
4. While live, use **Screen**, **Camera**/**Flip Camera**, **Hide Video**/**Show
   Video**, or **Stop**.

The Settings hierarchy is hidden from preparation until the session stops.
Accounts, destinations, metadata, encoders, pre-live audio selections, and
pause-image settings do not change mid-session; only the dedicated full-frame
source controls remain available.

When Screen is live, Preview shows the captured feed normally; selecting Ogh's
own window can therefore produce the familiar recursive screen-share view.

## Develop

Nix is the supported project environment:

```bash
nix develop
./gradlew :shared:jvmTest :androidApp:testDebugUnitTest
./gradlew :androidApp:lintDebug :androidApp:assembleDebug
```

For the end-to-end suite, start an emulator or connect a device, install the
debug APK, and run:

```bash
maestro test .maestro
```

The suite exercises initial setup, Preview/Settings navigation, destination
CRUD, settings persistence, and provider authorization-page loading. It does
not grant account consent or start a public broadcast; those checks require
dedicated test accounts and an appropriately configured device.

## Architecture

Ogh has two Gradle modules:

- `shared/`: Compose Multiplatform screens, theme, navigation model, in-app
  brand mark, domain models, destination state, endpoint planning, and RTMP
  validation. Its common code imports no Android APIs and is compiled for both
  Android and JVM.
- `androidApp/`: the Android entry point and lifecycle-aware UI adapter, capture
  service, permissions, persistence, OAuth, provider clients, and launcher or
  notification resources.

The bound capture service uses one RootEncoder pipeline for camera preview and
live encoding; `MultiStream` publishes that one feed to each enabled
destination. Android DataStore persists non-secret settings.
Sensitive local records are encrypted with AES-256-GCM keys held by Android
Keystore.

The CI build creates the debug APK once; the emulator job downloads and tests
that exact artifact. Tag releases require repository signing secrets and fail
instead of publishing an unsigned or debug-signed fallback.

## Documentation

| Document | Purpose |
|---|---|
| [AGENTS.md](AGENTS.md) | Repository engineering and documentation rules |
| [SPECIFICATION.md](SPECIFICATION.md) | Current product and architecture contract |
| [DECISIONS.md](DECISIONS.md) | Significant technical decisions and tradeoffs |
| [OAUTH_SETUP.md](OAUTH_SETUP.md) | Maintainer registration for provider clients |
| [PRIVACY.md](PRIVACY.md) | User-facing data and retention policy |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Development and review workflow |
| [CHANGELOG.md](CHANGELOG.md) | User-visible release changes |

## Frequently asked questions

### Are OAuth client IDs secrets?

No. Native/public client IDs identify Ogh and are recoverable from every APK.
Client secrets, user tokens, stream keys, and signing keys are secrets and must
not be tracked or embedded.

### Does Ogh need application-owned secrets?

It needs no confidential runtime/API credential in the APK. A capability that
requires one is outside the APK-only architecture. Maintainers do need a
private Android signing key to publish updateable APKs, but that build-time key
stays outside the repository and is not embedded in the installed application.

### Where does media go?

Directly from the Android device to the destinations the user enables. Ogh does
not run an intermediary server.

## Contributing

Working pull requests are preferred over feature-request-only issues. Keep a PR
focused, follow [CONTRIBUTING.md](CONTRIBUTING.md), and complete the repository's
full regression checklist, marking an item not applicable only with a reason.
Bug reports with reproducible steps remain welcome.

## License

[Apache License 2.0](LICENSE.txt)
