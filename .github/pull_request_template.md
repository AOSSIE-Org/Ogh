## Summary

<!-- What changed, why, and what should reviewers observe? -->

## Risk

<!-- Note capture, lifecycle, OAuth, persistence, security, or compatibility risk. -->

- [ ] The change is focused and contains no credentials, generated output, or unrelated refactor.
- [ ] Applicable source-of-truth docs were updated with the implementation.
- [ ] Required CI checks pass on this commit.

## Manual regression

Check the behaviors affected by this pull request and record anything not run in **Exceptions**.

### App and configuration

- [ ] Fresh install opens Settings until a platform or manual destination is configured.
- [ ] Preview and Settings navigation, system back, background/foreground, and process restart behave correctly.
- [ ] Stream details, quality, orientation, wake lock, Preview audio, and pause-image choices persist.
- [ ] Resolution, frame-rate, and bitrate dropdowns expose valid presets and persist selections.
- [ ] Compact screens, rotation, font scaling, keyboard, insets, and accessibility labels remain usable.

### Preview and capture

- [ ] Screen and Camera controls select the intended source without a dropdown.
- [ ] Preview controls use two rows in portrait and one usable row in landscape.
- [ ] Microphone and System Audio can be selected independently before preparation.
- [ ] Camera permission denial is recoverable; front/back flipping uses only available cameras.
- [ ] Camera preview preserves aspect ratio and physical up/down with auto-rotate on or off, including after sleep/reopen.
- [ ] Camera output chooses the physical portrait/landscape geometry at start and remains stable during the session.
- [ ] Orientation lock applies before and during a session and restores correctly when disabled.
- [ ] Screen capture requests fresh MediaProjection consent and renders its live preview, including recursion.
- [ ] Microphone-only, playback-only, mixed, and silent modes behave as configured.

### Live session

- [ ] Go Live immediately shows preparation and cannot be triggered twice.
- [ ] Screen ↔ camera and front ↔ back switching works while live without reconnecting healthy outputs.
- [ ] Hide/Show Video preserves RTMP and audio with both default and custom images.
- [ ] Settings stays hidden while preparing/live/video-hidden; Stop clears projection/audio and leaves any bound camera preview stable.
- [ ] A second session works after stop, denial, failure, hide/show, and source switching.
- [ ] One and multiple destinations receive the same valid H.264/AAC session; one failed output does not end healthy outputs.

### Destinations and accounts

- [ ] Manual RTMP add, validation, edit, enable/disable, delete confirmation, encryption, and restore work.
- [ ] YouTube connect, browser redirect, refresh, broadcast creation, streaming, and disconnect/revocation work.
- [ ] Twitch device authorization, polling, refresh, stream-key resolution, streaming, and disconnect/revocation work.
- [ ] Cancellation and provider/network failures return to an actionable state without leaking URLs, keys, tokens, or personal data.

## Exceptions

<!-- List unchecked/not-applicable items and why. -->
