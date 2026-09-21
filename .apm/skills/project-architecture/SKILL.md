---
name: project-architecture
description: Apply GriffBoard architecture when working on the Android keyboard, settings, local Whisper runtime, model downloads, build tooling, or device verification.
---

# GriffBoard Architecture

## Scope

GriffBoard is a single-module Kotlin Android keyboard with local Whisper dictation. Keep ordinary Android APIs and small feature classes; do not add dependency injection frameworks or a backend.

## Repository Map

- `app/src/main/java/com/griffinboris/griffboard/keyboard/`: input-method service, key layout, and editor behavior.
- `app/src/main/java/com/griffinboris/griffboard/voice/`: microphone ownership and JNI inference.
- `app/src/main/java/com/griffinboris/griffboard/models/`: pinned model catalog and atomic verified downloads.
- `app/src/main/java/com/griffinboris/griffboard/settings/`: launcher, onboarding, and model management.
- `app/src/main/cpp/`: small JNI adapter and pinned upstream whisper.cpp build.
- `tasks/`: Task wrappers following GriffLab's `local:*` convention and current Agents `ai:*` template.
- `docs/`: architecture, device verification, and implementation status.

## Local Conventions

- Use native Android views for the IME and Material components for settings. Share theme values; support light/dark system appearance.
- Keep the input-method service responsible for Android lifecycle and text insertion, with recording and inference off the main thread.
- Native inference is serialized. Cancellation must reach whisper.cpp, and native contexts must be freed after use.
- Models use whisper.cpp GGML `.bin` format. GGUF cleanup models from OpenTranscribe are a separate runtime and must not appear as speech models.
- Download catalog URLs use immutable Hugging Face revisions and SHA-256 digests. Publish a model only after size and digest checks. Partial downloads must never be selectable.
- Support Android 10+ and package ARM64 for phones plus x86_64 for testing. Keep upstream C++ pinned and unmodified; pin NDK and CMake in Gradle.
- A build proves packaging, not microphone quality or Galaxy latency. Record physical-device checks separately from automated results.

## Completion Checklist

- Test editor session isolation, model integrity, and resource cleanup when changing those boundaries.
- Run the project checks and document any device-only gaps.
- Update this authored guidance when a verified architectural decision changes.

## Verified Android Behaviors

- Disable baseline alignment on horizontal key rows. Mixed label sizes otherwise shift key backgrounds vertically to align the text baselines.
- Apply system/IME insets to the settings root container so the ScrollView's viewport shrinks around the focused test field. Padding only the ScrollView can leave the editor behind the keyboard.
- Reserve a bottom navigation area in the IME. Android's IME navigation controls can overlay the input view even when reported navigation insets are consumed.
- Keep native code optimized in debug builds; otherwise a sideloaded debug APK gives misleading Whisper latency.
- Release versions live in `version.properties`. Releases require a persistent signing identity from environment secrets and must never fall back to an ephemeral debug signature.
