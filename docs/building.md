# Building

## Toolchain

| Tool | Version | Where it's set |
|---|---|---|
| Gradle | 9.8.1 (wrapper, downloaded automatically) | `gradle/wrapper/gradle-wrapper.properties` |
| Android Gradle Plugin | 9.4.1 | `gradle/libs.versions.toml` |
| Kotlin | 2.4.21 | `gradle/libs.versions.toml` |
| JDK | 17 or later (21 recommended; Android Studio's bundled JBR works) | `JAVA_HOME` |
| Android SDK | Platform **37**, build-tools 36+ | `ANDROID_HOME` or `local.properties` |
| minSdk / targetSdk | 26 / 37 | `app/build.gradle.kts` |

All dependency versions are kept in the version catalog, `gradle/libs.versions.toml`. Add new dependencies there, not inline.

> **AGP 9 has Kotlin built in.** Don't add the `org.jetbrains.kotlin.android` plugin; AGP rejects it. Only the Compose compiler plugin (`org.jetbrains.kotlin.plugin.compose`) is applied, and its version must match `kotlin`.

## Android Studio

Open the project folder and let it sync. Then run the `app` configuration on a device or emulator. To try the widget, add it from the launcher's widget picker.

## Command line

```bash
export ANDROID_HOME="$HOME/Android/Sdk"          # or create local.properties with sdk.dir=…
export JAVA_HOME=/path/to/jdk-17-or-later

./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease      # app/build/outputs/apk/release/app-release.apk
./gradlew testDebugUnitTest    # JVM unit tests (parser, formatting)
./gradlew lintDebug            # Android lint → app/build/reports/lint-results-debug.html
```

Install on a connected device with USB debugging enabled:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Using Android Studio's JDK on Linux

If Android Studio is installed but there's no system JDK, point `JAVA_HOME` at the JBR it bundles:

```bash
# Flatpak install
export JAVA_HOME=$(dirname "$(dirname "$(find /var/lib/flatpak/app/com.google.AndroidStudio -path '*jbr/bin/java' | head -1)")")
# Tarball install
export JAVA_HOME=~/android-studio/jbr
```

## Signing

- **Debug builds** use the local debug keystore at `~/.android/debug.keystore`.
- **Release builds** use the signing key from environment variables, if you set them. Without them, release builds fall back to the debug key so they still install for testing. See [releasing.md](releasing.md).

Android won't install an APK over one signed with a different key. If you switch between your own builds and CI builds, uninstall first. This also removes the sign-in.

## Troubleshooting

| Symptom | Fix |
|---|---|
| `SDK location not found` | Set `ANDROID_HOME`, or create `local.properties` with `sdk.dir=/path/to/Android/Sdk`. That file is git-ignored. |
| `Failed to find target with hash string 'android-37'` | Install it: `sdkmanager "platforms;android-37.0"`, or use Android Studio's SDK Manager. |
| `Unsupported class file major version` / JDK errors | Use JDK 17 or later for Gradle. Check with `./gradlew --version`. |
| `actionStartActivity` type-argument error | Import the reified version from `androidx.glance.action`, not `androidx.glance.appwidget.action`. |
| The emulator hangs or crashes when started headless | Use a physical device, or start the emulator from Android Studio with a window. |
| Widget doesn't update after reinstalling | Remove the widget and add it again. Launchers cache the old layout and size. |
| "Sign in again" right after connecting | The pasted code was stale. Tap **Sign in with Claude** again. Each attempt uses a new `state`. |
