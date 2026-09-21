# GriffBoard

A sideloadable Android keyboard with Samsung-inspired keys and local Whisper voice typing.
Built with Kotlin, native Android views, Material components, and whisper.cpp.

## What works

- English QWERTY, optional number row, two symbol pages, a small emoji panel, repeat delete, and numeric fields.
- Shift and Delete use vector icons. Tap Shift for one uppercase letter, tap it again while selected for caps lock, and tap once more for lowercase.
- Editor actions such as Send, Search, Next, and Done. Hold the spacebar to switch keyboards.
- System light/dark appearance and optional key vibration.
- Tap the microphone to replace the keys with a timer and live microphone waveform. Tap the waveform to stop and transcribe. The captured waveform pulses during transcription; tap again to cancel. There is no fixed recording limit. Longer recordings use more memory.
- English suggestions appear in the toolbar: word completion, simple spelling corrections, and common next-word choices. Tap a suggestion to insert it. Suggestions run offline, do not learn or store typed text, and are disabled in passwords, email addresses, URLs, numeric fields, and editors that request no suggestions.
- Sentence-ending punctuation capitalizes the next letter. Shift updates key labels without rebuilding touch targets, so overlapping taps remain active.
- Six downloadable, selectable models: Tiny English, Base English, Base multilingual, Small, Medium, and Large v3 Turbo.
- Download progress, cancellation, removal, size checks, and SHA-256 verification before models become usable.
- On-device inference, no account, no telemetry, no cloud transcription, and no saved audio or typing history.

This version does not include swipe typing, automatic word replacement, personalized predictions, continuous streaming dictation, or arbitrary model imports. English suggestions use a bundled frequency dictionary and a small set of common next-word choices. The keyboard layout is English; multilingual models can transcribe other languages.

## Install

Requires Android 10 or newer on ARM64 or x86_64. Current Galaxy phones use ARM64.

1. Build with `task local:build`, or obtain an APK from a configured GitHub release.
2. Transfer `app/build/outputs/apk/debug/app-debug.apk` to the phone and open it. Allow installation from that source when Android asks.
3. Open GriffBoard and use **Enable GriffBoard**, **Choose keyboard**, and **Allow microphone**.
4. Download **Base · English** or **Base · Multilingual**, then select **Use model**.
5. Tap the test field or open another app. Tap the keyboard microphone, speak, and tap stop.

Android displays its standard third-party keyboard warning during setup. The app's internet permission is used for requested model downloads. Dictation works offline after downloading a model.

Closing the keyboard, switching editors, or opening settings cancels voice typing. Voice typing is disabled in password fields. A late transcript cannot be inserted into a different editor session.

## Develop

Open this repository in Android Studio and let Gradle sync. The project uses the existing Gradle 9.6.0 / Android Gradle Plugin 9.4.1 setup and Java 25 toolchain. Android SDK 37, NDK 28.2.13676358, and CMake 3.22.1 are pinned. Gradle can install the native tools when SDK licenses are accepted.

For terminal builds, set `JAVA_HOME` to a Java 25 installation. Android Studio's bundled JDK works. Set the SDK path through `local.properties` or `ANDROID_HOME`; keep machine paths out of tracked files.

```sh
task                         # Available tasks
task local:check             # Release script tests, JVM tests, Android lint, debug APK
task local:build             # Debug APK
task local:install           # Install on one connected device/emulator
task local:test:device       # Android instrumentation tests
task ai:install              # Install pinned shared agent guidance
task ai:generate             # Regenerate guidance after editing .apm/
task ai:check                # Validate guidance and installed package integrity
```

[Task](https://taskfile.dev) is optional: Android tasks wrap `./gradlew`. Agent tooling uses [APM](https://github.com/microsoft/apm) 0.28.0 or newer and [GriffinBoris/Agents v0.1.0](https://github.com/GriffinBoris/Agents/tree/v0.1.0). Commit `.apm/`, `apm.yml`, and `apm.lock.yaml`; generated harness output stays ignored.

The first native build downloads the pinned whisper.cpp source archive. Models download separately from the app and are never bundled in the APK or repository.

## Models and performance

| Model | Download | Use |
| --- | ---: | --- |
| Tiny English Q5 | 32 MB | Short notes and initial testing |
| Base English Q5 | 59 MB | Everyday English dictation |
| Base multilingual Q5 | 59 MB | Compact multilingual option |
| Small multilingual Q5 | 190 MB | Higher accuracy, more processing |
| Medium multilingual Q5 | 539 MB | Higher memory use and latency |
| Large v3 Turbo Q5 | 574 MB | Higher memory use; evaluate on your phone |

Download size is not runtime memory use. Begin with Base and compare accuracy and latency on your phone before moving up. The CPU runtime uses up to four threads and loads a model for each utterance, then frees it. Models stay in private app storage; removing the app removes its downloaded models.

Downloads use WorkManager and survive activity recreation. Interrupted or cancelled downloads restart from the beginning. Android can stop long-running background work, so use a reliable connection for larger models.

Model revisions, sizes, and checksums for Small, Medium, and Turbo follow [OpenTranscribe's catalog](https://github.com/GriffinBoris/OpenTranscribe/blob/main/apps/desktop/src-tauri/src/local_models/catalog.rs). Tiny and Base use the same pinned upstream Hugging Face repository. OpenTranscribe's S1 GGUF cleanup model is a separate text model and is not compatible with this speech runtime.

## Repository notes

- [Architecture](docs/architecture.md)
- [Device verification](docs/testing.md)
- [Releases and signing](docs/releases.md)
- [Privacy](PRIVACY.md)
- [Third-party notices](app/src/main/assets/THIRD_PARTY_NOTICES.md)
