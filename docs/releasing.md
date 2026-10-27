# Releasing

## Versioning

The project uses [Semantic Versioning](https://semver.org/). Both numbers live in `app/build.gradle.kts`:

- `versionName`: the user-facing version, e.g. `1.3.0`.
- `versionCode`: an integer that **must increase** with every release. Android refuses to install an update with a lower or equal versionCode.

## Checklist

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Move the `Unreleased` items in [CHANGELOG.md](../CHANGELOG.md) under a new version heading.
3. Run `./gradlew testDebugUnitTest lintDebug assembleRelease`, then smoke-test on a device. Check sign-in, ↻, and the 1×1, 2×1 and 2×2 sizes.
4. Commit, then tag and push:
   ```bash
   git tag v1.3.0
   git push origin main --tags
   ```
5. The **Release** workflow (`.github/workflows/release.yml`) builds a signed APK and attaches it to a GitHub Release named after the tag.

## Signing key

Release builds are signed with the keystore given in these environment variables, which `app/build.gradle.kts` reads:

| Variable | Meaning |
|---|---|
| `SIGNING_KEYSTORE_PATH` | Path to the `.jks` file |
| `SIGNING_STORE_PASSWORD` | Keystore password |
| `SIGNING_KEY_ALIAS` | Key alias |
| `SIGNING_KEY_PASSWORD` | Key password |

If `SIGNING_KEYSTORE_PATH` isn't set, the release build is signed with the debug key. That build is fine for testing, but not for distribution.

Create a key once and keep it safe. Losing it means users must uninstall to update.

```bash
keytool -genkeypair -v -keystore release.jks -alias claude-widget \
  -keyalg RSA -keysize 4096 -validity 10000
```

Then add four repository secrets in GitHub (**Settings → Secrets and variables → Actions**):

- `SIGNING_KEYSTORE_BASE64`: the output of `base64 -w0 release.jks`
- `SIGNING_STORE_PASSWORD`
- `SIGNING_KEY_ALIAS`
- `SIGNING_KEY_PASSWORD`

The release workflow decodes the keystore into a temporary file and exports the variables above. If the secrets are missing, it still publishes an APK, but signed with a throwaway debug key, and it says so in the release notes.

**Never commit the keystore or its passwords.** `.gitignore` already excludes `*.jks`, `*.keystore` and `keystore.properties`.
