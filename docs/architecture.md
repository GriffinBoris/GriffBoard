# Architecture

GriffBoard is one Android app module. It has no backend and no account system.

| Area | Responsibility |
| --- | --- |
| `keyboard/GriffBoardService` | Android IME lifecycle, current editor, voice session ownership, text insertion |
| `keyboard/KeyboardView` | Key layout, light/dark colors, haptics, shift, symbols, emoji, repeat delete |
| `keyboard/EditorActions` | Password classification, Unicode deletion, editor actions |
| `settings/` | Setup, runtime permission, model cards, preferences, keyboard test field |
| `dictation/` | Optional standalone voice shortcuts, accessibility editor connection, floating panel, and foreground microphone service |
| `models/` | Immutable catalog, private storage, verified WorkManager downloads |
| `voice/VoiceController` | Recording/transcription state and cancellation |
| `voice/AudioCapture` | 16 kHz mono PCM capture, elapsed time and signed waveform peaks, microphone cleanup |
| `voice/PcmRecording` | Growing in-memory PCM chunks, conversion for Whisper after stopping |
| `keyboard/VoiceWaveform` | Signed microphone waveform; recorded shape pulses during transcription |
| `keyboard/RecordingOverlay` | Timer and tappable waveform covering the key area during dictation |
| `keyboard/WordSuggestions` | Offline English completions, one-edit corrections, and basic next-word choices |
| `keyboard/AutoCorrect` | Common-typo and dictionary replacements with cursor-checked undo |
| `voice/WhisperTranscriber` | Coroutine/JNI boundary and native-job ownership |
| `cpp/` | Pinned whisper.cpp build and a small JNI adapter |

Dictionary auto-correction considers unknown lowercase English words of 4–24 letters with one insertion, deletion, substitution, or adjacent transposition. A match needs at least 1,000 occurrences in the bundled corpus and four times the frequency of the next candidate. These are conservative ranking rules, not probability estimates. Existing common-typo rules also apply. Valid dictionary words and ambiguous matches are not automatically replaced. The same resolver chooses the preview and the correction at a word boundary, so acceptance does not depend on the preview having appeared.

## Voice lifecycle

The microphone starts one recording. Stopping it releases the microphone before inference begins. An energy threshold rejects silent or very short clips; this is not a streaming voice activity detector. Inference runs on an IO dispatcher, and results return to the main thread.

Each recording carries an editor-session generation. Finishing input, hiding the keyboard, switching fields, or opening settings invalidates that generation. Cancellation stops recording, sets the native atomic cancellation flag, and cancels the coroutine. Native model loading completes before cancellation can release the model; inference checks cancellation through whisper.cpp's abort callback. A new recording cannot overlap cleanup from the previous one.

The controller has small recorder/transcriber interfaces to test hardware failure and cancellation without a real microphone. Ordinary layout and storage code use direct Android APIs.

## Standalone dictation

Android 13+ provides an Accessibility input method alongside the selected keyboard. `DictationAccessibilityService` requests `flagInputMethodEditor` without screen-content retrieval. It owns a non-focusable accessibility overlay, a `VoiceController`, and an editor-session generation. Field or cursor changes invalidate the target connection and cancel active work. App changes and screen locking also cancel work. The service is disabled by a version-qualified resource on older Android versions.

Four independent preferences control the floating microphone, Quick Settings tile, notification shortcut, and launcher alias used by configurable buttons. They default off. Each starts `VoiceLaunchActivity`, which checks that its source remains enabled, obtains microphone permission, and stays visible until the service acknowledges foreground startup. The panel starts ready, allowing the original editor to regain focus before the user taps Record. Android requires the microphone foreground-service status while the panel is open, independently of the optional notification launcher.

The overlay shares the keyboard's waveform, recorder, transcriber, model store, and voice language. It never switches the default keyboard. The captured Accessibility input connection receives the transcript only while its generation is valid. Android's Accessibility commit preserves existing composing text. Unsupported editors can use the explicit Copy action. The latest result stays only in panel memory. Closing the panel removes its window and clears the result; microphone cleanup finishes before foreground status stops. Idle panels close after two minutes. The Accessibility binding can remain active for the optional floating button, without foreground microphone status or recording.

The native wrapper returns UTF-8 bytes rather than JNI modified UTF-8 strings, preserving multilingual transcripts. Contexts are freed after each utterance to release memory while the keyboard is idle. Model caching and hardware acceleration are future optimizations requiring phone measurements.

## Model lifecycle

The app downloads only built-in HTTPS catalog URLs with immutable revisions. Each download writes a unique `.part` file, checks its byte count and SHA-256 digest, and atomically renames it into the model directory. Incomplete files are never selectable. Model choice is explicit and survives restarts. Selecting or deleting a model does not start inference.

The keyboard and settings run in the same process. Android opens settings after input is cancelled. Downloads are independent WorkManager jobs; progress is observed through lifecycle-aware LiveData. Models and audio are excluded from cloud backup and device transfer.

## Build and automation

Gradle owns application identity, dependencies, compilation, tests, lint, and packaging. Task exposes concise `local:*`, `ai:*`, and `release:*` commands. `version.properties` is the single source of release version and Android version code.

CMake downloads whisper.cpp v1.9.4 with archive SHA-256 verification. It builds CPU-only ARM64 and x86_64 libraries with 16 KB page alignment. Native code is optimized in both debug and release APKs. There is no runtime download of executable code.

GitHub checks build and retain a debug APK. Tag-driven releases require a persistent Android signing key, validate the tag against `version.properties`, run verification, and publish a signed APK with checksums only after the draft release has every asset.
