package dev.yaelrdf.claudewidget.data

import android.content.Context
import androidx.glance.appwidget.updateAll
import dev.yaelrdf.claudewidget.widget.UsageWidget
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class UsageRepository(private val context: Context) {
    val store = Store(context.applicationContext)

    /** Refreshes the access token if needed, fetches usage, stores the result and redraws widgets. */
    suspend fun refresh(): Status = lock.withLock {
        val tokens = store.tokens()
        if (tokens == null) {
            redraw()
            return Status.SIGNED_OUT
        }
        store.setRefreshing(true)
        redraw()

        val status = fetchWithTokens(tokens)
        redraw()
        status
    }

    private suspend fun fetchWithTokens(initial: Tokens): Status {
        var tokens = initial
        val nearExpiry = tokens.expiresAt != 0L && tokens.expiresAt - System.currentTimeMillis() < 5 * 60_000
        if (nearExpiry) {
            tokens = renew(tokens) ?: return store.currentState().status
        }

        var result = ClaudeApi.fetchUsage(tokens.accessToken)
        if (result is ApiResult.AuthFailed && !nearExpiry) {
            // The access token may have been revoked early; try one refresh before giving up.
            val renewed = renew(tokens)
            if (renewed != null) result = ClaudeApi.fetchUsage(renewed.accessToken)
        }
        return when (result) {
            is ApiResult.Ok -> {
                runCatching { UsageParser.parse(result.value) }.fold(
                    onSuccess = {
                        store.saveSuccess(result.value, System.currentTimeMillis())
                        Status.OK
                    },
                    onFailure = { fail(Status.ERROR, "Couldn't read usage: ${it.message}") },
                )
            }
            is ApiResult.AuthFailed -> fail(Status.AUTH_EXPIRED, "Sign in again")
            is ApiResult.Offline -> fail(Status.OFFLINE, "Offline")
            is ApiResult.Failed -> fail(Status.ERROR, result.message)
        }
    }

    /** Returns fresh tokens, or null (after recording AUTH_EXPIRED) when they can't be renewed. */
    private suspend fun renew(tokens: Tokens): Tokens? {
        val refreshToken = tokens.refreshToken ?: run {
            fail(Status.AUTH_EXPIRED, "Token expired — sign in again")
            return null
        }
        return when (val result = ClaudeApi.refresh(refreshToken)) {
            is ApiResult.Ok -> result.value.also { store.saveTokens(it) }
            is ApiResult.AuthFailed -> { fail(Status.AUTH_EXPIRED, "Sign in again"); null }
            is ApiResult.Offline -> { fail(Status.OFFLINE, "Offline"); null }
            is ApiResult.Failed -> { fail(Status.ERROR, "Token refresh failed: ${result.message}"); null }
        }
    }

    private suspend fun fail(status: Status, message: String): Status {
        store.saveFailure(status, message)
        return status
    }

    suspend fun redraw() {
        UsageWidget().updateAll(context)
    }

    private companion object {
        val lock = Mutex()
    }
}
