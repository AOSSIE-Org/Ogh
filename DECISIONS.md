# Architecture Decision Records

Status: accepted decisions for the current implementation

Last reviewed: July 18, 2026

This file records choices that constrain implementation and explains their
tradeoffs. It is not a roadmap or chronological development diary. Source code
and tests are authoritative for exact mechanics; when an architectural choice
changes, update the relevant record in the same change.

## ADR-001: Android runtime with shared Compose Multiplatform UI

Status: accepted

### Context

Screen capture, playback capture, cameras, foreground services, notifications,
and Keystore are direct Android facilities. The screens, theme, navigation
model, and in-app brand mark do not require those APIs and should remain usable
by another platform entry point without being rewritten.

### Decision

Use Kotlin 2.4.10 and Compose Multiplatform 1.11.1. Keep platform-neutral
screens, theme, UI state inputs, navigation identifiers, in-app icons, domain
models, collection state, formatting, endpoint planning, and validation in
`shared/src/commonMain`. Common composables accept plain values and callbacks;
they do not import Android ViewModels, contexts, intents, resources, or
lifecycle APIs.

Keep the shipping Android entry point, lifecycle-aware flow collection,
activity results, permissions, capture, storage, OAuth, provider networking,
foreground service, launcher assets, and notification assets in `androidApp`.
Compile and test shared code for JVM in addition to producing its Android AAR.

### Consequences

Android behavior remains explicit without leaking into common code. UI and
domain portability are compiler-checked, while there is still only one shipped
application target and no speculative platform runtime. The platform adapter
must supply build metadata and external actions such as opening a URL or
choosing a document. Additional application targets or modules still require a
current product or test boundary.

## ADR-002: RootEncoder with encode-once multi-destination fan-out

Status: accepted

### Context

Implementing RTMP, H.264, AAC, camera/screen sources, and media codec lifecycle
locally would be a high-risk maintenance burden. Encoding independently for
every output multiplies CPU, thermal, battery, and synchronization costs.

### Decision

Use RootEncoder 2.7.5. Build one capture and encoding pipeline and use
`MultiStream` to feed one indexed RTMP client per enabled destination. Isolate
the library inside `ScreenCaptureService`; UI and provider clients exchange
settings and endpoint strings, not RootEncoder objects.

### Consequences

All outputs share resolution, frame rate, video bitrate, and the single AAC
track. Per-destination encoding settings are not supported. Library upgrades
require real capture and RTMP regression testing because the service relies on
its source-switching and callback semantics.

## ADR-003: One non-sticky foreground service owns live media

Status: accepted

### Context

Android requires foreground execution and explicit service types for ongoing
MediaProjection, camera, and microphone use. MediaProjection consent is an
ephemeral user grant that cannot be reconstructed safely after process death.

### Decision

`ScreenCaptureService` exclusively owns preview/live capture sources, audio
sources, encoders, pause bitmap, and RTMP clients. Bind it while the activity is
visible so a pre-live camera preview can reuse the publishing pipeline. Promote
it to the foreground only for a session, with the `mediaProjection`, `camera`,
and `microphone` type flags required by the current protected sources. The
service is non-exported and returns `START_NOT_STICKY`.

### Consequences

There is one cleanup owner and Android 14+ while-in-use restrictions are
respected. A killed process ends the session rather than attempting an unsafe
restart. The activity and ViewModel may coordinate the service but may not own
or duplicate camera/encoder resources. Unbinding an idle service releases the
camera; a started live service continues independently of the activity surface.

## ADR-004: Full-frame source switching and a non-destructive pause slate

Status: accepted

### Context

Superseded in part by ADR-017. The preview-orientation separation remains
accepted, but portrait encoder preparation and swapped output dimensions are
no longer used.

The product needs screen streaming, camera streaming, and temporary video
hiding while audio remains independent. A compositor or second encoder would
add complexity without being required for these mutually exclusive sources.

### Decision

Treat screen, front camera, and back camera as full-frame sources in one video
encoder. Use that pipeline for camera preview and live publishing. Serialize
live source changes through the service, update protected foreground-service
types before opening the new source, and request a keyframe after the change.
Keep one detachable VirtualDisplay for the lifetime of each MediaProjection
grant so Android 14's one-display rule is respected across screen/camera/screen
transitions. Use fill scaling for camera frames so aspect ratio is preserved by
cropping overflow instead of distortion or preview bars. Keep one service-owned
physical-orientation tracker active for the service lifetime so preview
reattachment never depends on a new movement or Android display rotation. Map
sensor readings to cardinal angles with hysteresis, applying physical orientation
changes to RootEncoder's base camera texture (setCameraOrientation) so the stream
stays upright, and applying an equal counter-rotation to the preview
(setPreviewRotation) so the camera viewfinder remains visually stable. Derive the preview
portrait/landscape viewport flag only from the attached surface dimensions.
Keep the activity lock a UI-only policy. A live screen source may render in
Preview, including the expected recursive view when Ogh itself is captured.

For a camera-started session, snapshot portrait/landscape during preparation.
Use RootEncoder rotation 90 and swapped output dimensions for portrait, or
rotation 0 and configured dimensions for landscape. Keep that encoded geometry
for the full session; later physical turns update camera/preview transforms and
crop into the fixed stream canvas rather than rebuilding H.264 or RTMP.

Render the default logo or a bounded local document into an encoder-sized
bitmap and apply it as a fully opaque RootEncoder GL filter. Hide/Show changes
only that filter; it does not destroy or recreate the underlying screen or
camera source, alter the selected audio source, or disconnect RTMP.

### Consequences

Hide/resume, camera flipping, and screen/camera switching preserve intended
RTMP and audio continuity without reusing projection result data or creating a
second VirtualDisplay. A new MediaProjection grant is requested only when a
live camera-only session first switches to Screen. Camera-over-screen,
multi-camera composition, scenes, and arbitrary overlays are not represented
by this architecture. Changing encoded portrait/landscape geometry during a
live session is intentionally unsupported because it requires re-preparation
and is not reliably seamless across RTMP destinations. Custom images remain in
the document provider and require only a persisted read grant.

## ADR-005: Separate storage by sensitivity

Status: accepted

### Context

Small preference documents do not need a relational database, while stream
keys and provider tokens must not be stored as ordinary preferences. Android
backup could otherwise transfer credentials outside the intended device/key
relationship.

### Decision

Store the complete non-secret `AppSettings` snapshot as versioned JSON in
Preferences DataStore. Store manual destination records and OAuth account fields
as AES-256-GCM ciphertext using non-exportable Android Keystore keys. Retain
only a read URI for a custom pause document. Exclude application data from cloud
backup and device transfer.

### Consequences

Settings writes are transactional and codec migrations can be tested without
DataStore. Room is unnecessary. Keystore invalidation or authentication failure
fails closed; no plaintext migration or fallback is permitted. Clearing app
data or uninstalling removes local records.

## ADR-006: Public native OAuth clients without embedded secrets

Status: accepted

### Context

An installed APK cannot protect a client secret. Ogh must authenticate users on
standard and de-Googled Android without operating a confidential backend or
requiring Google Play Services.

### Decision

Track the public YouTube and Twitch client IDs in Android string resources.
YouTube uses AppAuth Authorization Code with PKCE in the system browser and
`org.aossie.ogh:/oauth2callback`, requesting only the `youtube` scope. Twitch uses
its public-client Device Authorization Grant and `channel:read:stream_key`.
Never embed a client secret.

### Consequences

Client IDs are expected to be visible in source and recoverable from APKs.
YouTube's custom scheme can be claimed by another installed app, while PKCE
prevents that app from exchanging an intercepted code without Ogh's verifier.
Provider registration and Google review must match the package, distributed
signing certificate, redirect setting, and documented scopes.

## ADR-007: Resolve provider ingest endpoints after capture consent

Status: accepted

### Context

Provider destinations have expiring credentials and private ingest keys.
Creating a YouTube broadcast before Android capture consent can leave an unused
remote broadcast when the user cancels permission.

### Decision

After required Android grants are obtained, refresh provider access as needed,
create/resolve each enabled provider endpoint immediately before encoder/service
startup, and keep the result only in memory for the attempt. Manual endpoints
continue to come from encrypted local destination records.

### Consequences

Credentials are fresh near connection time and unused provider broadcasts are
reduced. Preparation includes network latency, so the UI must enter a visible,
non-repeatable `PREPARING` state before this work. Endpoint values must never be
logged or persisted.

## ADR-008: One activity-scoped ViewModel and state-based navigation

Status: accepted

### Context

The product has one live session and a shallow screen hierarchy. There are no
deep links into nested screens, independent navigation stacks, or separately
lived session domains.

### Decision

Use one activity-scoped `StreamViewModel` for session-facing UI state and a
simple shared `Screen` enum held by `MainActivity` for navigation. MainActivity
collects flows with Android lifecycle awareness and passes immutable values and
callbacks into common composables. Implement back behavior with `BackHandler`
and visible back controls. Keep capture resources in the service and
account/storage ownership in their dedicated classes.

### Consequences

State ownership is easy to trace without a navigation dependency or multiple
ViewModel synchronization. Screen composables remain stateless around observed
flows and callbacks. The ViewModel must not absorb Android resource ownership
or become a replacement for provider/storage classes.

## ADR-009: Immediate preparation state and explicit user-stop precedence

Status: accepted

### Context

Permissions, provider calls, service startup, and encoder setup can take
noticeable time. Leaving **Go Live** active permits duplicate attempts. RTMP
disconnect callbacks arriving after a deliberate stop can incorrectly turn the
UI into an error.

### Decision

`beginPreparing()` is the atomic entry point for a start attempt and rejects
attempts while preparing, streaming, or paused. All cancellation/failure paths
call a preparation terminal method. Track a user stop so asynchronous
disconnect callbacks preserve `STOPPED` rather than replacing it with failure.
Treat those same three states as configuration-locked: hide the entire Settings
hierarchy, guard its navigation, and reject account, destination, metadata,
encoding, audio, and pause-image mutations. Live full-frame source switching is
the sole exception and uses a separate busy state and serialized service path.

### Consequences

The primary action reflects work immediately and session transitions are
deterministic. Every newly introduced asynchronous preparation branch must end
in success, actionable failure/cancellation, or explicit cleanup.
The active encoder and destination set cannot drift away from the configuration
shown by the UI, while source transitions stay explicit and non-repeatable.
Setup becomes editable again after stop, cancellation, or error.

## ADR-010: Immutable versioned settings snapshots in DataStore

Status: accepted

### Context

Individual in-memory defaults caused settings to be lost or partially applied.
Separate preference keys make atomic reads and schema evolution harder to test.

### Decision

`AppSettings` is the complete settings source. `SettingsRepository` serializes
schema version 1 JSON into one DataStore key and decodes missing/unknown fields
against current defaults. The ViewModel loads it before allowing streaming and
writes a new complete snapshot after each user change.

### Consequences

Process death and restart preserve video, audio, metadata, and pause URI. New
defaults affect only absent/fresh fields, not stored choices. Schema decode is a
pure, unit-tested boundary. Secrets remain excluded from this document.

## ADR-011: Reproducible pinned toolchain and dependencies

Status: accepted

### Context

Android, Kotlin, Compose, Gradle, and library compatibility is coupled. Dynamic
versions or arbitrary host SDKs make failures difficult to reproduce.

### Decision

Use `nix develop` as the supported environment, JDK 17, Gradle wrapper 8.14.5,
AGP 8.13.2, Kotlin 2.4.10, Compose Multiplatform 1.11.1, the dedicated
Android-KMP library plugin, and exact library versions. CI and local quality
gates use the repository wrapper and the same Gradle tasks.

### Consequences

Build inputs are explicit and upgrades are reviewable compatibility changes.
Libraries requiring a newer compile SDK/AGP line are not upgraded independently
of that toolchain. A dependency is added only when existing platform/library
capabilities cannot satisfy a current requirement.

## ADR-012: Build-derived repository metadata

Status: accepted

### Context

A source/issue URL hardcoded in Kotlin becomes wrong after a fork, repository
rename, or transfer.

### Decision

Populate `BuildConfig.REPO_URL` from `APP_REPOSITORY_URL` in CI or the local Git
origin. Convert SSH origin syntax to HTTPS and remove `.git`. Use no hardcoded
project fallback; hide links when the value is empty.

### Consequences

The APK built by a fork identifies that fork without source edits. CI must pass
GitHub's server/repository context. Unit/UI code cannot assume a particular
hosting owner or repository name.

## ADR-013: No application-owned confidential credential in the APK

Status: accepted

### Context

Mobile binaries and their runtime memory can be inspected. GitHub Actions
secrets can hide a value during compilation but cannot keep it secret after it
is embedded in an APK.

### Decision

Ogh's implemented provider integrations are public-client flows. User/provider
tokens and manual keys originate from the user authorization relationship and
are encrypted locally. Any capability that fundamentally requires a
confidential server credential is outside this APK-only architecture; placing
that value in source, resources, BuildConfig, NDK code, or CI substitution is
prohibited.

### Consequences

No OAuth-client secret is needed to build or distribute Ogh. CI secrets are not
used for the tracked client IDs. Security review distinguishes public
identifiers from actual credentials instead of applying ineffective
obfuscation. The Android release-signing key is a separate build-time trust
credential: it stays outside the source and APK and is never available to app
runtime code.

## ADR-014: Compact dark high-contrast product design

Status: accepted

### Context

Streaming setup has dense configuration and high-consequence live controls.
Large decorative cards and excessive spacing hide relevant state on compact
phones, while experimental styling can undermine accessibility.

### Decision

Use a dark Material 3 foundation with compact 16dp gutters, simple border-free
rounded surfaces, strong typography, and a pink-to-purple brand accent. The
centered logo is one continuous triangular spiral/play stroke. Split the root
experience into Preview and Settings: Preview is a full-surface source view
with Screen, Camera, pre-live Microphone/System Audio, and live controls;
Settings contains stream details, destinations, platforms, quality, pause
image, and About. Preview controls use two rows in portrait and one row in
landscape. Do not expose a camera dropdown or hardware-specific names. Preserve
standard semantics, insets, contrast, and minimum 48dp touch targets.

### Consequences

Preview does not scroll and keeps high-frequency actions reachable; Settings
can scroll for lower-frequency configuration. An empty installation opens
Settings until a destination exists, and active sessions hide Settings entirely.
The orientation preference applies to the entire activity rather than only a
live session. State and errors remain textual, not color-only. Visual refactors
must update black-box flows and may not disrupt permission, provider,
destination, preview, or live-control behavior.

## ADR-015: Layered automated checks with explicit real-device boundaries

Status: accepted

### Context

Pure JVM tests cannot prove Android browser, permission, camera, MediaProjection,
codec, or provider behavior. Fully automating real provider consent would place
credentials in infrastructure and create fragile public broadcasts.

### Decision

Use common/JVM tests for domain, state, codecs, and HTTP clients; Android lint
and APK assembly for static/package validation; Maestro on an API 35 emulator
for navigation, persistence, CRUD, and live provider-page loading; and manual
device/local-RTMP testing for permissions, media capture, source switching, and
real provider consent/broadcasts.

### Consequences

CI gives deterministic broad coverage without storing user credentials. The PR
template makes the remaining device/account checks explicit and allows a
reasoned not-applicable result. Media-pipeline work is not considered verified
solely because unit tests pass.

## ADR-016: One tested artifact and fail-closed release signing

Status: accepted

### Context

Building a second debug APK in the emulator job can test bytes other than the
artifact uploaded by the main job. Publishing an unsigned release or silently
falling back to a debug-signed APK gives users no durable update identity.

### Decision

Build the debug APK once, pass it to the dependent Maestro job as a workflow
artifact, and install that exact file. For version tags, derive versionName from
the tag and versionCode from the workflow run, require a long-lived keystore via
GitHub Actions secrets, build only the release variant, verify it with
`apksigner`, and publish it with a SHA-256 checksum. Never fall back to a debug
APK when signing configuration is absent or invalid.

### Consequences

UI automation covers the same APK reviewers download from CI. Public APKs have
one maintainable signing identity and a verifiable checksum. Maintainers must
securely back up the private keystore and register its certificate with the
Google OAuth Android client; losing or rotating it breaks direct APK updates.

## ADR-017: Stable landscape camera encoder with independent transforms

Status: accepted

### Context

Preparing a camera encoder with portrait rotation caused the encoded RTMP
canvas to remain portrait-shaped after the camera texture became correctly
oriented in landscape. This produced squeezed or side-cropped landscape output.

### Decision

Prepare camera and screen encoders with rotation 0 and the configured landscape
width×height. Apply physical camera orientation through setCameraOrientation.
Apply the equal counter-rotation to the preview through setPreviewRotation.
Set the stream portrait viewport from current physical orientation and the
preview portrait viewport from the attached preview surface dimensions.

Do not rebuild H.264 or RTMP clients when physical orientation changes.

### Consequences

Landscape camera output uses the full landscape canvas. Portrait camera content
remains upright inside the stable landscape canvas. Preview behaviour remains
independent and unchanged.
