package dev.yaelrdf.claudewidget.data

import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.buildJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>
    data class AuthFailed(val code: Int) : ApiResult<Nothing>
    data class Offline(val error: IOException) : ApiResult<Nothing>
    data class Failed(val message: String) : ApiResult<Nothing>
}

/**
 * Talks to the same (unofficial) endpoints Claude Code uses for sign-in and `/usage`.
 * Values were taken from Claude Code 2.1.303 and may change without notice.
 */
object ClaudeApi {
    private const val CLIENT_ID = "9d1c250a-e61b-44d9-88ed-5944d1962f5e"
    private const val AUTHORIZE_URL = "https://claude.com/cai/oauth/authorize"
    private const val TOKEN_URL = "https://platform.claude.com/v1/oauth/token"
    private const val REDIRECT_URL = "https://platform.claude.com/oauth/code/callback"
    private const val USAGE_URL = "https://api.anthropic.com/api/oauth/usage"
    private const val SCOPES = "user:profile user:inference"

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonType = "application/json".toMediaType()

    data class Pkce(val verifier: String, val state: String, val authorizeUrl: Uri)

    fun newPkce(): Pkce {
        val verifier = randomUrlSafe(32)
        val state = randomUrlSafe(32)
        val challenge = base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        val url = Uri.parse(AUTHORIZE_URL).buildUpon()
            .appendQueryParameter("code", "true")
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URL)
            .appendQueryParameter("scope", SCOPES)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", state)
            .build()
        return Pkce(verifier, state, url)
    }

    /** [pasted] is what the callback page shows: `code#state`. */
    suspend fun exchangeCode(pasted: String, verifier: String, expectedState: String): ApiResult<Tokens> {
        val parts = pasted.trim().split('#', limit = 2)
        val code = parts[0]
        val state = parts.getOrNull(1) ?: expectedState
        if (state != expectedState) return ApiResult.Failed("That code is from a different sign-in attempt. Tap Sign in again.")
        val body = buildJsonObject {
            put("grant_type", "authorization_code")
            put("code", code)
            put("redirect_uri", REDIRECT_URL)
            put("client_id", CLIENT_ID)
            put("code_verifier", verifier)
            put("state", state)
        }
        return postToken(body.toString(), previousRefresh = null)
    }

    suspend fun refresh(refreshToken: String): ApiResult<Tokens> {
        val body = buildJsonObject {
            put("grant_type", "refresh_token")
            put("refresh_token", refreshToken)
            put("client_id", CLIENT_ID)
            put("scope", SCOPES)
        }
        return postToken(body.toString(), previousRefresh = refreshToken)
    }

    /** Returns the raw JSON body so it can be cached and re-parsed later. */
    suspend fun fetchUsage(accessToken: String): ApiResult<String> = call(
        Request.Builder()
            .url(USAGE_URL)
            .header("Authorization", "Bearer $accessToken")
            .header("anthropic-beta", "oauth-2025-04-20")
            .header("Accept", "application/json")
            .build()
    ) { it }

    private suspend fun postToken(body: String, previousRefresh: String?): ApiResult<Tokens> = call(
        Request.Builder()
            .url(TOKEN_URL)
            .post(body.toRequestBody(jsonType))
            .build()
    ) { text ->
        val obj = json.parseToJsonElement(text) as JsonObject
        val expiresIn = obj["expires_in"]?.jsonPrimitive?.longOrNull ?: 0L
        Tokens(
            accessToken = obj.getValue("access_token").jsonPrimitive.content,
            refreshToken = obj["refresh_token"]?.jsonPrimitive?.content ?: previousRefresh,
            expiresAt = if (expiresIn > 0) System.currentTimeMillis() + expiresIn * 1000 else 0L,
        )
    }

    private suspend fun <T> call(request: Request, parse: (String) -> T): ApiResult<T> =
        withContext(Dispatchers.IO) {
            try {
                http.newCall(request).execute().use { response ->
                    val text = response.body.string()
                    when {
                        response.code == 401 || response.code == 403 -> ApiResult.AuthFailed(response.code)
                        // The token endpoint answers 400 invalid_grant for a dead refresh token.
                        response.code == 400 && text.contains("invalid_grant") -> ApiResult.AuthFailed(400)
                        !response.isSuccessful -> ApiResult.Failed("HTTP ${response.code}")
                        else -> runCatching { ApiResult.Ok(parse(text)) }
                            .getOrElse { ApiResult.Failed("Unexpected response: ${it.message}") }
                    }
                }
            } catch (e: IOException) {
                ApiResult.Offline(e)
            }
        }

    private fun randomUrlSafe(bytes: Int): String =
        base64Url(ByteArray(bytes).also { SecureRandom().nextBytes(it) })

    private fun base64Url(data: ByteArray): String =
        Base64.encodeToString(data, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}
