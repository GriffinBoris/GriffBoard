# Privacy

GriffBoard processes keyboard input and voice recordings on the device. It does not upload text or audio, require an account, include analytics, or keep a typing history.

English suggestions use a bundled dictionary and nearby text from the current editor. That text is processed in memory, is not saved, and is cleared when the input session ends. Suggestions do not use a remote service or a learned personal vocabulary. Password, email, URL, and numeric fields do not receive suggestions.

Automatic corrections use local common-typo rules. The latest correction and its surrounding text stay in memory only long enough to offer Undo. Starting another edit, changing fields, or closing the keyboard clears that undo state. No personal correction history is kept.

Microphone access is requested in the launcher activity. Recording starts only after tapping the keyboard microphone and continues until you stop or cancel it. Audio is held in memory and discarded after transcription or cancellation. The transcript is inserted into the focused app; that app controls what happens to inserted text afterward.

Internet access is used only for user-requested model downloads from Hugging Face and its download infrastructure. Those services receive normal download request metadata, including the device's IP address. Downloads use pinned revisions and verified checksums.

Model files and keyboard preferences remain in app-private storage. App backup and device transfer are disabled. Models can be removed in settings; uninstalling GriffBoard removes its stored data.

Voice typing is disabled in password fields. The app does not log keystrokes, audio, or transcripts.
