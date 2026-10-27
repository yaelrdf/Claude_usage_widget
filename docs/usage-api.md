# The usage API (unofficial)

Anthropic doesn't publish an API for Claude subscription usage. This app uses the endpoints **Claude Code** itself uses for `claude login` and `/usage`. We read the values out of the Claude Code CLI (v2.1.303, October 2026). **They can change or be blocked without notice.** All of them live in `app/src/main/java/dev/yaelrdf/claudewidget/data/ClaudeApi.kt`.

| Constant | Value |
|---|---|
| `CLIENT_ID` | `9d1c250a-e61b-44d9-88ed-5944d1962f5e` (Claude Code's public OAuth client) |
| `AUTHORIZE_URL` | `https://claude.com/cai/oauth/authorize` (redirects to `claude.ai/oauth/authorize`) |
| `TOKEN_URL` | `https://platform.claude.com/v1/oauth/token` |
| `REDIRECT_URL` | `https://platform.claude.com/oauth/code/callback` (the "manual" redirect that displays the code) |
| `USAGE_URL` | `https://api.anthropic.com/api/oauth/usage` |
| `SCOPES` | `user:profile user:inference` |

## Sign-in: OAuth 2.0 authorization code + PKCE

1. The app generates `code_verifier` and `state` (32 random bytes each, base64url), then saves them with `Store.savePkce`.
2. It opens the browser at the authorize URL with these parameters:
   `code=true`, `client_id`, `response_type=code`, `redirect_uri`, `scope`, `code_challenge` (S256), `code_challenge_method=S256` and `state`.
3. The user approves. The callback page shows a code in the form **`<code>#<state>`**, and the user pastes it into the app.
4. The app sends the code exchange as JSON (not form-encoded):
   ```json
   POST TOKEN_URL
   {"grant_type":"authorization_code","code":"…","redirect_uri":"…","client_id":"…","code_verifier":"…","state":"…"}
   ```
   It responds with `access_token`, `refresh_token`, `expires_in` (seconds) and `scope`.
5. To renew, the app sends this request:
   ```json
   POST TOKEN_URL
   {"grant_type":"refresh_token","refresh_token":"…","client_id":"…","scope":"user:profile user:inference"}
   ```
   The response may contain a **new** refresh token, because tokens rotate. Always store it, and keep the old one only if no new one is returned.

Errors are mapped in `ClaudeApi.call`. HTTP 401 and 403, and 400 with `invalid_grant`, become `ApiResult.AuthFailed`. `IOException` becomes `Offline`. Anything else becomes `Failed`.

> **Why not `claude setup-token`?** Claude Code creates those long-lived tokens with only the `user:inference` scope (its "inference-only" login). The usage endpoint is a profile-level call, so the app requests `user:profile` as well. If you paste a token by hand ("Use an access token instead"), it has to carry `user:profile`, and it can't be renewed.
>
> **Why not reuse the computer's `~/.claude/.credentials.json`?** Refresh tokens rotate. If two clients refreshed the same token pair, one of them would be signed out. The phone always gets its own pair.

## `GET /api/oauth/usage`

These headers are required:

```
Authorization: Bearer <access_token>
anthropic-beta: oauth-2025-04-20
```

The response is a JSON object with many fields, and most of them are `null` for most plans. Below is a trimmed example; the full shape is in `app/src/test/resources/usage_sample.json`.

```json
{
  "five_hour":  { "utilization": 15.0, "resets_at": "2026-10-19T09:49:59.672581+00:00" },
  "seven_day":  { "utilization": 11.0, "resets_at": "2026-10-20T23:59:59.672605+00:00" },
  "seven_day_opus": null,
  "seven_day_sonnet": null,
  "limits": [
    { "kind": "session",    "group": "session", "percent": 15, "severity": "normal", "resets_at": "…", "is_active": true },
    { "kind": "weekly_all", "group": "weekly",  "percent": 11, "severity": "normal", "resets_at": "…", "is_active": false }
  ],
  "extra_usage": { "is_enabled": false, … },
  "spend": { … },
  "seven_day_breakdown": { "rows": [ { "key": "claude_code", "display_name": "Claude Code", "percent": 50 }, … ] }
}
```

The response also contains many keys with codename-style names. They are usually `null`. Ignore them.

### How `UsageParser` reads it (`data/Usage.kt`)

1. **It prefers `limits[]`, the newer shape.**
   - `kind == "session"` or `group == "session"` becomes **Session**.
   - `kind == "weekly_all"` becomes **Weekly**.
   - Any other entry with `group == "weekly"` goes into `extraWeekly`, labelled from its kind (`weekly_opus` becomes "Opus weekly").
   - The percentage comes from `percent`, falling back to `utilization`.
2. **If `limits` is missing or empty, it falls back to the flat keys:** `five_hour`, `seven_day`, `seven_day_opus` and `seven_day_sonnet`, reading `utilization` from each.
3. **Unknown keys are ignored.** Percentages are rounded and clamped to 0–100. `resets_at` is parsed as `OffsetDateTime`, falling back to `Instant`. A missing or invalid time becomes `null`.

If the endpoint changes, add a fixture with the new shape under `app/src/test/resources/` and a test case to `UsageParserTest` **before** you change the parser.

### Checking the endpoint from a computer

If Claude Code is installed and signed in, the following prints the live response without ever printing the token:

```bash
TOKEN=$(jq -r .claudeAiOauth.accessToken ~/.claude/.credentials.json)
curl -s https://api.anthropic.com/api/oauth/usage \
  -H "Authorization: Bearer $TOKEN" -H "anthropic-beta: oauth-2025-04-20" | jq '{five_hour, seven_day, limits}'
```

Don't commit real responses. They contain your account's data. Trim them, and replace the numbers with made-up values first.
