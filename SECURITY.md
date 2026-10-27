# Security policy

## Reporting a vulnerability

Please **don't open a public issue** for security problems. Report them privately through GitHub's [private vulnerability reporting](../../security/advisories/new), using the **Security → Report a vulnerability** tab.

Include what you found, how to reproduce it and what the impact is. You should get a reply within a week.

## Scope

In scope:

- Token handling: storage (Keystore + DataStore), transport and accidental exposure (logs, backups, intents).
- The OAuth PKCE flow implemented in `data/ClaudeApi.kt`.
- Anything that could leak a user's Claude account data off the device.

Out of scope:

- The behaviour of Anthropic's own endpoints. Report those to Anthropic.
- Problems that need a rooted or compromised device.

## Design notes

- Tokens are encrypted with AES-256-GCM using a non-exportable Android Keystore key.
- `android:allowBackup="false"`.
- The app only contacts `claude.com`, `platform.claude.com` and `api.anthropic.com`, all over HTTPS.
- There is no logging of tokens or response bodies, and no analytics or third-party SDKs.

## Supported versions

Only the latest release gets security fixes.
