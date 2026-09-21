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
- Auto-correct previews common English typo fixes and strong dictionary spelling matches before applying them. Tap X to keep your spelling; otherwise Space, punctuation, or Enter accepts the correction. You can also tap the preview to accept it. After acceptance, tap Undo or immediately press Backspace to restore the original word. You can turn auto-correct off in Keyboard preferences.
- Sentence-ending punctuation capitalizes the next letter. Shift updates key labels without rebuilding touch targets, so overlapping taps remain active.
- Six downloadable, selectable models: Tiny English, Base English, Base multilingual, Small, Medium, and Large v3 Turbo.
- Download progress, cancellation, removal, size checks, and SHA-256 verification before models become usable.
- On-device inference, no account, no telemetry, no cloud transcription, and no saved audio or typing history.
- Optional voice shortcuts on Android 13+: a floating microphone, Quick Settings tile, notification shortcut, and app shortcut for a configurable side button. Keep your current keyboard selected.

This version does not include swipe typing, personalized predictions, continuous streaming dictation, or arbitrary model imports. English suggestions use a bundled frequency dictionary and a small set of common next-word choices. Automatic correction combines common-typo rules with single-edit matches for unknown words when one spelling clearly outranks the alternatives. Ambiguous suggestions remain manual choices. The keyboard layout is English; multilingual models can transcribe other languages.

## Install

Requires Android 10 or newer on ARM64 or x86_64. Current Galaxy phones use ARM64.

1. Build with `task local:build`, or obtain an APK from a configured GitHub release.
2. Transfer `app/build/outputs/apk/debug/app-debug.apk` to the phone and open it. Allow installation from that source when Android asks.
3. Open GriffBoard and use **Enable GriffBoard**, **Choose keyboard**, and **Allow microphone**.
4. Download **Base · English** or **Base · Multilingual**, then select **Use model**.
5. Tap the test field or open another app. Tap the keyboard microphone, speak, and tap stop.

Android displays its standard third-party keyboard warning during setup. The app's internet permission is used for requested model downloads. Dictation works offline after downloading a model.

Closing GriffBoard's keyboard, switching editors, or opening settings cancels keyboard voice typing. Voice typing is disabled in password fields. A late transcript cannot be inserted into a different editor session.

## Voice with Samsung Keyboard or another keyboard

On Android 13 or newer, open **GriffBoard → Voice shortcuts**. Download and select a model in the main settings first. Enable **GriffBoard Voice** through the Accessibility setup button. The optional service provides the floating panel and access to the active editor; it does not retrieve screen contents.

Each shortcut starts off and has its own switch:

- **Floating microphone:** drag to position it, then tap to open the panel.
- **Quick Settings tile:** enable it, then tap **Add Quick Settings tile**.
- **Notification shortcut:** allow notifications to keep a launch shortcut in the notification shade.
- **Side-button / app shortcut:** exposes **GriffBoard Voice** as a launchable app. On supported Samsung phones, choose it under **Settings → Advanced features → Side button → Double press → Open app**. Button options vary by phone; GriffBoard does not intercept hardware keys.

Focus a text field, open any enabled shortcut, and tap **Record**. Tap **Stop** to transcribe with your selected local model. The panel shows a live waveform and elapsed time, then pulses during transcription. It sends text to the original editor. **Copy** is available if an app does not accept direct insertion; copying is never automatic. Changing fields, moving the cursor, changing apps, locking the screen, or closing the panel cancels active dictation. An idle panel closes after two minutes.

The notification shortcut is optional. Android's foreground-service status is separate and remains required while the dictation panel is open. Enabling a shortcut alone does not record audio. Android 10–12 can continue using voice typing inside GriffBoard's keyboard.

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
