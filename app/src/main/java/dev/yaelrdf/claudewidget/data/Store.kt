package dev.yaelrdf.claudewidget.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.dataStore by preferencesDataStore(name = "claude_widget")

data class Tokens(
    val accessToken: String,
    /** Null when the user pasted a bare access token instead of signing in. */
    val refreshToken: String?,
    /** Epoch millis; 0 when unknown. */
    val expiresAt: Long,
)

enum class Status { OK, AUTH_EXPIRED, OFFLINE, ERROR, SIGNED_OUT }

/** What the widget and the app render. */
data class WidgetState(
    val snapshot: UsageSnapshot?,
    val fetchedAt: Long,
    val status: Status,
    val message: String?,
    val refreshing: Boolean,
    val appearance: Appearance,
)

/** Persists tokens (encrypted with an Android Keystore key) and the last fetched usage. */
class Store(private val context: Context) {

    private object Keys {
        val accessToken = stringPreferencesKey("access_token")
        val refreshToken = stringPreferencesKey("refresh_token")
        val expiresAt = longPreferencesKey("expires_at")
        val pkceVerifier = stringPreferencesKey("pkce_verifier")
        val pkceState = stringPreferencesKey("pkce_state")
        val lastBody = stringPreferencesKey("last_body")
        val fetchedAt = longPreferencesKey("fetched_at")
        val status = stringPreferencesKey("status")
        val message = stringPreferencesKey("message")
        val refreshing = booleanPreferencesKey("refreshing")
        val colorStyle = stringPreferencesKey("color_style")
        val brightness = stringPreferencesKey("brightness")
        val opacity = intPreferencesKey("opacity")
        val barStyle = stringPreferencesKey("bar_style")
    }

    private val appearanceKeys = setOf(Keys.colorStyle, Keys.brightness, Keys.opacity, Keys.barStyle)

    val state: Flow<WidgetState> = context.dataStore.data.map { it.toWidgetState() }

    suspend fun currentState(): WidgetState = state.first()

    suspend fun tokens(): Tokens? {
        val prefs = context.dataStore.data.first()
        val access = prefs[Keys.accessToken]?.let(Crypto::decrypt) ?: return null
        return Tokens(
            accessToken = access,
            refreshToken = prefs[Keys.refreshToken]?.let(Crypto::decrypt),
            expiresAt = prefs[Keys.expiresAt] ?: 0L,
        )
    }

    suspend fun saveTokens(tokens: Tokens) {
        context.dataStore.edit {
            it[Keys.accessToken] = Crypto.encrypt(tokens.accessToken)
            if (tokens.refreshToken != null) {
                it[Keys.refreshToken] = Crypto.encrypt(tokens.refreshToken)
            } else {
                it.remove(Keys.refreshToken)
            }
            it[Keys.expiresAt] = tokens.expiresAt
        }
    }

    suspend fun savePkce(verifier: String, state: String) {
        context.dataStore.edit {
            it[Keys.pkceVerifier] = verifier
            it[Keys.pkceState] = state
        }
    }

    suspend fun pkce(): Pair<String, String>? {
        val prefs = context.dataStore.data.first()
        val verifier = prefs[Keys.pkceVerifier] ?: return null
        val state = prefs[Keys.pkceState] ?: return null
        return verifier to state
    }

    /** Clears tokens and usage but keeps the appearance settings. */
    suspend fun signOut() {
        context.dataStore.edit { prefs ->
            prefs.asMap().keys.filter { it !in appearanceKeys }.forEach { prefs.remove(it) }
        }
    }

    suspend fun saveAppearance(appearance: Appearance) {
        context.dataStore.edit {
            it[Keys.colorStyle] = appearance.colors.name
            it[Keys.brightness] = appearance.brightness.name
            it[Keys.opacity] = appearance.opacity
            it[Keys.barStyle] = appearance.bars.name
        }
    }

    suspend fun setRefreshing(refreshing: Boolean) {
        context.dataStore.edit { it[Keys.refreshing] = refreshing }
    }

    suspend fun saveSuccess(body: String, fetchedAt: Long) {
        context.dataStore.edit {
            it[Keys.lastBody] = body
            it[Keys.fetchedAt] = fetchedAt
            it[Keys.status] = Status.OK.name
            it.remove(Keys.message)
            it[Keys.refreshing] = false
        }
    }

    /** Records a failed refresh but keeps the last good numbers on screen. */
    suspend fun saveFailure(status: Status, message: String?) {
        context.dataStore.edit {
            it[Keys.status] = status.name
            if (message != null) it[Keys.message] = message else it.remove(Keys.message)
            it[Keys.refreshing] = false
        }
    }

    private fun Preferences.toWidgetState(): WidgetState {
        val snapshot = this[Keys.lastBody]?.let { runCatching { UsageParser.parse(it) }.getOrNull() }
        val status = when {
            this[Keys.accessToken] == null -> Status.SIGNED_OUT
            else -> this[Keys.status]?.let { runCatching { Status.valueOf(it) }.getOrNull() } ?: Status.OK
        }
        return WidgetState(
            snapshot = snapshot,
            fetchedAt = this[Keys.fetchedAt] ?: 0L,
            status = status,
            message = this[Keys.message],
            refreshing = this[Keys.refreshing] ?: false,
            appearance = toAppearance(),
        )
    }

    private fun Preferences.toAppearance(): Appearance {
        val defaults = Appearance()
        return Appearance(
            colors = enumOr(this[Keys.colorStyle], defaults.colors),
            brightness = enumOr(this[Keys.brightness], defaults.brightness),
            opacity = this[Keys.opacity] ?: defaults.opacity,
            bars = enumOr(this[Keys.barStyle], defaults.bars),
        )
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: default
}

private object Crypto {
    private const val KEY_ALIAS = "claude_widget_tokens"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.iv + cipher.doFinal(plain.toByteArray())
        return Base64.encodeToString(sealed, Base64.NO_WRAP)
    }

    fun decrypt(encoded: String): String? = runCatching {
        val sealed = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, sealed, 0, 12))
        String(cipher.doFinal(sealed, 12, sealed.size - 12))
    }.getOrNull()
}
