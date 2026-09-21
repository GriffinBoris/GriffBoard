# Verification

## Automated checks

Run `task local:check` for release-script tests, JVM tests, Android lint, and both packaged native ABIs.

JVM tests cover model size/digest failures, cancellation during downloads, model catalog pins, microphone lifecycle failure/retry, concurrent recording rejection, silence handling, cancellation during transcription, editor session isolation, password-field classification, audio retention beyond 30 seconds, and elapsed-time updates/reset.

Run `task local:test:device` on an ARM64 or x86_64 Android emulator/device. Instrumentation checks Unicode deletion, selected-text deletion, editor actions, JNI loading, native cancellation, and invalid model errors. The optional known-speech test uses `jfk.wav` and runs when **Tiny · English** is downloaded in the app; otherwise that single test is skipped.

For a deterministic inference check:

1. Install the debug APK on the test device.
2. Download **Tiny · English** in GriffBoard.
3. Run `task local:test:device`.

The test sample is included only in the test APK. The microphone lifecycle test grants microphone permission and records for 31 seconds, then checks cancellation and restart. Run it on an emulator or a device where test recording is appropriate. UI tests verify overlapping finger touches, sentence capitalization, suggestion insertion, elapsed time, fixed keyboard height during recording, and tappable waveform controls. Waveform rendering previews use synthetic signed sample peaks; JVM tests check exact peaks from PCM input.

## Physical phone checklist

An emulator cannot establish Galaxy microphone quality, battery use, thermal behavior, or practical model latency. Perform these checks on the target phone:

- Enable and select GriffBoard using its setup screen.
- Type in messaging, browser, multiline, email, URL, password, and numeric fields.
- Verify shift, caps lock, symbols, emoji, number row, long-press delete, spacebar keyboard picker, and editor actions.
- Tap Shift for one uppercase letter. Tap again while uppercase is selected to lock capitals, then tap once more to return to lowercase. Check the outlined, filled, and underlined arrow states.
- Type quickly with two thumbs. Check letters around automatic Shift changes and near key edges, and try English suggestions in the toolbar.
- Compare portrait/landscape and light/dark appearance, including gesture navigation and larger display text.
- Grant microphone access and dictate a short sentence using Tiny or Base.
- Dictate for more than 30 seconds. Check that the timer continues, the waveform responds to speech, and Stop begins the transcription animation.
- Download a model, enable airplane mode, and dictate again.
- While recording, hide the keyboard or switch to another app. Verify capture stops.
- While transcribing, change fields. Verify no transcript appears in the new field.
- Deny microphone access, then grant it from settings and retry.
- Cancel a model download and retry. An incomplete file must not appear as ready.
- Select another downloaded model, remove a model, and restart the app.
- Measure Base, Small, and Turbo latency before selecting the largest model for daily use.

## Recorded results

See the implementation status below for the checks performed during setup. Physical-device performance remains unverified until a phone is connected.

- v0.2.0 release preparation: `task local:check` passed, and all ten emulator instrumentation tests passed on API 36 ARM64. Guidance validation and all ten package-integrity checks passed.

- Android API 36 ARM64 emulator: five instrumentation tests passed, including actual Tiny English transcription of the JFK fixture. The inference test was confirmed to execute without skipping.
- Recording update: eighteen JVM tests and seven emulator instrumentation tests passed, including capture beyond 30 seconds, microphone cancellation/restart, voice controls, and actual Tiny English transcription. The real keyboard showed recording at 32 seconds; waveform previews were visually checked using synthetic levels.
- Typing and waveform update: twenty-two JVM tests and nine emulator tests passed. Two-finger input survived automatic Shift changes, punctuation capitalized the next letter, and a suggestion replaced a word without changing adjacent punctuation. The waveform overlay retained the typing keyboard's height. The actual IME displayed English completions for `hel`, and tapping `hello` inserted `hello `.
- Two-page symbols: visually compared both pages with the supplied references. Checked page switching, math and currency entry, deletion, and returning to letters with ABC. Unit tests, Android lint, and debug/release builds passed.
- UI polish: twenty-two JVM tests, debug/release lint, and three focused emulator UI tests passed. Checked compact setup, preferences, model action priority, dark/light typing, 130% text, landscape, and visible no-speech feedback. Added a regression assertion that voice errors override suggestions and suggestions return after clearing the message. Verified dark navigation icons on the light IME. Recording/transcription screenshots from the UI test use synthetic waveform samples; actual silent recording confirmed the error flow. TalkBack gestures and physical-device typing remain unverified.
- Shift and Delete icons: twenty-two JVM tests and four focused emulator UI tests passed, plus debug/release lint and builds. Checked one-letter Shift, a second tap for caps lock, persistence across letters and punctuation, explicit return to lowercase despite automatic capitalization, editor reset, and Delete using the new icon. The overlapping-touch regression still passes.
- Android lint passed with warnings treated as errors; dependency-update notices are excluded because toolchain upgrades are reviewed explicitly.
- Release scripts: version/tag mismatch, invalid versions, non-increasing Android codes, dirty checkouts, annotated tag creation, and duplicate-tag rejection passed in a disposable Git fixture.
- APM: local guidance validation and all ten package-integrity checks passed.
- Workflow YAML: actionlint passed.
- Manual emulator checks: setup, model selection, on-screen typing, microphone start/stop, light/dark settings, and gesture-navigation spacing.
- Downloaded Base English through the app's WorkManager flow and independently verified the installed SHA-256 digest. Selected it and confirmed the keyboard used that selection.
- Built and linted a release APK with a disposable verification key. Android's APK signature verifier and 16 KB ZIP alignment check passed. This does not configure a production signing identity or publish a release.
