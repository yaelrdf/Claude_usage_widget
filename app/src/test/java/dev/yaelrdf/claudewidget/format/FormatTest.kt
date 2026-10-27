package dev.yaelrdf.claudewidget.format

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class FormatTest {
    private val now = Instant.parse("2026-10-19T08:00:00Z").toEpochMilli()

    @Test
    fun agoShort() {
        assertEquals("–", Format.agoShort(0, now))
        assertEquals("now", Format.agoShort(now - 20_000, now))
        assertEquals("4m", Format.agoShort(now - 4 * 60_000, now))
        assertEquals("2h", Format.agoShort(now - 125 * 60_000, now))
        assertEquals("3d", Format.agoShort(now - 3 * 24 * 3_600_000L, now))
    }

    @Test
    fun resets() {
        assertEquals("now", Format.resets(Instant.parse("2026-10-19T07:59:00Z"), now))
        assertEquals("1m", Format.resets(Instant.parse("2026-10-19T08:00:20Z"), now))
        assertEquals("2h 13m", Format.resets(Instant.parse("2026-10-19T10:13:00Z"), now))
        // More than a day away shows weekday and local time.
        val far = Format.resets(Instant.parse("2026-10-22T09:00:00Z"), now, ZoneOffset.UTC)
        assertEquals(true, far.endsWith("09:00"))
    }
}
