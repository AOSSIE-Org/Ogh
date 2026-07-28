# Repository Engineering Guide

This file is the working agreement for human and automated contributors to
Ogh. It describes how to change the repository safely and how to keep its
implementation and documentation useful to the next maintainer.

## Authority and reading order

The checked-in source code, build files, manifests, resources, and tests are
the source of truth for implemented behavior. Documentation explains that
behavior and its constraints; it does not override code that has already been
verified. When code and documentation disagree, establish the behavior from
the implementation and tests, then update the stale document in the same
change.

Before changing the project, read:

1. `SPECIFICATION.md` for the current product and implementation contract;
2. `DECISIONS.md` for architectural constraints and tradeoffs;
3. `README.md`, `PRIVACY.md`, and `OAUTH_SETUP.md` when the change affects
   users, data handling, or provider authentication;
4. the relevant source and tests.

Do not infer requirements from old branches, exported conversations, issue
discussions, or generated artifacts when the current code answers the
question.

## Supported workflow

Use the repository development shell for all project commands:

```bash
nix develop
```

The Gradle wrapper remains authoritative for Gradle. Do not substitute a host
JDK, Android SDK, Gradle installation, or unpinned tool when the Nix environment
provides it.

Preserve unrelated worktree changes. Prefer small, reviewable commits in
Conventional Commits format. Every commit should leave the repository
buildable; do not commit generated build directories, credentials, emulator
state, or local IDE configuration.

## Architecture boundaries

- `shared/` contains platform-independent Compose Multiplatform screens, theme,
  navigation model, in-app icons, domain models, destination collection state,
  endpoint planning, formatting, and validation. Common UI is stateless around
  values and callbacks. Android classes must not cross into this module.
- `androidApp/` owns the Android Compose entry point and lifecycle-aware state
  collection, Android lifecycle and permissions, capture, the foreground
  service, persistence, OAuth, provider APIs, and platform resources.
- `ScreenCaptureService` is the single owner of preview/live capture, encoder,
  audio, and RTMP resources. Do not open a second camera pipeline for preview.
- `StreamViewModel` owns stream-facing UI state. Do not create a second owner
  for the same session state.
- Media is captured and encoded once, then fanned out by RootEncoder
  `MultiStream`. Never create one capture/encode pipeline per destination.
- Screen, front camera, and back camera are mutually exclusive full-frame
  sources in the same video pipeline. Hide Video uses one opaque pause-slate
  filter and must not destroy the underlying capture source.
- Use established implementations for RTMP, H.264, AAC, OAuth, TLS, and
  cryptography. Do not create local protocol or crypto implementations.

Add a layer, interface, module, or dependency only when it removes current
duplication, creates a real test boundary, or satisfies an implemented
requirement. Avoid speculative abstractions and models for unimplemented work.

## Android lifecycle and permissions

- Request runtime permissions while `MainActivity` is visible.
- Start the foreground service with only the types required by the selected
  video and audio sources.
- Treat MediaProjection consent as a single-session grant. Never persist or
  reuse its result after service destruction or process death.
- Keep the service non-exported and non-sticky.
- Keep permission-denial and preparation-failure paths recoverable in the UI.
- Preserve Android 7.0/API 24 support. Playback capture is available only on
  Android 10/API 29 or newer and only for apps that allow it.

## Data and security rules

Public OAuth client IDs may be tracked because every APK exposes them. Never
commit or embed client secrets, access or refresh tokens, authorization codes,
PKCE verifiers, manual stream keys, signing keys, passwords, or private
certificates.

- Non-secret settings belong in DataStore.
- Manual destination credentials and OAuth account records must remain
  encrypted with AES-256-GCM keys held by Android Keystore.
- Encryption and decoding failures must fail closed; never fall back to
  plaintext.
- Provider ingest URLs and keys stay in memory for the active preparation or
  session and must not be logged or persisted.
- Logs must not contain credentials, full authorization URLs, personal data,
  or user-selected document contents.
- Keep cloud backup and device transfer disabled for application data.
- Request no provider scope or Android permission without an implemented use.
- Tag releases must require the external signing configuration, verify the
  produced APK, and never fall back to an unsigned or debug-signed artifact.

Changes to collection, storage, transmission, retention, or revocation require
a matching review of `PRIVACY.md`.

## UI and product consistency

Ogh uses a dark, flat, high-contrast interface with compact spacing, simple
rounded surfaces, and a pink-to-purple accent. Preserve Android interaction
conventions, system insets, readable contrast, semantic labels, and touch
targets of at least 48dp. Style must never obscure state, errors, permissions,
or the primary live controls.

Camera preview and encoded output preserve aspect ratio. A service-owned
physical-orientation tracker keeps camera frames upright independently of
Android auto-rotate and the persisted activity-orientation lock, including
after preview reattachment. Derive preview viewport orientation from its actual
surface dimensions, not the sensor or encoded canvas. Always prepare the encoded camera canvas using rotation 0 and the configured
landscape dimensions. Do not derive encoder dimensions from physical or UI
orientation. Apply physical orientation to the camera texture, preserve the
preview counter-rotation, derive stream portrait state from physical
orientation, and derive preview portrait state from its actual surface.
Do not “fix” camera output by removing or reversing setPreviewRotation. Preview
orientation and encoded output orientation are separate paths. The UI lock applies to the entire activity lifecycle. A live screen
preview may be recursive.

Keep navigation shallow. Preserve user-visible labels used by Maestro unless
the flow is updated in the same commit. A tap on **Go Live** must immediately
enter `PREPARING`, disable duplicate starts, and show progress. Existing
streaming, provider connection, destination, hide/show, and settings flows must
remain functional during visual refactors.

Preview contains source, pre-live audio, and live controls; Settings owns every
other configuration surface. Hide the Settings hierarchy while a session is
preparing, streaming, or paused. Accounts, destinations, metadata, encoder,
audio, and pause-image values remain immutable. Live full-frame source changes
and permission-gated live audio updates are the only exceptions and must use the serialized service/ViewModel path.

## Testing and completion

Run the full non-device gate in `nix develop`:

```bash
./gradlew :shared:jvmTest :androidApp:testDebugUnitTest
./gradlew :androidApp:lintDebug :androidApp:assembleDebug
```

For UI, lifecycle, OAuth-page, permission, capture, or persistence changes,
install the APK and run:

```bash
maestro test .maestro
```

Streaming-pipeline changes also require the local RTMP/FFmpeg validation path
on an appropriately configured emulator or device. Provider consent and public
broadcast tests use dedicated accounts; credentials never enter fixtures or
source control.

Before completion:

- review the diff for unrelated edits, dead code, hardcoded repository data,
  and credential exposure;
- test failure and cancellation paths, not only the happy path;
- update tests for state, parsing, persistence, and validation changes;
- update user and architecture documentation in the same change;
- record a decision only when it constrains later implementation, rather than
  documenting routine history;
- complete the pull-request regression checklist or explain every item marked
  not applicable.

## Documentation contract

The maintained Markdown set is deliberately sufficient to understand and
reconstruct the current application when read with the source tree:

- `README.md`: user-facing purpose, features, use, build, and contribution
  entry points;
- `SPECIFICATION.md`: exact current behavior, architecture, defaults, flows,
  and acceptance criteria;
- `DECISIONS.md`: accepted architectural choices and consequences;
- `OAUTH_SETUP.md`: provider-console configuration matching the code;
- `PRIVACY.md`: public data-use statement;
- `CONTRIBUTING.md`: contributor workflow and quality gates;
- `CHANGELOG.md`: user-visible release history.

Do not add future roadmaps or milestone plans to these documents. Describe
current implementation, explicit product boundaries, and reproducible
verification. Keep file paths, package names, dependency versions, defaults,
endpoints, permissions, and commands synchronized with the repository.
