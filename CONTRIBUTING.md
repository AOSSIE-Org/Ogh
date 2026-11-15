# Contributing to Ogh

## Environment

Use the repository's Nix flake for all project commands:

```bash
nix develop
```

It supplies JDK 17, the Android SDK, FFmpeg, emulator tooling, and Maestro. The
Gradle wrapper remains the source of truth for Gradle itself.

## Before changing code

Read [AGENTS.md](AGENTS.md), [SPECIFICATION.md](SPECIFICATION.md), and the
relevant entries in [DECISIONS.md](DECISIONS.md). Preserve unrelated worktree
changes. Keep Android APIs in `androidApp/`; `shared/` must remain
platform-independent. Put reusable Compose screens, theme tokens, and in-app
icons in `shared/src/commonMain`; expose Android behavior through values and
callbacks supplied by the application entry point.

Do not add speculative abstractions or future-feature models. A new dependency
must solve a current requirement, be actively maintained, and be pinned to an
exact version compatible with the existing Android build toolchain.

## Validation

Run the full local checks:

```bash
nix develop --command bash -lc \
  './gradlew :shared:jvmTest :androidApp:testDebugUnitTest :androidApp:lintDebug :androidApp:assembleDebug'
```

For UI or permission-flow changes, also install the APK on an emulator/device
and run:

```bash
nix develop --command maestro test .maestro
```

A preview-only test is insufficient. Contributors must inspect the actual
encoded RTMP output for camera-orientation changes.

| Device position       | Encoded canvas | Expected camera output                            |
| --------------------- | -------------: | ------------------------------------------------- |
| Portrait 0°           |      landscape | upright, portrait content inside landscape canvas |
| Landscape 90°         |      landscape | upright and fills landscape width                 |
| Reverse portrait 180° |      landscape | upright                                           |
| Landscape 270°        |      landscape | upright and fills landscape width                 |

Tests that complete OAuth consent or publish media must use dedicated test
accounts and must never place credentials in source control.

## Review checklist

- The change has a focused purpose and handles failure paths.
- Tests cover new domain, persistence, parsing, or state behavior.
- Stream keys, authorization URLs, tokens, PKCE values, and personal data are
  absent from logs and fixtures.
- Runtime permissions and foreground-service types are no broader than the
  selected feature requires.
- Public behavior and architectural decisions are documented where applicable.
- Unit tests, lint, APK assembly, and relevant Maestro flows pass.

## Commits

Use focused [Conventional Commits](https://www.conventionalcommits.org/) such as:

```text
feat: add camera capture source
fix: persist audio settings
refactor: remove unused profile models
docs: clarify OAuth registration
```

Public OAuth client IDs may be tracked. OAuth access/refresh tokens, stream
keys, client secrets, signing material, passwords, and private certificates may
not be committed.

## Tagged releases

GitHub releases are created only from semantic-version tags such as `v1.0.0`.
The workflow derives `versionName` from the tag and a monotonically increasing
`versionCode` from the workflow run number. It builds, verifies, and publishes
one signed APK plus its SHA-256 checksum; there is no debug-signed fallback.

After configuring the signing secrets below, a maintainer can release entirely
from the GitHub page: open **Actions**, select **Release**, choose **Run
workflow**, select the commit branch, enter a new tag such as `v1.0.0`, and run
it. The workflow creates the tag and GitHub Release only after all gates pass.
Pushing the same `vX.Y.Z` tag from Git remains supported and runs the identical
gate.

Create and securely back up one long-lived Android release keystore outside the
repository. Losing it prevents future APKs from updating existing installs.
Configure these GitHub Actions repository secrets:

- `ANDROID_RELEASE_KEYSTORE_BASE64`: base64-encoded keystore contents;
- `ANDROID_RELEASE_STORE_PASSWORD`: keystore password;
- `ANDROID_RELEASE_KEY_ALIAS`: signing-key alias; and
- `ANDROID_RELEASE_KEY_PASSWORD`: signing-key password.

For example, on Linux, encode the keystore without line wrapping:

```bash
base64 -w 0 /secure/path/ogh-release.jks
```

The workflow decodes it only into the runner's temporary directory and passes
signing values through environment variables. Never place the keystore or its
passwords in Gradle files, commits, workflow variables, artifacts, or logs.
Before tagging, make sure the same release certificate is registered for the
Google OAuth Android client and complete all manual device/provider checks in
the pull-request template.

## Proposals and pull requests

An implemented, focused pull request is preferred over a feature-request-only
issue. Discuss changes first only when they alter security/privacy, provider
scopes, the capture architecture, or a public compatibility contract. Use the
pull-request template and mark a check not applicable only with a concise
reason; do not delete unperformed checks.

### Pull-request scope and independence

- Prefer one coherent, independently reviewable scope per pull request. A PR may be
  large when the work is genuinely related and the rationale, design, and
  validation justify keeping it together.
- Keep changes that are necessary parts of the same outcome in one PR (for
  example, an implementation and its directly related tests/documentation).
  Do not bundle unrelated features, refactors, visual redesigns, or cleanup just
  because they touch nearby files.
- Keep independent work in separate PRs. For example, a UI-only redesign should
  be submitted independently from a streaming lifecycle fix, even when both
  affect the same screen or feature area.
- Do not stack dependent PRs or submit a set of dependent PRs in parallel.
  Start independent PRs from the current target branch. If one change truly
  depends on another, wait for the prerequisite PR to be merged before opening
  the dependent PR, unless maintainers explicitly agree on another review plan.
- A PR should build and be reviewable on its own against its target branch; it
  should not require reviewers to mentally combine unmerged branches to
  understand or validate it.
- Describe the single intended outcome, explain why the scope belongs together,
  identify any user-visible or architectural impact, and list the tests and
  manual checks performed. For UI changes, include screenshots or a recording
  when they materially help reviewers assess the result.
- If review identifies unrelated work, split that work into a separate
  independent PR rather than expanding the current PR's scope.

