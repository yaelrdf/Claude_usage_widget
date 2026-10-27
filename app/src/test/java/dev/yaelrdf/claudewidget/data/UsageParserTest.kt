package dev.yaelrdf.claudewidget.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class UsageParserTest {

    private fun fixture(name: String) =
        javaClass.classLoader!!.getResource(name)!!.readText()

    /** The fixture mirrors a real response's shape (both `limits[]` and flat keys) with made-up numbers. */
    @Test
    fun parsesSampleResponse() {
        val snapshot = UsageParser.parse(fixture("usage_sample.json"))
        val session = snapshot.session!!
        assertEquals(15, session.percent)
        assertNotNull(session.resetsAt)
        assertEquals(11, snapshot.weekly!!.percent)
        assertTrue(snapshot.extraWeekly.isEmpty())
    }

    @Test
    fun prefersLimitsArray() {
        val body = """
            {
              "five_hour": {"utilization": 1.0, "resets_at": "2026-10-19T09:49:59.871398+00:00"},
              "limits": [
                {"kind": "session", "group": "session", "percent": 42, "resets_at": "2026-10-19T09:49:59.871398+00:00"},
                {"kind": "weekly_all", "group": "weekly", "percent": 17.6, "resets_at": "2026-10-20T23:59:59+00:00"},
                {"kind": "weekly_opus", "group": "weekly", "percent": 5, "resets_at": null},
                {"kind": "something_new", "group": "other", "percent": 99}
              ]
            }
        """.trimIndent()
        val s = UsageParser.parse(body)
        assertEquals(42, s.session!!.percent)
        assertEquals(Instant.parse("2026-10-19T09:49:59.871398Z"), s.session!!.resetsAt)
        assertEquals(18, s.weekly!!.percent)
        assertEquals(listOf(UsageWindow("Opus weekly", 5, null)), s.extraWeekly)
    }

    @Test
    fun fallsBackToFlatKeys() {
        val body = """
            {
              "five_hour": {"utilization": 68.4, "resets_at": "2026-10-19T09:49:59+00:00", "extra": true},
              "seven_day": {"utilization": 21.0, "resets_at": "2026-10-20T23:59:59+00:00"},
              "seven_day_opus": null,
              "seven_day_sonnet": {"utilization": 3.0, "resets_at": null},
              "unknown_field": {"a": 1}
            }
        """.trimIndent()
        val s = UsageParser.parse(body)
        assertEquals(68, s.session!!.percent)
        assertEquals(21, s.weekly!!.percent)
        assertEquals(listOf(UsageWindow("Sonnet weekly", 3, null)), s.extraWeekly)
    }

    @Test
    fun emptyLimitsFallsBackToFlat() {
        val s = UsageParser.parse("""{"limits": [], "five_hour": {"utilization": 5}}""")
        assertEquals(5, s.session!!.percent)
        assertNull(s.weekly)
    }

    @Test
    fun clampsOutOfRange() {
        val s = UsageParser.parse("""{"five_hour": {"utilization": 130.0}, "seven_day": {"utilization": -2}}""")
        assertEquals(100, s.session!!.percent)
        assertEquals(0, s.weekly!!.percent)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonObject() {
        UsageParser.parse("[]")
    }
}
