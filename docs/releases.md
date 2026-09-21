# Releases and signing

The release workflow follows OpenTranscribe's tag validation and draft-before-publication pattern. Every `v*` tag triggers Android verification and creates a release containing a signed universal APK and `SHA256SUMS.txt`. A semver suffix such as `-rc.1` marks a prerelease.

`version.properties` supplies both the app version and Android's increasing integer version code. The workflow rejects tags that do not match that file. Manual workflow runs must target a matching tag.

## One-time signing setup

Android requires every installable APK to be signed. Use one persistent release key so later APKs can update the installed app. The build never uses a temporary debug key for published releases.

1. In Android Studio, select **Build → Generate Signed App Bundle / APK → APK**.
2. Create a release keystore outside the repository and record its alias and passwords in your password manager.
3. Back up the keystore in secure storage. Losing the key prevents updates to existing installations.
4. Create a GitHub environment named `release` in `GriffinBoris/GriffBoard`.
5. Add these environment secrets:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64 contents of the keystore |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Signing key alias |
| `ANDROID_KEY_PASSWORD` | Signing key password |

The CLI can upload the keystore without printing it:

```sh
base64 < /path/to/griffboard-release.jks | gh secret set ANDROID_KEYSTORE_BASE64 --repo GriffinBoris/GriffBoard --env release
gh secret set ANDROID_KEYSTORE_PASSWORD --repo GriffinBoris/GriffBoard --env release
gh secret set ANDROID_KEY_ALIAS --repo GriffinBoris/GriffBoard --env release
gh secret set ANDROID_KEY_PASSWORD --repo GriffinBoris/GriffBoard --env release
```

The last three commands prompt for values. Do not put passwords in shell history, source files, or workflow YAML.

The `release` environment contains the permanent signing secrets for releases starting with v0.1.0. Keep using that identity for updates. The original keystore and password are stored outside the repository in `~/.config/griffboard/signing/` on the maintainer's Mac. Back up both files in secure storage.

The release workflow stops when the keystore is missing. Debug APKs remain available from normal check runs and local builds.

## Cut a release

1. Update the version with an increasing version code:

   ```sh
   task release:prepare VERSION=0.3.1 VERSION_CODE=5
   ```

2. Run `task local:check`.
3. Commit and push the reviewed changes.
4. Run `task release:tag` to verify the clean checkout and create an annotated local tag.
5. Run `task release:publish` to push that tag and trigger GitHub Actions.
6. Check the **Release** workflow and download its APK from GitHub Releases.

The tag command does not push. The publish command pushes only the named tag. Neither command force-pushes or replaces tags. A published release is not overwritten on reruns. A failed draft release can be retried after correcting the failure.

## Local release build

Set these environment variables through your password manager or current shell, then run `./gradlew assembleRelease`:

- `ANDROID_KEYSTORE_PATH`: absolute path to the persistent keystore
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

The signed output is `app/build/outputs/apk/release/app-release.apk`. Without signing variables, Gradle produces an unsigned release artifact; use the debug APK for immediate sideload testing.

A local debug installation and a production release use different signing identities. Android requires uninstalling the debug app before switching identities, which removes downloaded models. Subsequent release updates retain app data when signed with the same release key and a higher version code.
