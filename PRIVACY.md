# Privacy

GriffBoard processes keyboard input and voice recordings on the device. It does not upload text or audio, require an account, include analytics, or keep a typing history.

Microphone access is requested in the launcher activity. Recording starts only after tapping the keyboard microphone. Audio is held in memory, limited to 30 seconds, and discarded after transcription or cancellation. The transcript is inserted into the focused app; that app controls what happens to inserted text afterward.

Internet access is used only for user-requested model downloads from Hugging Face and its download infrastructure. Those services receive normal download request metadata, including the device's IP address. Downloads use pinned revisions and verified checksums.

Model files and keyboard preferences remain in app-private storage. App backup and device transfer are disabled. Models can be removed in settings; uninstalling GriffBoard removes its stored data.

Voice typing is disabled in password fields. The app does not log keystrokes, audio, or transcripts.
