# Architecture

This is a single-module Android app (`:app`) written in Kotlin. The UI is Jetpack Compose for the app screen and Jetpack Glance for the widget. Background work runs on WorkManager, persistence uses DataStore, and HTTP goes through OkHttp. There is no DI framework: classes are built where they're needed and take a `Context`.

```
dev.yaelrdf.claudewidget
├── MainActivity.kt          Compose screen: sign-in, live numbers, appearance settings + preview
├── data/
│   ├── ClaudeApi.kt         OAuth PKCE (authorize URL, code exchange, refresh) + usage GET
│   ├── Usage.kt             UsageSnapshot / UsageWindow models + defensive JSON parser
│   ├── Store.kt             DataStore: encrypted tokens, cached usage body, status, appearance
│   ├── UsageRepository.kt   The one refresh pipeline (token renew → fetch → store → redraw)
│   └── Appearance.kt        Theme enums, Material color schemes, widget palettes
├── format/Format.kt         Pure time formatting ("2h 13m", "Thu 09:00", "4m")
├── widget/UsageWidget.kt    Glance widget, receiver, refresh action, responsive layouts
└── work/RefreshWorker.kt    CoroutineWorker + periodic / one-off scheduling helpers
```

## Data flow

```
 RefreshWorker (every 15 min)     RefreshAction (↻ tap)       MainActivity (onResume / button)
            │                            │ enqueues one-off work          │
            └──────────────┬─────────────┘                                │
                           ▼                                              ▼
                 UsageRepository.refresh()  ◄─────────────────────────────┘
                           │ 1. Store.tokens()  (decrypt)
                           │ 2. renew via ClaudeApi.refresh() if < 5 min to expiry
                           │ 3. ClaudeApi.fetchUsage()  (one retry after renew on 401/403)
                           │ 4. Store.saveSuccess(rawBody) / saveFailure(status, msg)
                           │ 5. UsageWidget().updateAll()
                           ▼
                    DataStore "claude_widget"
                           │  Store.state : Flow<WidgetState>
              ┌────────────┴────────────┐
              ▼                         ▼
     UsageWidget (Glance)        MainActivity (Compose)
```

Key points:

- **`UsageRepository.refresh()` is the only place that fetches data.** A process-wide `Mutex` serializes it, so a ↻ tap and a periodic run can't race each other or rotate the refresh token twice.
- **The raw JSON body is cached, not the parsed model.** `Store` re-parses it with `UsageParser` each time state is read. A parser fix therefore applies to the cached reading straight away, without waiting for the next fetch.
- **Failures keep the last good data.** `saveFailure` changes only the status and message. The UI shows the old numbers with an error marker.
- **`WidgetState`** combines everything the two UIs render: snapshot, `fetchedAt`, `Status`, message, `refreshing` and `Appearance`. `Status.SIGNED_OUT` is derived from whether an access token is stored.

## Refresh scheduling

| Trigger | Mechanism |
|---|---|
| Every 15 min | `RefreshWorker.schedulePeriodic()`: unique periodic work (`KEEP`), requires a network connection. Scheduled on sign-in, when the app resumes, and in `UsageWidgetReceiver.onEnabled`. |
| ↻ on the widget (or tapping the 1×1 tile) | `RefreshAction` → `RefreshWorker.refreshNow()`: unique one-off work (`KEEP`) |
| App opened | `MainActivity.onResume` calls `repo.refresh()` directly |
| Sign out | `RefreshWorker.cancelAll()` + `Store.signOut()` |

15 minutes is WorkManager's minimum for periodic work. Faster refreshes would need a foreground service or push messages, and the project deliberately has neither.

## Storage (`Store.kt`)

There is a single Preferences DataStore named `claude_widget`.

| Key | Contents |
|---|---|
| `access_token`, `refresh_token` | AES-256-GCM ciphertext (`Crypto`, Android Keystore alias `claude_widget_tokens`), Base64 of IV + ciphertext |
| `expires_at` | Epoch ms; 0 = unknown (a pasted token) |
| `pkce_verifier`, `pkce_state` | Pending sign-in, written when the browser is opened |
| `last_body`, `fetched_at` | Raw usage JSON + time of the last successful fetch |
| `status`, `message`, `refreshing` | Outcome of the last refresh |
| `color_style`, `brightness`, `opacity`, `bar_style` | Appearance; **kept** on sign-out |

`android:allowBackup="false"`: Keystore keys don't survive a restore, so a restored backup would contain ciphertext the app could no longer decrypt.

## Theming (`Appearance.kt`)

- `colorScheme(context, style, dark)` returns a Material 3 `ColorScheme`. The app screen uses it directly.
- `palettes(context, appearance)` returns a `(day, night)` pair of `Palette`s. The widget turns each colour into a Glance day/night `ColorProvider`, so in **Auto** brightness the launcher switches light and dark without a re-render.
- **Match phone** uses `dynamicLight/DarkColorScheme` (Android 12 and later) and falls back to the Claude palette on older versions.
- Background opacity is applied as alpha on the palette background.

## Widget

See [widget-layouts.md](widget-layouts.md). `UsageWidget` uses `SizeMode.Responsive` with four breakpoints. `provideGlance` collects `Store.state`, so the widget recomposes while its session is alive. `UsageRepository.redraw()` (`updateAll`) covers updates that arrive after that.
