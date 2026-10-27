package dev.yaelrdf.claudewidget.format

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Format {
    private val dayTime = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.getDefault())

    /** "2h 13m" when the reset is within a day, otherwise "Thu 09:00" in local time. */
    fun resets(at: Instant, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val left = Duration.between(Instant.ofEpochMilli(nowMillis), at)
        return when {
            left.isNegative || left.isZero -> "now"
            left.toHours() < 24 -> {
                val h = left.toHours()
                val m = left.toMinutes() % 60
                if (h == 0L) "${m.coerceAtLeast(1)}m" else "${h}h ${m}m"
            }
            else -> dayTime.format(at.atZone(zone))
        }
    }

    fun ago(thenMillis: Long, nowMillis: Long): String {
        if (thenMillis <= 0L) return "never"
        val minutes = (nowMillis - thenMillis) / 60_000
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "${minutes}m ago"
            minutes < 24 * 60 -> "${minutes / 60}h ago"
            else -> "${minutes / (24 * 60)}d ago"
        }
    }

    /** Compact form of [ago] for the widget corner: "now", "4m", "2h", "3d". */
    fun agoShort(thenMillis: Long, nowMillis: Long): String {
        if (thenMillis <= 0L) return "–"
        val minutes = (nowMillis - thenMillis) / 60_000
        return when {
            minutes < 1 -> "now"
            minutes < 60 -> "${minutes}m"
            minutes < 24 * 60 -> "${minutes / 60}h"
            else -> "${minutes / (24 * 60)}d"
        }
    }
}
