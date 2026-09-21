# Privacy

GriffBoard processes keyboard input and voice recordings on the device. It does not upload text or audio, require an account, include analytics, or keep a typing history.

English suggestions use a bundled dictionary and nearby text from the current editor. That text is processed in memory, is not saved, and is cleared when the input session ends. Suggestions do not use a remote service or a learned personal vocabulary. Password, email, URL, and numeric fields do not receive suggestions.

Automatic corrections use local common-typo rules and the bundled dictionary's word frequencies. The latest correction and its surrounding text stay in memory only long enough to offer Undo. Starting another edit, changing fields, or closing the keyboard clears that undo state. No personal correction history is kept.

Microphone access is requested in a visible activity. Recording starts only after tapping the keyboard microphone or Record in the optional voice panel, and continues until you stop or cancel it. Audio is held in memory and discarded after transcription or cancellation. The transcript is inserted into the original editor; that app controls what happens to inserted text afterward.

On Android 13+, the optional GriffBoard Voice Accessibility service lets you dictate without changing keyboards. It receives editor lifecycle, cursor selection, and app-change events to prevent insertion into a different field. It does not request access to screen contents or keep editor or app history. All four launch shortcuts start disabled and can be controlled independently. A foreground-service notification accompanies the open panel. Enabling shortcuts does not start microphone capture.

The panel retains its latest transcript in memory until it closes, including automatic closure after two minutes of inactivity. Only tapping Copy puts that text on the system clipboard, where Android's clipboard rules apply. Changing editors or locking the screen cancels recording or transcription. Password fields cannot start voice input.

Internet access is used only for user-requested model downloads from Hugging Face and its download infrastructure. Those services receive normal download request metadata, including the device's IP address. Downloads use pinned revisions and verified checksums.

Model files and keyboard preferences remain in app-private storage. App backup and device transfer are disabled. Models can be removed in settings; uninstalling GriffBoard removes its stored data.

Voice typing is disabled in password fields. The app does not log keystrokes, audio, or transcripts.
