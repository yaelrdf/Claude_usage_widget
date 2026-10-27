package dev.yaelrdf.claudewidget.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.time.Instant
import java.time.OffsetDateTime

/** One rate-limit window, e.g. the 5-hour session or the 7-day weekly limit. */
data class UsageWindow(
    val label: String,
    val percent: Int,
    val resetsAt: Instant?,
)

data class UsageSnapshot(
    val session: UsageWindow?,
    val weekly: UsageWindow?,
    /** Model- or product-scoped weekly limits (Opus, Sonnet, …) that are currently reported. */
    val extraWeekly: List<UsageWindow>,
) {
    val isEmpty get() = session == null && weekly == null && extraWeekly.isEmpty()
}

/**
 * Parses the response of the unofficial `GET /api/oauth/usage` endpoint.
 *
 * The response has changed shape over time, so this accepts both the newer `limits[]` array
 * and the older flat `five_hour` / `seven_day*` objects, and ignores anything it doesn't know.
 */
object UsageParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String): UsageSnapshot {
        val root = json.parseToJsonElement(body) as? JsonObject
            ?: throw IllegalArgumentException("Usage response is not a JSON object")
        return parseLimitsArray(root) ?: parseFlat(root)
    }

    private fun parseLimitsArray(root: JsonObject): UsageSnapshot? {
        val limits = root["limits"] as? JsonArray ?: return null
        var session: UsageWindow? = null
        var weekly: UsageWindow? = null
        val extra = mutableListOf<UsageWindow>()
        for (item in limits) {
            val obj = item as? JsonObject ?: continue
            val kind = obj.string("kind") ?: continue
            val group = obj.string("group")
            val percent = obj.number("percent") ?: obj.number("utilization") ?: continue
            val resets = obj.string("resets_at")?.let(::parseInstant)
            when {
                kind == "session" || group == "session" ->
                    session = UsageWindow("Session", percent.toPercent(), resets)
                kind == "weekly_all" ->
                    weekly = UsageWindow("Weekly", percent.toPercent(), resets)
                group == "weekly" ->
                    extra += UsageWindow(weeklyLabel(kind.removePrefix("weekly_")), percent.toPercent(), resets)
            }
        }
        if (session == null && weekly == null && extra.isEmpty()) return null
        return UsageSnapshot(session, weekly, extra)
    }

    private fun parseFlat(root: JsonObject): UsageSnapshot {
        fun window(key: String, label: String): UsageWindow? {
            val obj = root[key] as? JsonObject ?: return null
            val percent = obj.number("utilization") ?: return null
            return UsageWindow(label, percent.toPercent(), obj.string("resets_at")?.let(::parseInstant))
        }
        val extra = listOfNotNull(
            window("seven_day_opus", "Opus weekly"),
            window("seven_day_sonnet", "Sonnet weekly"),
        )
        return UsageSnapshot(window("five_hour", "Session"), window("seven_day", "Weekly"), extra)
    }

    private fun weeklyLabel(scope: String): String =
        scope.split('_').joinToString(" ") { it.replaceFirstChar(Char::uppercase) } + " weekly"

    private fun Double.toPercent() = Math.round(this).toInt().coerceIn(0, 100)

    private fun parseInstant(s: String): Instant? =
        runCatching { OffsetDateTime.parse(s).toInstant() }.getOrNull()
            ?: runCatching { Instant.parse(s) }.getOrNull()

    private fun JsonObject.prim(key: String): JsonPrimitive? =
        this[key]?.takeUnless { it is JsonNull } as? JsonPrimitive

    private fun JsonObject.string(key: String): String? = prim(key)?.takeIf { it.isString }?.content

    private fun JsonObject.number(key: String): Double? = prim(key)?.doubleOrNull
}
