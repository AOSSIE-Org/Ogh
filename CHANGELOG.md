# Changelog

Notable user-visible changes are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added

- Full-frame screen, back-camera, and front-camera source selection.
- A live pause slate that keeps audio and RTMP connections active; users can
  show the Ogh logo or choose a local image.
- Durable DataStore persistence for video, audio, stream metadata, and pause
  image selection.
- Immediate **Preparing…** feedback and duplicate-start prevention after
  tapping **Go Live**.
- Maestro coverage for navigation, destination CRUD, settings persistence, and
  live Google/Twitch authorization-page loading.
- A focused Preview screen with a same-pipeline camera preview and direct
  Screen, Camera, and live controls.
- Direct YouTube, Twitch, and manual RTMP onboarding when no destination exists.
- A centered single-stroke triangular spiral/play logo in a pink-purple
  gradient.

### Changed

- Fixed a camera, audio, or screen-capture permission result that arrived after
  the activity was recreated (for example by a dark-mode change while the
  dialog was open) starting a broadcast without **Go Live**. The result now
  completes the original request, such as selecting the camera preview.
- Fixed landscape camera streams remaining inside a portrait encoder canvas.
  Camera encoding now keeps stable landscape dimensions while physical
  orientation and preview counter-rotation are applied independently.
- Live audio changes now obtain required permissions before changing source and
  leave the previous audio configuration active when an update fails.
- MediaProjection revocation now ends sessions that actively depend on screen
  capture or playback audio.
- Partial multi-destination startup now rolls back destinations already started.
- YouTube streams now use variable CDN detection and prefer RTMPS ingestion.
- Twitch device authorization now handles pending, slow-down, denial, and
  expiry responses.

- Fresh installs open the consolidated Settings screen until a destination is
  configured; stream details, platforms, destinations, quality, pause
  image, and About now live there.
- The Settings hierarchy is hidden during a session, while guarded screen/
  camera and front/back source switching remains available without rebuilding
  RTMP outputs.
- Camera preview uses its actual surface orientation for fill scaling while
  physical camera rotation remains independent of the locked app interface.
- Camera orientation is now independent of Android auto-rotate and survives
  preview reattachment after reopen or screen sleep. Camera broadcasts choose
  portrait/landscape from physical orientation at start and keep that canvas
  stable until stopped; the orientation lock affects only the app interface.
- Resolution, frame-rate, and bitrate controls are now compact dropdowns with
  presets spanning 360p–2160p, 15–60 fps, and 0.8–35 Mbps.
- Microphone and System Audio selections now sit beside the Preview source and
  live controls; portrait uses two rows and landscape uses one.
- Settings snapshots are saved in order so rapid changes cannot persist an
  older value after a newer one.
- Hide Video now changes only the picture and always leaves selected audio live.
- Platform-independent destination state and endpoint planning now live in the
  shared module with common tests.
- Provider endpoints and YouTube broadcasts are prepared only after capture
  permissions are granted.
- Foreground-service types are selected per session for screen, camera, and
  microphone use.
- Video frame-rate settings now reach encoder preparation.
- OAuth uses tracked public YouTube and Twitch client IDs; no local secrets file
  or CI secret is required for them.
- Repository links are derived from CI context or the local Git origin.
- Public documentation now distinguishes current behavior from non-goals and
  architectural decisions.
- Fresh installations now enable eligible system/playback audio by default.
- The simpler rounded, border-free interface is restored, and the Preview
  header uses the gradient Ogh mark instead of a plain text wordmark.
- Maestro now installs the debug APK produced by the build job instead of
  compiling it again.
- Tagged releases now require and verify a durable release signature, derive
  their APK version from the tag, and never fall back to a debug-signed APK.
- Release builds now pass the dedicated signing-key password secret to Gradle.
- Hide/Show now keeps the active capture source alive behind an opaque pause
  slate, and screen/camera/screen switching reuses one detachable VirtualDisplay
  per grant for Android 14+ compatibility.
- Screens, theme, navigation identifiers, color handling, and the in-app logo
  now use Compose Multiplatform in shared common code; the Android activity is
  a lifecycle-aware platform adapter.
- The build now uses Kotlin 2.4.10, Compose Multiplatform 1.11.1, and the
  supported Android-KMP library plugin, with shared code also compiled and
  tested for JVM portability.

### Security

- OAuth tokens and manual stream keys fail closed into Android
  Keystore-backed AES-256-GCM storage.
- Application data is excluded from Android cloud backup and device transfer.
- Provider ingest credentials remain in memory and are not logged or persisted.

### Removed

- Unused profile, error, retry/developer-setting, and unreachable log-export
  scaffolding.
- Legacy environment/local-resource complexity for public OAuth client IDs.
- The redundant Stop Audio When Hidden preference and its pause/resume audio
  source swaps; hiding video now affects video only.
