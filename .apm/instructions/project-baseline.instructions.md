---
description: Essential GriffBoard commands, source roots, and privacy invariants.
---

# GriffBoard

- Android application sources live in `app/src/main/`; Kotlin uses `com.griffinboris.griffboard`.
- Run `task local:check` for unit tests, Android lint, and a sideloadable debug APK. Use `task local:install` for an explicitly selected connected device.
- Shared guidance is pinned in `apm.yml` and `apm.lock.yaml`. Edit repository guidance under `.apm/`, then run `task ai:generate` and `task ai:check`.
- Audio and entered text stay on device. Network access is exclusively for explicitly requested model downloads. Never log audio, typed text, or transcripts.
- Recording and transcription belong to one input session. Cancel them when the editor changes or the owning keyboard/panel closes; never insert a late result into another field.
