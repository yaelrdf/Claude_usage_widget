# Claude Usage Widget

An Android home-screen widget that shows your **Claude plan usage**: the 5-hour session limit and the weekly limit, each with a progress bar, a percentage and a reset countdown.

It runs entirely on the phone. You sign in once with your Claude account, and the widget refreshes on its own every 15 minutes. Tap ↻ to refresh right away.

> [!WARNING]
> **Unofficial project.** It isn't affiliated with or endorsed by Anthropic. Anthropic has no public API for subscription usage. This app reads the same **undocumented** endpoint Claude Code's `/usage` command uses, and signs in through Claude Code's OAuth client. Either can change or stop working at any time. See [docs/usage-api.md](docs/usage-api.md).

```
┌──────────────────────────────┐
│Session                       │
│████████░░░░░░░░░░░░░░   42%  │
│resets 2h 13m                 │
│Weekly                        │
│███░░░░░░░░░░░░░░░░░░░   18%  │
│resets Thu 09:00        4m ↻  │
└──────────────────────────────┘
```

## Features

- **Session (5h) and weekly limits**, plus Opus or Sonnet weekly limits when your plan reports them.
- **Responsive.** The minimum size is 1×1 and the default is 2×1, and the layout adapts up to large sizes. See [docs/widget-layouts.md](docs/widget-layouts.md).
- **Theming:**
  - **Colors:** *Match phone* (Material You), *Claude* or *Mono*.
  - **Brightness:** Auto, Light, Dark, or Black for AMOLED screens.
  - **Bar colors:** by usage level, or your accent color.
  - **Background opacity:** adjustable.
  - A live preview in the app.
- **Background refresh** every 15 minutes (Android's minimum for periodic work). The ↻ button and opening the app also refresh.
- **Own sign-in on the phone.** OAuth PKCE gives the phone its own token pair, which renews itself and never touches a Claude Code login on your computer. Tokens are encrypted with an Android Keystore key.
- **Survives failures.** If a refresh fails, the widget keeps showing the last good numbers and marks the error with a red **!**.

## Install

1. Download the APK from [Releases](../../releases), or [build it yourself](docs/building.md).
2. Install it on your phone. You'll need to allow installs from unknown sources for your browser or file manager.
3. Open **Claude Usage**, then tap **Sign in with Claude**.
4. Approve access in the browser, copy the code it shows, paste it into the app and tap **Connect**.
5. Long-press your home screen, open **Widgets** and add **Claude usage**.

Requires Android 8.0 (API 26) or later. *Match phone* colors need Android 12 or later.

## Build from source

```bash
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # unit tests
```

You need JDK 17 or later and the Android SDK, with platform 37 installed. Android Studio's bundled JDK works. For the full setup, including Linux and Flatpak notes, see [docs/building.md](docs/building.md).

## Documentation

| Doc | What's in it |
|---|---|
| [docs/architecture.md](docs/architecture.md) | Modules, data flow, refresh scheduling, storage |
| [docs/usage-api.md](docs/usage-api.md) | The unofficial usage endpoint and OAuth flow, with response shapes |
| [docs/widget-layouts.md](docs/widget-layouts.md) | Size breakpoints and what each layout shows |
| [docs/building.md](docs/building.md) | Toolchain, build commands, troubleshooting |
| [docs/releasing.md](docs/releasing.md) | Versioning, signing, GitHub releases |
| [CONTRIBUTING.md](CONTRIBUTING.md) | How to contribute |
| [AGENTS.md](AGENTS.md) | Orientation for AI coding agents (also loaded through `CLAUDE.md`) |

## Privacy

The app only talks to Anthropic's own endpoints: `claude.com`, `platform.claude.com` and `api.anthropic.com`. It has no analytics, ads or third-party servers. Your tokens and your last usage reading stay on the device, and Android backup is disabled for the app.

## License

[MIT](LICENSE). "Claude" and "Anthropic" are trademarks of Anthropic, PBC. They're used here only to describe what the app works with.
