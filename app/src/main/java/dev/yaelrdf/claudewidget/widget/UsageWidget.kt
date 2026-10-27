package dev.yaelrdf.claudewidget.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.yaelrdf.claudewidget.MainActivity
import dev.yaelrdf.claudewidget.data.BarStyle
import dev.yaelrdf.claudewidget.data.Palette
import dev.yaelrdf.claudewidget.data.Status
import dev.yaelrdf.claudewidget.data.Store
import dev.yaelrdf.claudewidget.data.UsageWindow
import dev.yaelrdf.claudewidget.data.WidgetState
import dev.yaelrdf.claudewidget.data.palettes
import dev.yaelrdf.claudewidget.format.Format
import dev.yaelrdf.claudewidget.work.RefreshWorker

/** Breakpoints: Glance renders the largest one that fits the widget's current size. */
private val TINY = DpSize(40.dp, 40.dp)         // 1×1
private val SHORT = DpSize(100.dp, 40.dp)       // 2×1 on launchers with short rows
private val STACK = DpSize(100.dp, 80.dp)       // 2×1 and wider
private val STACK_TALL = DpSize(100.dp, 150.dp) // 2×2 and larger

/** Below this width the reset lines drop the "resets" prefix to leave room for the corner button. */
private val NARROW_WIDTH = 150.dp

class UsageWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(TINY, SHORT, STACK, STACK_TALL))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = Store(context.applicationContext)
        val initial = store.currentState()
        provideContent {
            val state by store.state.collectAsState(initial)
            Content(state)
        }
    }
}

class UsageWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UsageWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RefreshWorker.schedulePeriodic(context)
        RefreshWorker.refreshNow(context)
    }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        RefreshWorker.refreshNow(context)
    }
}

/** Day/night color providers for the current appearance. */
private class Colors(private val day: Palette, private val night: Palette, val bars: BarStyle) {
    private fun of(pick: (Palette) -> Color): ColorProvider = ColorProvider(day = pick(day), night = pick(night))

    val background = of { it.background }
    val text = of { it.text }
    val muted = of { it.muted }
    val track = of { it.track }
    val warn = of { it.high }
    fun bar(percent: Int) = of { it.bar(percent, bars) }
}

@Composable
private fun Content(state: WidgetState) {
    val (day, night) = palettes(LocalContext.current, state.appearance)
    val colors = Colors(day, night, state.appearance.bars)
    val size = LocalSize.current
    val now = System.currentTimeMillis()
    val signedOut = state.status == Status.SIGNED_OUT || state.snapshot == null
    val openApp = actionStartActivity<MainActivity>()
    val refresh = actionRunCallback<RefreshAction>()

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(colors.background)
            .cornerRadius(16.dp)
            // The 1×1 size has no room for a refresh button, so the whole tile refreshes.
            .clickable(if (size == TINY && !signedOut) refresh else openApp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            signedOut -> SignedOut(state, colors, compact = size.height < STACK.height)
            size == TINY -> Tiny(state, colors)
            size == SHORT -> Short(state, colors, refresh, now, narrow = size.width < NARROW_WIDTH)
            else -> Stacked(state, colors, refresh, now, narrow = size.width < NARROW_WIDTH, tall = size == STACK_TALL)
        }
    }
}

@Composable
private fun SignedOut(state: WidgetState, colors: Colors, compact: Boolean) {
    Text(
        when {
            state.status == Status.SIGNED_OUT -> if (compact) "Sign in" else "Tap to sign in"
            else -> state.message ?: "Loading…"
        },
        style = TextStyle(color = colors.text, fontSize = 12.sp, textAlign = TextAlign.Center),
        modifier = GlanceModifier.padding(6.dp),
    )
}

/** 1×1: big session percentage with the weekly one underneath. */
@Composable
private fun Tiny(state: WidgetState, colors: Colors) {
    val snapshot = state.snapshot!!
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = GlanceModifier.padding(4.dp)) {
        val session = snapshot.session
        Text(
            if (state.refreshing) "…" else session?.let { "${it.percent}%" } ?: "–",
            style = TextStyle(
                color = session?.let { colors.bar(it.percent) } ?: colors.text,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
        Text(
            snapshot.weekly?.let { "wk ${it.percent}%" } ?: "5h",
            style = TextStyle(color = if (state.status == Status.OK) colors.muted else colors.warn, fontSize = 10.sp),
            maxLines = 1,
        )
    }
}

/**
 * 2×1 and up: each limit is a label, a bar with its percentage, and the reset time underneath.
 * The refresh button and data age sit in the bottom-right corner on top of the content.
 */
@Composable
private fun Stacked(state: WidgetState, colors: Colors, refresh: Action, now: Long, narrow: Boolean, tall: Boolean) {
    val snapshot = state.snapshot!!
    val windows = listOfNotNull(snapshot.session, snapshot.weekly) + if (tall) snapshot.extraWeekly else emptyList()
    Box(
        modifier = GlanceModifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.BottomEnd,
    ) {
        Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            windows.forEachIndexed { i, window ->
                if (i > 0) Spacer(GlanceModifier.height(2.dp))
                Text(
                    window.label,
                    style = TextStyle(color = colors.text, fontSize = 9.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
                BarRow(window, colors)
                Text(
                    window.resetsAt?.let { resetText(it, now, narrow) } ?: "",
                    style = TextStyle(color = colors.muted, fontSize = 8.sp),
                    maxLines = 1,
                )
            }
        }
        val age = Format.agoShort(state.fetchedAt, now)
        CornerRefresh(state, colors, refresh, label = if (tall && !narrow) "Updated $age" else age)
    }
}

/** Rows too short for three lines per limit: the reset time moves up next to the label. */
@Composable
private fun Short(state: WidgetState, colors: Colors, refresh: Action, now: Long, narrow: Boolean) {
    val snapshot = state.snapshot!!
    val windows = listOfNotNull(snapshot.session, snapshot.weekly)
    Column(
        modifier = GlanceModifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        windows.forEachIndexed { i, window ->
            if (i > 0) Spacer(GlanceModifier.height(2.dp))
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    window.label,
                    style = TextStyle(color = colors.text, fontSize = 9.sp, fontWeight = FontWeight.Medium),
                    modifier = GlanceModifier.defaultWeight(),
                    maxLines = 1,
                )
                Text(
                    window.resetsAt?.let { resetText(it, now, narrow) } ?: "",
                    style = TextStyle(color = colors.muted, fontSize = 8.sp),
                    maxLines = 1,
                )
                Spacer(GlanceModifier.width(18.dp))
            }
            // The last bar row carries the refresh button; the others keep an empty slot so bars line up.
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = GlanceModifier.defaultWeight()) { BarRow(window, colors) }
                Box(modifier = GlanceModifier.width(18.dp), contentAlignment = Alignment.CenterEnd) {
                    if (i == windows.lastIndex) CornerRefresh(state, colors, refresh, label = null)
                }
            }
        }
    }
}

@Composable
private fun BarRow(window: UsageWindow, colors: Colors) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Bar(window.percent, colors, GlanceModifier.defaultWeight(), height = 4.dp)
        Text(
            "${window.percent}%",
            style = TextStyle(color = colors.text, fontSize = 9.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End),
            modifier = GlanceModifier.width(30.dp),
            maxLines = 1,
        )
    }
}

private fun resetText(at: java.time.Instant, now: Long, narrow: Boolean): String =
    (if (narrow) "" else "resets ") + Format.resets(at, now)

@Composable
private fun Bar(percent: Int, colors: Colors, modifier: GlanceModifier, height: Dp) {
    LinearProgressIndicator(
        progress = percent / 100f,
        modifier = modifier.height(height).cornerRadius(height / 2),
        color = colors.bar(percent),
        backgroundColor = colors.track,
    )
}

/** Small "4m ↻" button; the glyph becomes "…" while refreshing and a red "!" after a failure. */
@Composable
private fun CornerRefresh(state: WidgetState, colors: Colors, refresh: Action, label: String?) {
    val failed = state.status != Status.OK
    Row(
        modifier = GlanceModifier.height(16.dp).padding(start = 4.dp).clickable(refresh),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (label != null) {
            Text(
                label,
                style = TextStyle(color = if (failed) colors.warn else colors.muted, fontSize = 8.sp),
                maxLines = 1,
            )
            Spacer(GlanceModifier.width(3.dp))
        }
        Text(
            when {
                state.refreshing -> "…"
                failed -> "!"
                else -> "↻"
            },
            style = TextStyle(
                color = if (failed && !state.refreshing) colors.warn else colors.text,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}
