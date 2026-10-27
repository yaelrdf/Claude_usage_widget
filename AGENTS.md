# Agent guide

These are orientation notes for AI coding agents (Claude Code, Codex, etc.) working on this repo. Humans should read [README.md](README.md) and [CONTRIBUTING.md](CONTRIBUTING.md) first.

## What this is

This is an Android home-screen widget showing Claude plan usage: the 5-hour session limit and the weekly limit. Everything runs on the phone. It's written in Kotlin in a single `:app` module, using Jetpack Glance (widget), Compose (app screen), WorkManager, DataStore and OkHttp. The data comes from an **unofficial** endpoint; see [docs/usage-api.md](docs/usage-api.md).

## Commands

```bash
./gradlew testDebugUnitTest     # fast JVM tests — run after every change
./gradlew assembleDebug         # must pass before you finish
./gradlew lintDebug             # Android lint
```

- JDK 17+ must be on `JAVA_HOME`, and the Android SDK (platform 37) on `ANDROID_HOME` or in `local.properties`.
- On the maintainer's Linux machine there is no system JDK. Use the Flatpak Android Studio JBR: `export JAVA_HOME=$(dirname "$(dirname "$(find /var/lib/flatpak/app/com.google.AndroidStudio -path '*jbr/bin/java' | head -1)")")`.
- The first build downloads Gradle and the dependencies, which takes several minutes. Run long builds in the background.

## Map

| Path | Role |
|---|---|
| `app/src/main/java/dev/yaelrdf/claudewidget/data/ClaudeApi.kt` | All endpoints, OAuth PKCE, HTTP → `ApiResult` |
| `app/src/main/java/dev/yaelrdf/claudewidget/data/Usage.kt` | Models + `UsageParser` (handles two response shapes) |
| `app/src/main/java/dev/yaelrdf/claudewidget/data/UsageRepository.kt` | **The only refresh pipeline**, guarded by a mutex |
| `app/src/main/java/dev/yaelrdf/claudewidget/data/Store.kt` | DataStore + Keystore encryption, `WidgetState` flow |
| `app/src/main/java/dev/yaelrdf/claudewidget/data/Appearance.kt` | Theme options → Material schemes / widget palettes |
| `app/src/main/java/dev/yaelrdf/claudewidget/widget/UsageWidget.kt` | Glance widget, responsive breakpoints, refresh action |
| `app/src/main/java/dev/yaelrdf/claudewidget/work/RefreshWorker.kt` | Periodic (15 min) and one-off refresh scheduling |
| `app/src/main/java/dev/yaelrdf/claudewidget/MainActivity.kt` | Sign-in, dashboard, appearance settings + live preview |
| `app/src/main/res/xml/usage_widget_info.xml` | Widget sizes: default 2×1, minimum 1×1 |
| `docs/` | Architecture, API notes, layouts, building, releasing |

## Rules that matter here

1. **Never log, print, commit or echo tokens.** That includes `~/.claude/.credentials.json` on the computer. Read it into a shell variable and use it inline, as in docs/usage-api.md. Test fixtures must be trimmed and contain **made-up numbers**, never a raw account response.
2. **Treat the API as hostile to change.** Parse defensively and ignore unknown keys. When the shape changes, add a fixture and a test in `UsageParserTest` first.
3. **All fetching goes through `UsageRepository.refresh()`.** Don't call `ClaudeApi.fetchUsage` from UI code. Refresh tokens rotate, and the mutex keeps them from being used twice.
4. **Keep the three widget renderings in sync** when you change the layout:
   - the Glance code (`UsageWidget.kt`),
   - the app preview (`MainActivity.WidgetPreview`),
   - the picker preview (`res/layout/widget_preview.xml`).
   Update `docs/widget-layouts.md` too.
5. **AGP 9 has Kotlin built in.** Don't apply `org.jetbrains.kotlin.android`. Versions go in `gradle/libs.versions.toml`.
6. **Glance is not Compose UI.** Use `androidx.glance.*` composables and modifiers (`GlanceModifier.defaultWeight()`, `cornerRadius`, …). The reified `actionStartActivity<T>()` is in `androidx.glance.action`.
7. **15 minutes is the floor** for periodic refresh (WorkManager). Don't add foreground services or tight alarms just to refresh faster.
8. Match the existing style: small files, KDoc on non-obvious things, no DI framework, `Context`-constructed classes.
9. Every user-visible change goes into `CHANGELOG.md` under **Unreleased**.

## Verifying changes

- Logic in `data/` and `format/`: add or extend a JVM unit test under `app/src/test/`.
- The widget and UI have no automated UI tests. Build the APK and say plainly that testing on a device is still needed. The local emulator has been unreliable headless.
- Release APKs are signed with the debug key unless the signing environment variables are set (docs/releasing.md).
