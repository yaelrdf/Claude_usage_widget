# Changelog

Notable changes are listed here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- Repository documentation (`docs/`), contributing guide, security policy, agent guide (`AGENTS.md` / `CLAUDE.md`).
- GitHub Actions: CI (unit tests, lint, debug build) and a tag-triggered release workflow.
- Release signing from environment variables, with a fallback to the debug key.

### Changed
- The test fixture is now a trimmed sample with made-up numbers.

## [1.2.0] - 2026-10-19

### Changed
- New, more compact stacked widget layout: a label, then the bar with its percentage, then the reset time, with a small "4m ↻" button in the corner.
- Short-row fallback layout for launchers whose 1-row widgets are under 80dp tall.

## [1.1.0] - 2026-10-19

### Added
- Responsive widget: minimum 1×1, default 2×1, larger layouts when resized.
- Appearance settings: colors (Match phone / Claude / Mono), brightness (Auto / Light / Dark / Black), bar colors, background opacity, and a live preview.
- The app screen follows the selected theme.

## [1.0.0] - 2026-10-19

### Added
- First release: a widget showing session (5h) and weekly usage from Claude Code's usage endpoint.
- On-device OAuth PKCE sign-in with automatic token refresh; tokens encrypted with an Android Keystore key.
- Refresh every 15 minutes, a manual ↻ button, and a refresh when the app opens.

[Unreleased]: ../../compare/v1.2.0...HEAD
[1.2.0]: ../../releases/tag/v1.2.0
[1.1.0]: ../../releases/tag/v1.1.0
[1.0.0]: ../../releases/tag/v1.0.0
