# Contributing

Thanks for helping out! This is a small project, so the process is light.

## Before you start

- For anything bigger than a small fix, **open an issue first** so we can agree on the approach.
- Read [docs/architecture.md](docs/architecture.md). It's short, and it explains where things live.
- Remember that the usage endpoint is unofficial. If you're fixing breakage after an API change, include a trimmed sample of the new response with **made-up numbers** (see [docs/usage-api.md](docs/usage-api.md)).

## Development setup

See [docs/building.md](docs/building.md). In short, you need JDK 17 or later and the Android SDK with platform 37, then:

```bash
./gradlew testDebugUnitTest assembleDebug
```

## Making changes

1. Fork the repo and create a branch from `main`, for example `fix/parser-null-reset` or `feat/opus-row`.
2. Keep each pull request focused on one change.
3. Follow the existing code style:
   - Kotlin official style, 4-space indent, 120-column lines (`.editorconfig`).
   - Small, single-purpose files, with KDoc on anything non-obvious.
   - Dependencies go in `gradle/libs.versions.toml`.
4. **Tests:**
   - Logic in `data/` or `format/` needs a JVM unit test in `app/src/test/`.
   - The widget and app UI don't have automated UI tests. Describe in the PR how you tested on a device, and with which launcher and widget sizes.
5. **Widget layout changes:** update the Glance layout, the in-app preview (`MainActivity.WidgetPreview`) and the picker preview (`res/layout/widget_preview.xml`) together, plus [docs/widget-layouts.md](docs/widget-layouts.md). Screenshots in the PR help a lot.
6. Add an entry under **Unreleased** in [CHANGELOG.md](CHANGELOG.md) for anything users would notice.
7. Make sure CI passes. It runs the unit tests, lint and a debug build.

## Commit messages

Use the imperative mood and keep the summary short, e.g. `Fix crash when resets_at is null`. Explain the *why* in the body when it isn't obvious. [Conventional Commits](https://www.conventionalcommits.org/) prefixes (`fix:`, `feat:`, `docs:`) are welcome but not required.

## Security and privacy

- Never commit tokens, keystores, `local.properties` or raw account responses.
- If you find a vulnerability, follow [SECURITY.md](SECURITY.md) rather than opening a public issue.

## Code of conduct

This project follows the [Code of Conduct](CODE_OF_CONDUCT.md). By taking part, you agree to uphold it.

## License

By contributing, you agree that your contributions are licensed under the [MIT License](LICENSE).
