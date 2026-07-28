# Ogh Product and Implementation Specification

Status: current implementation contract

Last reviewed: July 18, 2026

## 1. Authority and scope

Ogh is a local-first Android application that captures one audiovisual feed,
encodes it once, and publishes it directly from the device to one or more
enabled RTMP destinations.

The checked-in source code, resources, manifests, build files, and tests are
the source of truth. This specification is a reconstruction-grade description
of that implementation. If it drifts, verify the code and tests and update this
file; do not change working behavior merely to make it match stale prose.

The current deliverable is the Android application. Its application ID,
namespace, OAuth redirect identity, and Maestro app ID are `com.ogh.app`.
Android 7.0/API 24 is the minimum version; compile and target SDK are 36.

The name Ogh comes from Sanskrit *ogha* (ओघ), meaning a stream, current,
torrent, or continuous flow.

## 2. Product invariants

These properties define Ogh:

- Media travels from the Android device to destinations selected by the user.
- Ogh operates no account server, media relay, analytics service, advertising
  service, or telemetry endpoint.
- A session has one full-frame video source and one optional encoded audio
  track.
- Capture and encoding happen once. RootEncoder `MultiStream` fans the encoded
  packets out to all enabled destinations.
- A manual RTMP destination and each connected provider destination can be
  enabled or disabled independently.
- Hiding video covers the full-frame source with a still-image filter; it does
  not stop capture, alter audio, or intentionally disconnect RTMP.
- OAuth clients are native/public clients. The APK contains public client IDs
  and no client secret.
- User settings survive process and device restart. MediaProjection consent
  never survives a session.
- Sensitive stored records fail closed into Android Keystore-backed
  encryption; there is no plaintext fallback.

## 3. User-visible behavior

### 3.1 Preview

Preview is the broadcast-control screen. A fresh install with no destination
opens Settings instead, and Preview remains unavailable until a provider or
manual RTMP destination exists. Preview contains:

- a full-screen surface for the selected source; screen sharing appears there
  while live and can naturally include Ogh recursively;
- the centered Ogh mark, a Settings action while idle, and a preparation/live/
  video-hidden badge;
- a compact status overlay with duration and bitrate during a session attempt;
- persistent **Screen** and **Camera** source controls;
- pre-live **Microphone** and **System Audio** selections; and
- **Go Live** while stopped, or **Hide Video**/**Show Video** and **Stop** while
  live.

Controls occupy two rows when the UI is portrait and one row when the UI is
landscape. Audio selections are available only before preparation; they remain
visible as selected/unselected buttons and persist like other settings.

There is no camera dropdown. **Camera** selects the first compatible facing;
when both front and back cameras exist, its selected-state label becomes **Flip
Camera** and each tap changes facing. The camera permission is requested before
preview or streaming begins. The preview uses fill scaling: it preserves the
camera aspect ratio and center-crops overflow instead of stretching or adding
bars. The camera preview is stable like a native camera viewfinder: it does not
rotate when the device is tilted or when the UI orientation lock differs from
the physical orientation. A service-owned cardinal-orientation tracker updates
RootEncoder's camera texture rotation for the stream, then applies an equal
counter-rotation to the preview through RootEncoder's separate preview rotation
path so the two cancel out and the preview stays visually fixed. RootEncoder's
preview portrait/landscape viewport flag follows the actual preview surface
dimensions instead of the physical sensor or encoded canvas.
The tracker remains active across preview surface detach/reattach, app reopen,
and screen sleep; 15-degree hysteresis around cardinal boundaries prevents
rapid flips during ordinary tilt.

With at least one enabled destination, **Go Live** is available. A tap
immediately changes it to a disabled **Preparing…** action with progress. Source
controls are also disabled during preparation so encoder setup cannot race a
source transition.

While `STREAMING` or `PAUSED`, Screen ↔ camera and front ↔ back changes use
RootEncoder's single existing video pipeline and request a keyframe; healthy
RTMP outputs and audio are not rebuilt. Switching from a camera-only session to
Screen requests MediaProjection consent if the session does not already hold
it. Rapid source changes are serialized and show a switching state.

From `PREPARING` through `STREAMING` and `PAUSED`, the Settings action and every
configuration screen are hidden. Only Preview and its live controls remain.
Destination, account, metadata, encoding, and pause-image mutations are
rejected below the UI. Source switching and audio toggles (Microphone, System
Audio) are the only live configuration exceptions and have dedicated
service/ViewModel paths.

#### Stream details and source

| Setting | Values/default | Use |
|---|---|---|
| Source | Screen or Camera; Screen default | full-frame capture source selected on Preview |
| Title | `Ogh Live` | provider broadcast title |
| Description | empty | provider description |
| Privacy | Public, Unlisted, Private; Private default | YouTube status |
| Latency | Ultra Low, Low, Normal; Normal default | YouTube latency |

Stream-detail fields live in Settings. Manual RTMP servers ignore metadata
because RTMP alone does not expose a common metadata-management API.

### 3.2 Manual destinations

Users can add, edit, enable, disable, and delete manual destinations. A record
contains:

| Field | Contract |
|---|---|
| ID | locally generated identifier |
| Name | trimmed, non-blank, at most 50 characters |
| RTMP URL | `rtmp://` or `rtmps://`, hostname, optional numeric port and path |
| Stream key | non-blank and contains no spaces, tabs, or newlines |
| Enabled | defaults to true |
| Color | hexadecimal display value |
| Type | `RTMP_MANUAL` |

The stream endpoint is the server URL plus exactly one slash plus the key,
unless the URL already ends in a slash. A delete action requires confirmation.
Only manual destinations are serialized to the manual-destination store.

### 3.3 Provider platforms

Platforms lists YouTube and Twitch. A provider is either disconnected, in a
connection operation, or connected with its locally retained account label.
Connecting creates exactly one provider-managed destination. Disconnecting
removes that destination and the local account record after attempting provider
revocation.

Provider destinations store no ingest key. Their `DestinationType` is
`YOUTUBE_OAUTH` or `TWITCH_OAUTH`, and their endpoint is resolved in memory for
each preparation.

### 3.4 Settings

Settings is the single scrollable configuration screen. It contains provider
connection or stream details, Platforms/Destinations/About navigation, and the
Video and pause-image controls. Every change is written through
`StreamViewModel` into a complete immutable settings snapshot. It is the initial
screen until a destination exists and is not reachable during a session.

#### Video

| Setting | Values/default |
|---|---|
| Resolution | 360p, 480p, 540p, 720p, 1080p, 1440p, 2160p; 720p default |
| Frame rate | 15, 24, 25, 30, 50, 60 fps; 30 default |
| Bitrate | 0.8, 1.5, 2.5, 4.5, 6, 8, 12, 20, 35 Mbps; 2.5 Mbps default |
| Lock orientation | enabled by default; locks only the app interface throughout its lifecycle |
| Keep screen awake | enabled by default |
| Pause image | bundled Ogh logo by default or a local document URI |

Resolution, frame rate, and bitrate use compact dropdowns. High-end presets
remain subject to the selected camera, hardware encoder, and destination; an
unsupported preparation fails visibly without starting a partial session.

Resolution labels use the conventional short edge. For a camera session, Ogh
samples physical orientation when preparation begins: landscape uses the listed
width×height, while portrait swaps it. The encoded canvas remains fixed until
the session ends because changing H.264 geometry requires encoder preparation.

Camera source means a full-frame camera broadcast. Ogh does not composite a
camera over a screen capture. Camera transforms use cached physical cardinal
orientation rather than Android display rotation. The preview viewport follows
the UI surface while its camera image follows physical orientation; live frames
remain upright and center-crop into the portrait/landscape canvas selected at
session preparation. Fill scaling preserves aspect ratio rather than stretching
or letterboxing. **Lock orientation** locks only the app interface as soon as
persisted settings load and remains effective whether idle or live. It does not
control camera capture. Screen capture orientation is handled by MediaProjection.

#### Audio

| Setting | Default | Behavior |
|---|---|---|
| Microphone | enabled | Preview button; captures the selected device microphone |
| System audio | enabled | Preview button; captures eligible app playback on Android 10+ |
| AAC bitrate | 128 Kbps | encoder setting, not exposed as a UI preset |
| Sample rate | 44.1 KHz | encoder setting, not exposed as a UI preset |
| Channels | mono | encoder setting, not exposed as a UI preset |

The two pre-live Preview selections produce microphone-only, playback-only,
mixed, or silent video. Resolution, frame rate, bitrate, destinations, and metadata remain locked after
preparation. Microphone and system-audio toggles are explicit live exceptions.
A live audio update is committed only after required permissions are available
and the service successfully changes its audio source. On Android 7–9, requested playback capture is unavailable; microphone
audio continues if selected. On Android 10+, Android only permits playback from
source applications whose audio-capture policy allows it.

Defaults apply to a fresh settings document. An existing user's persisted
choices are retained across upgrades.

### 3.5 Pause slate

**Hide Video** covers the current screen or camera with a fully opaque still
image at the encoder resolution. The underlying source stays alive so showing
video does not recreate camera or MediaProjection capture. The default image is
a centered, single-stroke, triangular spiral/play mark with a pink-to-purple
gradient on the dark Ogh background. A custom image is selected with Android
`OpenDocument`.

Ogh retains read permission for the selected URI and stores only its string
form. The document remains in its original provider; Ogh does not upload or
make a private copy. Selecting the logo releases the prior persisted URI grant.
If the document cannot be decoded, rendering falls back to the logo.

The bitmap uses bounded decoding, center-crop scaling, encoder dimensions, and
RootEncoder's GL filter API. Hide/Show clears or applies that single filter and
requests a keyframe so the change reaches viewers promptly. Audio is independent
and remains live with the source selected before preparation. **Show Video**
reveals the selected screen or camera without rebuilding capture or intended
RTMP connections.

### 3.6 About

About displays the application version, local-first/privacy summary, source and
issue links when a repository URL was derived at build time, and the Apache
License 2.0 notice. The build has no hardcoded repository fallback.

## 4. Navigation and visual system

Navigation is a shallow state machine rather than a navigation framework:

```text
Preview ↔ Settings
          ├── Platforms
          ├── Destinations
          │   └── Add/Edit Destination
          └── About
```

`MainActivity` owns the current `Screen`. Compose `BackHandler` and the visible
back action return to the preceding logical screen. OAuth redirects return to
the activity through AppAuth's exported redirect receiver.

Screens, the `Screen` enum, theme tokens, and the in-app Ogh mark are Compose
Multiplatform code in `shared/src/commonMain`. They accept immutable values and
callbacks. The Android activity is the platform entry point: it performs
lifecycle-aware flow collection and supplies intents, document selection,
permissions, build metadata, and other Android actions.

Navigation away from Preview is rejected while
`StreamState.locksConfiguration` is true. This keeps account, destination,
encoding, audio, metadata, and pause-slate inputs stable while the dedicated
live source controls remain usable.

Ogh uses one dark Material 3 theme:

- near-black `#0B0B0E` background;
- dark flat, border-free rounded surfaces with dividers where rows share a card;
- primary pink-purple `#D946EF`, light `#F0ABFC`, deep `#701A75`;
- pink-to-purple logo gradient from `#F43F9E` to `#8B5CF6`;
- restrained 2–10dp corner radii, no decorative elevation, and compact 16dp screen
  gutters; and
- green success, amber warning, and red error states that never rely on color
  alone.

Controls must retain Android semantics, readable contrast, keyboard/IME and
system-bar insets, screen-reader labels, and at least 48dp interaction targets.
Chips wrap on compact widths. Critical controls stay clear of navigation bars.

## 5. Session state and preparation

`StreamViewModel` exposes `StateFlow` values for stream state, status, stats,
destinations, video settings, audio settings, metadata, and settings-loaded
state.

```text
IDLE -> PREPARING -> STREAMING <-> PAUSED -> STOPPED
          |               |
          +---- ERROR <---+
```

- `IDLE`: actionable, no active session.
- `PREPARING`: duplicate starts rejected; permissions, provider endpoints,
  service startup, and encoder preparation may be in progress; configuration
  is locked.
- `STREAMING`: at least one output reported connection success.
- `PAUSED`: opaque still-image filter active while capture and streaming remain intended;
  configuration remains locked.
- `STOPPED`: publishing ended and session-only capture resources are cleared;
  a bound Preview host may prepare a fresh camera pipeline afterward.
- `ERROR`: current attempt failed; the UI remains available for correction and
  a later attempt.

The start sequence is ordered:

1. Validate that settings loaded and at least one destination is enabled.
2. Atomically enter `PREPARING` before any asynchronous work.
3. Request camera permission when a camera source is selected.
4. Request `RECORD_AUDIO` when microphone or playback audio is selected.
5. Request MediaProjection consent for screen video, or for playback capture on
   Android 10+.
6. Start the service with the exact required foreground-service types.
7. Resolve enabled provider endpoints with refreshed credentials.
8. Configure one `MultiStream` output per enabled endpoint.
9. Prepare the selected video source and audio source(s).
10. Connect all output indexes.

Cancellation or denial aborts preparation, clears short-lived provider
endpoints, releases partial service resources, and returns an actionable status.
Provider broadcasts are created only after capture consent to avoid abandoned
remote broadcasts when permission is cancelled.

One destination failure does not intentionally terminate healthy outputs. The
ViewModel reports terminal connection failure only after all configured outputs
have failed/disconnected. A user-requested stop is tracked so later disconnect
callbacks cannot overwrite the explicit stopped state with an error.

Starting multiple RTMP clients is transactional from the application's
perspective. If any destination throws during startup, clients already started
during that attempt are stopped and preparation fails.

## 6. Android runtime ownership

### 6.1 MainActivity

`MainActivity`:

- enables the Compose application and owns activity-result launchers;
- collects ViewModel, destination, and account flows with Android lifecycle
  awareness and supplies their current values to shared composables;
- constructs settings, destination, token, OAuth, and provider clients;
- restores manual destinations and connected accounts;
- handles camera, audio, notification, MediaProjection, and document-picker
  results;
- resolves provider endpoints off the UI thread;
- computes foreground-service type flags from the selected session;
- applies orientation and keep-awake behavior; and
- binds the service while visible for same-pipeline camera preview and
  coordinates permission-gated source requests without owning encoder
  resources.

### 6.2 StreamViewModel

`StreamViewModel` owns session-facing state and validation. It bridges UI
intent to the live service, delegates destination selection to the shared
endpoint planner, combines manual endpoints with short-lived provider
endpoints, implements RootEncoder `ConnectChecker`, updates live duration and
bitrate, and persists immutable settings snapshots. Destination and settings
mutations are rejected while configuration is locked. A guarded asynchronous
source-switch operation is exposed separately and disables repeated taps until
the service finishes the transition.

The shared destination repository is an in-memory state owner observed by
Compose. MainActivity synchronizes its manual subset to Android encrypted
storage and reconstructs provider destinations from connected account records.

### 6.3 ScreenCaptureService

`ScreenCaptureService` is non-exported and returns `START_NOT_STICKY`. It is
bound while the activity is visible so the same RootEncoder pipeline can render
a pre-live camera preview, and promoted to the foreground only for a broadcast.
A static in-process instance is the bridge used by the activity-scoped
ViewModel; the service itself owns all preview and live capture resources.

It creates RootEncoder `MultiStream` with screen, camera, microphone, playback,
and mixed audio sources plus an opaque pause-slate GL filter. A compatible
prepared camera preview is reused when a session starts; otherwise the service
prepares the selected video and audio configuration before connecting indexed
RTMP clients. Live source changes are synchronized, update required foreground-
service types before opening a new protected source, and never create a second
encoder. The session-scoped screen adapter detaches and reattaches the surface
of one VirtualDisplay so Android 14+ never receives a second display-creation
call for one MediaProjection grant.

The service starts one physical-orientation tracker at creation and stops it at
destruction. It stores the latest 0/90/180/270-degree orientation even while the
preview surface is detached or video is hidden. Camera attachment, source
switching, and resume immediately reapply that cached value instead of waiting
for another sensor event. A display-derived value is only the startup fallback
until the first physical sensor reading.

The video encoder is always prepared with rotation 0 and the configured
landscape width×height. Physical camera orientation is applied to the camera
texture. The stream portrait viewport follows the current physical orientation.
The preview counter-rotation and preview portrait flag remain independent.
Changing device orientation does not rebuild the encoder or RTMP clients.

Cleanup stops every client, releases capture, encoder, preview, and audio
resources, clears the MediaProjection result, removes session callbacks,
clears session-only keep-awake state through activity coordination, and stops
foreground-service status. A bound Preview host can prepare a new camera
pipeline after cleanup. The app-wide orientation preference remains in force.

The service never restarts a capture session after process death because the
MediaProjection grant and in-memory endpoints cannot be reconstructed safely.

If MediaProjection ends while Screen video or playback audio is active, the
service ends the session and reports a capture-permission failure. Revocation
does not stop a camera-only session when the retained projection is unused.

### 6.4 Permissions and service types

| Selected capability | Runtime grant | Foreground-service type |
|---|---|---|
| Screen video | MediaProjection consent | `mediaProjection` |
| Back/front camera | `CAMERA` | `camera` |
| Microphone | `RECORD_AUDIO` | `microphone` |
| Playback audio on API 29+ | `RECORD_AUDIO` + MediaProjection | `mediaProjection` |
| Notifications on API 33+ | `POST_NOTIFICATIONS` | none |

The manifest declares Internet/network state, camera and optional camera
hardware, record audio, base foreground service, and media-projection,
microphone, and camera foreground-service permissions. The service declaration
lists the union of supported types; runtime startup supplies only the selected
subset.

## 7. Provider authentication and APIs

Public client IDs live in tracked
`androidApp/src/main/res/values/strings.xml`. Exact provider-console setup is in
`OAUTH_SETUP.md`.

### 7.1 YouTube

YouTube uses AppAuth Authorization Code with PKCE in a system browser/custom
tab. It does not use Google Sign-In or require Google Play Services.

| Property | Value |
|---|---|
| Authorization endpoint | `https://accounts.google.com/o/oauth2/v2/auth` |
| Token endpoint | `https://oauth2.googleapis.com/token` |
| Redirect | `com.ogh.app:/oauth2callback` |
| Scope | `https://www.googleapis.com/auth/youtube` |
| Extra authorization parameter | `access_type=offline` |
| API base | `https://www.googleapis.com/youtube/v3` |

After exchange, Ogh obtains the authorized channel, stores the encrypted token
record, and creates one provider destination. Before streaming it refreshes an
expired token when possible, creates a live broadcast, creates an RTMP stream,
binds them, and returns the ingestion address plus stream name as one in-memory
endpoint. API tokens use the Authorization header, not query parameters.

Disconnect posts the token in the form body to
`https://oauth2.googleapis.com/revoke`, then removes the local record.
Error messages do not disclose provider response bodies.

### 7.2 Twitch

Twitch uses the native/public Device Authorization Grant with no redirect or
client secret.

| Property | Value |
|---|---|
| Identity base | `https://id.twitch.tv/oauth2` |
| Helix base | `https://api.twitch.tv/helix` |
| Scope | `channel:read:stream_key` |
| Default polling interval | 5 seconds, replaced by provider response |

Ogh requests a device authorization, opens the provider verification URI in
the browser, polls according to the returned interval, and handles pending,
slow-down, denial, and expiry responses. It resolves the authenticated user and
private stream key through Helix immediately before a session, forming the
Twitch RTMP endpoint only in memory. Refresh and revocation use client ID plus
the user's token and no secret.

## 8. Data model and persistence

### 8.1 Shared domain

`shared/src/commonMain` contains:

- stateless Compose Multiplatform screens, navigation identifiers, the dark
  theme, provider/destination color parsing, and the gradient in-app brand mark;
- `AppSettings`, `VideoSettings`, `AudioSettings`, `VideoSource`, and
  `Resolution`;
- `Destination` and `DestinationType`;
- `DestinationRepository` for observable, platform-free collection behavior;
- `StreamEndpointPlanner` for enabled manual/provider endpoint selection;
- `StreamingProvider`, `ProviderAccount`, `StreamMetadata`, `PrivacyStatus`,
  and `LatencyMode`;
- `StreamState`, `VideoConfig`, and `AudioConfig` for runtime encoder settings;
- `StreamingStats` for elapsed seconds and current bitrate;
- log-level/subsystem identifiers; and
- `RtmpValidator`.

It imports no Android API and is compiled for Android and JVM. There is one
destination model: `Destination`. The JVM target is a portability compilation
and test boundary, not a separately shipped desktop application.

### 8.2 Storage separation

| Data | Owner and format | Protection/lifetime |
|---|---|---|
| Non-secret settings | Preferences DataStore key `settings_json_v1`, JSON schema 1 | private app storage, durable |
| Manual destinations and keys | `SecureDestinationStorage`, JSON array | AES-256-GCM with Android Keystore |
| OAuth tokens/account IDs | `TokenManager` + encrypted provider fields | AES-256-GCM with Android Keystore |
| Custom pause image | external document provider | retained read URI only |
| Provider ingest credentials | preparation/session memory | never persisted |
| MediaProjection result | active process/session memory | never persisted |

AES uses `AES/GCM/NoPadding`, a 256-bit non-exportable Android Keystore key, a
12-byte IV, and a 128-bit authentication tag. Encoded payloads prepend the IV
to ciphertext and store Base64. Invalid or undecryptable sensitive storage is
not read as plaintext.

Settings decode field-by-field against `AppSettings()` defaults so missing or
unknown enum values safely use current defaults. A corrupt complete settings
document falls back to defaults. DataStore writes replace the full immutable
snapshot transactionally.

Application cloud backup and device-transfer extraction exclude the entire app
root. Uninstall or clearing app data removes local records; provider disconnect
also requests revocation.

## 9. Modules and repository map

```text
.
├── androidApp/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── kotlin/com/ogh/app/
│       │   ├── MainActivity.kt
│       │   ├── ScreenCaptureService.kt
│       │   ├── PauseSlateFactory.kt
│       │   ├── data/          # Android DataStore and encrypted storage
│       │   ├── oauth/         # auth, token storage, provider clients
│       │   └── viewmodel/     # session UI state
│       └── res/               # launcher, notification, policy and auth resources
├── shared/
│   ├── build.gradle.kts
│   ├── src/commonMain/        # shared Compose UI, state, models and validation
│   └── src/commonTest/        # platform-free behavior tests, run on JVM
├── .maestro/                  # black-box Android flows
├── .github/workflows/         # CI build/test/emulator automation
├── flake.nix                  # supported development environment
└── *.md                       # maintained product/engineering documents
```

No additional use-case, repository, navigation, database, or plugin layer is
part of the implementation. New indirection requires a current duplication or
testability problem, not a hypothetical platform.

## 10. Build contract

The supported environment is `nix develop`. Key pinned versions are:

| Component | Version |
|---|---|
| JDK/JVM target | 17 |
| Kotlin/Compose compiler plugin | 2.4.10 |
| Android Gradle Plugin | 8.13.2 |
| Gradle wrapper | 8.14.5 |
| Compose Multiplatform plugin | 1.11.1 |
| Compose Multiplatform Material 3 | 1.11.0-alpha07 (the 1.11.1 distribution) |
| RootEncoder | 2.7.5 |
| AppAuth | 0.11.1 |
| OkHttp/MockWebServer | 5.4.0 |
| DataStore Preferences | 1.2.1 |
| AndroidX Core | 1.18.0 |
| Lifecycle | 2.10.0 |
| Kotlin coroutines | 1.11.0 |
| Maestro CLI | 2.6.1 |

Dependencies use exact versions. The shared Android target uses the dedicated
`com.android.kotlin.multiplatform.library` plugin rather than the deprecated
legacy Android-library integration. JitPack is present only for RootEncoder.
Version upgrades are explicit compatibility changes and must pass the same
gates as feature code.

Local builds default to version code 1 / name `0.1.0`. Tag CI supplies a
positive `APP_VERSION_CODE` and derives `APP_VERSION_NAME` from a `vX.Y.Z` tag.
BuildConfig receives:

- `VERSION_NAME` from the Android default configuration; and
- `REPO_URL` from `APP_REPOSITORY_URL`, or from the local Git `origin` converted
  to HTTPS and stripped of `.git`.

There is no project-specific source URL in Kotlin. GitHub Actions supplies
`${{ github.server_url }}/${{ github.repository }}`, so forks and repository
transfers produce correct About links without source changes.

## 11. Test and CI contract

The complete local non-device gate is:

```bash
nix develop --command bash -lc \
  './gradlew :shared:jvmTest :androidApp:testDebugUnitTest :androidApp:lintDebug :androidApp:assembleDebug'
```

Shared tests cover defaults, destination collection behavior, endpoint
planning/joining, configuration-lock policy, statistics formatting, and RTMP
validation. Android JVM tests cover ViewModel state/destination behavior,
permission predicates, settings and destination codecs, OAuth configuration,
Twitch device/refresh/revoke flows, YouTube requests/parsing, and Google
revocation using MockWebServer.

The Maestro suite runs in this order:

1. initial Settings setup, Preview controls, navigation, and settings/process-death persistence;
2. manual destination add/edit/delete;
3. YouTube authorization origin loading; and
4. Twitch activation origin loading.

The browser flows prove navigation to the live provider pages and reject known
configuration errors. They do not complete real account consent. Provider
consent, token refresh against production, real camera/audio capture, and a
public broadcast require dedicated accounts and suitable devices.

CI runs unit tests, lint, one debug assembly, and an API 35 emulator-backed
Maestro job. The emulator job depends on the build job and downloads that exact
APK rather than compiling a second copy. It uploads the tested APK and Maestro
diagnostics as workflow artifacts.

A semantic-version tag push or a manually dispatched GitHub **Release** workflow
runs the release gate, derives the APK version, requires the four Android
signing secrets documented in `CONTRIBUTING.md`, builds a
release APK without a debug fallback, verifies its signature with `apksigner`,
and publishes the APK plus SHA-256 checksum. OAuth client IDs remain tracked
public identifiers and are unrelated to release-signing secrets.

## 12. Security and privacy requirements

- Never embed or track a client secret, user token, authorization code, PKCE
  verifier, stream key, signing key, password, or private certificate.
- Public OAuth client IDs are expected to be recoverable from the APK and may
  be tracked.
- Never log a full authorization URL, token, provider ingest endpoint, manual
  endpoint containing a key, response body containing provider data, or the
  custom pause document.
- Use HTTPS for OAuth/provider APIs and do not weaken TLS validation.
- Manual endpoints may be RTMP or RTMPS because the destination is chosen by
  the user; validation must not rewrite it silently.
- Send provider tokens only to their issuing provider and media only to enabled
  destinations.
- Every Android permission and provider scope must map to an active feature.
- Keep `PRIVACY.md` consistent with storage, transmission, revocation, and data
  retention behavior.

## 13. Current product boundaries

The implemented application has no hosted backend, Ogh identity, media relay,
recording, chat, scene graph, overlays, picture-in-picture camera composition,
multi-camera composition, adaptive bitrate controller, automatic network
handoff, desktop/iOS binary, or unattended restart after process death.

These are boundaries, not a roadmap. Code, models, permissions, dependencies,
and documentation must not imply that an absent capability exists.

## 14. Acceptance criteria

A production-ready change preserves all applicable statements below:

- A fresh install opens Settings and keeps Preview unavailable until a
  destination is configured.
- Manual RTMP works without Google Play Services or a provider account.
- YouTube browser authorization can start without Google Play Services.
- Twitch device authorization opens the provider-supplied verification page.
- Destination CRUD validates and persists manual secrets encrypted.
- Connected providers appear as one independently toggleable destination.
- New installations default to screen, 720p30/2.5 Mbps, microphone and system
  audio enabled, orientation lock, keep-awake, and the Ogh pause logo.
- Preview source selection and Settings stream details persist in the same
  settings snapshot.
- Preview audio selection persists, uses two control rows in portrait, and fits
  in one row in landscape.
- Persisted settings load before **Go Live** can begin and survive process death.
- **Go Live** cannot be double-triggered while permissions/API/encoder work is
  running.
- Camera and microphone permissions are requested only for selected sources.
- Playback capture requests MediaProjection only on Android 10+.
- The foreground service starts with only selected type flags and remains
  non-exported/non-sticky.
- One encoder feeds all enabled outputs.
- Hiding/resuming video does not intentionally reconnect outputs or stop audio.
- Screen/camera and camera-facing changes remain in one encoder and do not
  intentionally reconnect healthy outputs.
- User stop wins over asynchronous disconnect callbacks.
- Provider ingest keys and OAuth/manual secrets never enter logs or DataStore.
- About links reflect the repository that built the APK.
- Unit tests, lint, APK assembly, and applicable Maestro/manual regression pass.

## 15. Reconstruction procedure

A clean implementation of this exact application can be reproduced in the
following dependency order:

1. Create the two Gradle modules, pinned Kotlin/Compose Multiplatform toolchain,
   Android and JVM shared targets, API levels, package, and Nix shell.
2. Implement shared immutable models, defaults, endpoint joining, stream state,
   RTMP validation, theme, stateless screens, navigation identifiers, and the
   code-drawn brand mark with common tests.
3. Implement encrypted destination/token stores and versioned DataStore
   settings with codec tests and backup exclusion.
4. Implement the destination repository and activity-scoped ViewModel state,
   validation, preparation lock, endpoint assembly, and callback aggregation.
5. Implement AppAuth YouTube PKCE, Twitch device authorization, refresh,
   revocation, and provider API clients with MockWebServer tests.
6. Implement MainActivity permission/document/OAuth result orchestration and
   dynamic repository metadata.
7. Implement the non-sticky foreground service with RootEncoder MultiStream,
   screen/camera source switching, the pause-slate filter, microphone/playback/
   mixed audio, notification, and deterministic cleanup.
8. Connect the Android entry point to the shared Preview/Settings flow and
   shallow screens through lifecycle-aware state, a service-backed camera
   preview, and platform callbacks, retaining browser-compatible authorization.
9. Add unit, lint, build, Maestro, and local RTMP/media verification, then align
   the public privacy and OAuth registration documents.

This sequence describes the existing repository; it is not a milestone plan.

## 16. Change control

User-visible behavior changes update README, this specification, privacy, OAuth
setup, or changelog as applicable. A choice that constrains later architecture
updates `DECISIONS.md`. Routine refactors and visual polish remain in Git
history. Pull requests use the full regression template and explain every
unchecked or not-applicable item.
