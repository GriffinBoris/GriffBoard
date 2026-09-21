# Verification

## Automated checks

Run `task local:check` for release-script tests, JVM tests, Android lint, and both packaged native ABIs.

JVM tests cover model size/digest failures, cancellation during downloads, model catalog pins, microphone lifecycle failure/retry, concurrent recording rejection, silence handling, cancellation during transcription, editor session isolation, and password-field classification.

Run `task local:test:device` on an ARM64 or x86_64 Android emulator/device. Instrumentation checks Unicode deletion, selected-text deletion, editor actions, JNI loading, native cancellation, and invalid model errors. The optional known-speech test uses `jfk.wav` and runs when **Tiny · English** is downloaded in the app; otherwise that single test is skipped.

For a deterministic inference check:

1. Install the debug APK on the test device.
2. Download **Tiny · English** in GriffBoard.
3. Run `task local:test:device`.

The test sample is included only in the test APK. Tests do not require or capture live microphone audio.

## Physical phone checklist

An emulator cannot establish Galaxy microphone quality, battery use, thermal behavior, or practical model latency. Perform these checks on the target phone:

- Enable and select GriffBoard using its setup screen.
- Type in messaging, browser, multiline, email, URL, password, and numeric fields.
- Verify shift, caps lock, symbols, emoji, number row, long-press delete, spacebar keyboard picker, and editor actions.
- Compare portrait/landscape and light/dark appearance, including gesture navigation and larger display text.
- Grant microphone access and dictate a short sentence using Tiny or Base.
- Download a model, enable airplane mode, and dictate again.
- While recording, hide the keyboard or switch to another app. Verify capture stops.
- While transcribing, change fields. Verify no transcript appears in the new field.
- Deny microphone access, then grant it from settings and retry.
- Cancel a model download and retry. An incomplete file must not appear as ready.
- Select another downloaded model, remove a model, and restart the app.
- Measure Base, Small, and Turbo latency before selecting the largest model for daily use.

## Recorded results

See the implementation status below for the checks performed during setup. Physical-device performance remains unverified until a phone is connected.

- Android API 36 ARM64 emulator: five instrumentation tests passed, including actual Tiny English transcription of the JFK fixture. The inference test was confirmed to execute without skipping.
- JVM: sixteen tests passed. Android lint passed with warnings treated as errors; dependency-update notices are excluded because toolchain upgrades are reviewed explicitly.
- Release scripts: version/tag mismatch, invalid versions, non-increasing Android codes, dirty checkouts, annotated tag creation, and duplicate-tag rejection passed in a disposable Git fixture.
- APM: local guidance validation and all ten package-integrity checks passed.
- Workflow YAML: actionlint passed.
- Manual emulator checks: setup, model selection, on-screen typing, microphone start/stop, light/dark settings, and gesture-navigation spacing.
- Downloaded Base English through the app's WorkManager flow and independently verified the installed SHA-256 digest. Selected it and confirmed the keyboard used that selection.
- Built and linted a release APK with a disposable verification key. Android's APK signature verifier and 16 KB ZIP alignment check passed. This does not configure a production signing identity or publish a release.
