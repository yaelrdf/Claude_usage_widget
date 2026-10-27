package dev.yaelrdf.claudewidget

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import dev.yaelrdf.claudewidget.data.Appearance
import dev.yaelrdf.claudewidget.data.BarStyle
import dev.yaelrdf.claudewidget.data.Brightness
import dev.yaelrdf.claudewidget.data.ColorStyle
import dev.yaelrdf.claudewidget.data.colorScheme
import dev.yaelrdf.claudewidget.data.dynamicColorAvailable
import dev.yaelrdf.claudewidget.data.palettes
import kotlin.math.roundToInt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import dev.yaelrdf.claudewidget.data.ApiResult
import dev.yaelrdf.claudewidget.data.ClaudeApi
import dev.yaelrdf.claudewidget.data.Status
import dev.yaelrdf.claudewidget.data.Tokens
import dev.yaelrdf.claudewidget.data.UsageRepository
import dev.yaelrdf.claudewidget.data.UsageWindow
import dev.yaelrdf.claudewidget.data.WidgetState
import dev.yaelrdf.claudewidget.format.Format
import dev.yaelrdf.claudewidget.work.RefreshWorker
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var repo: UsageRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        repo = UsageRepository(this)
        setContent {
            val state by repo.store.state.collectAsState(null)
            state?.let {
                MaterialTheme(colorScheme = appScheme(it.appearance)) {
                    Surface(modifier = Modifier.fillMaxSize()) { App(it) }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            if (repo.store.tokens() != null) {
                RefreshWorker.schedulePeriodic(this@MainActivity)
                repo.refresh()
            }
        }
    }

    @Composable
    private fun App(state: WidgetState) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Claude Usage", style = MaterialTheme.typography.headlineMedium)
            if (state.status == Status.SIGNED_OUT) SignIn() else Dashboard(state)
            AppearanceCard(state.appearance)
            Text(
                "Uses the same unofficial usage endpoint as Claude Code's /usage command. " +
                    "Anthropic may change it at any time. The widget refreshes about every 15 minutes, " +
                    "or tap ↻ on it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    @Composable
    private fun Dashboard(state: WidgetState) {
        val scope = rememberCoroutineScope()
        val now = System.currentTimeMillis()
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                val snapshot = state.snapshot
                if (snapshot == null || snapshot.isEmpty) {
                    Text(state.message ?: "Loading…")
                } else {
                    listOfNotNull(snapshot.session, snapshot.weekly).plus(snapshot.extraWeekly)
                        .forEach { WindowRow(it, now) }
                }
                val status = if (state.refreshing) "Refreshing…" else when (state.status) {
                    Status.OK -> "Updated ${Format.ago(state.fetchedAt, now)}"
                    else -> "${state.message ?: "Error"} · last update ${Format.ago(state.fetchedAt, now)}"
                }
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.status == Status.OK) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.error,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { scope.launch { repo.refresh() } }, enabled = !state.refreshing) {
                Text("Refresh now")
            }
            OutlinedButton(onClick = {
                scope.launch {
                    RefreshWorker.cancelAll(this@MainActivity)
                    repo.store.signOut()
                    repo.redraw()
                }
            }) { Text("Sign out") }
        }
        if (state.status == Status.AUTH_EXPIRED) {
            Text("Your sign-in expired.", color = MaterialTheme.colorScheme.error)
            SignIn()
        }
    }

    @Composable
    private fun WindowRow(window: UsageWindow, now: Long) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(window.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("${window.percent}%", style = MaterialTheme.typography.titleMedium)
            }
            LinearProgressIndicator(
                progress = { window.percent / 100f },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = when {
                    window.percent >= 80 -> Color(0xFFE5484D)
                    window.percent >= 50 -> Color(0xFFE8A33D)
                    else -> Color(0xFF4CB782)
                },
                trackColor = Color(0x33FFFFFF),
                drawStopIndicator = {},
            )
            window.resetsAt?.let {
                Text(
                    "Resets ${Format.resets(it, now)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SignIn() {
        val scope = rememberCoroutineScope()
        var code by remember { mutableStateOf("") }
        var manualToken by remember { mutableStateOf("") }
        var showManual by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        var busy by remember { mutableStateOf(false) }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("1. Sign in with your Claude account in the browser and approve access.")
                Button(onClick = {
                    scope.launch {
                        val pkce = ClaudeApi.newPkce()
                        repo.store.savePkce(pkce.verifier, pkce.state)
                        startActivity(Intent(Intent.ACTION_VIEW, pkce.authorizeUrl))
                    }
                }) { Text("Sign in with Claude") }

                Text("2. Copy the code shown after approving and paste it here.")
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it; error = null },
                    label = { Text("Authentication code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    enabled = code.isNotBlank() && !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            val pkce = repo.store.pkce()
                            if (pkce == null) {
                                error = "Tap “Sign in with Claude” first."
                            } else {
                                when (val r = ClaudeApi.exchangeCode(code, pkce.first, pkce.second)) {
                                    is ApiResult.Ok -> {
                                        repo.store.saveTokens(r.value)
                                        connected()
                                    }
                                    is ApiResult.AuthFailed -> error = "The code was rejected (HTTP ${r.code}). Sign in again."
                                    is ApiResult.Offline -> error = "No connection."
                                    is ApiResult.Failed -> error = r.message
                                }
                            }
                            busy = false
                        }
                    },
                ) { Text(if (busy) "Connecting…" else "Connect") }

                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                TextButton(onClick = { showManual = !showManual }) {
                    Text(if (showManual) "Hide access-token option" else "Use an access token instead")
                }
                if (showManual) {
                    Text(
                        "Paste an OAuth access token (sk-ant-oat…). It can't be renewed automatically, " +
                            "so you'll need to paste a new one when it expires.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = manualToken,
                        onValueChange = { manualToken = it },
                        label = { Text("Access token") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(enabled = manualToken.isNotBlank(), onClick = {
                        scope.launch {
                            repo.store.saveTokens(Tokens(manualToken.trim(), refreshToken = null, expiresAt = 0L))
                            connected()
                        }
                    }) { Text("Save token") }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }

    @Composable
    private fun appScheme(appearance: Appearance): ColorScheme {
        val dark = isDark(appearance.brightness)
        val scheme = colorScheme(this, appearance.colors, dark)
        return if (appearance.brightness == Brightness.BLACK) {
            scheme.copy(background = Color.Black, surface = Color.Black)
        } else scheme
    }

    @Composable
    private fun isDark(brightness: Brightness) = when (brightness) {
        Brightness.AUTO -> isSystemInDarkTheme()
        Brightness.LIGHT -> false
        Brightness.DARK, Brightness.BLACK -> true
    }

    @Composable
    private fun AppearanceCard(appearance: Appearance) {
        val scope = rememberCoroutineScope()
        fun save(new: Appearance) = scope.launch {
            repo.store.saveAppearance(new)
            repo.redraw()
        }
        // Local copy so the slider and preview follow the finger without a store write per frame.
        var opacity by remember(appearance.opacity) { mutableFloatStateOf(appearance.opacity.toFloat()) }
        val preview = appearance.copy(opacity = opacity.roundToInt())

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Widget appearance", style = MaterialTheme.typography.titleMedium)
                WidgetPreview(preview)

                Text("Colors", style = MaterialTheme.typography.labelLarge)
                Choice(ColorStyle.entries, appearance.colors, { it.label }) { save(appearance.copy(colors = it)) }
                if (appearance.colors == ColorStyle.PHONE && !dynamicColorAvailable) {
                    Text(
                        "Your Android version has no system colors to follow, so Claude colors are used.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text("Brightness", style = MaterialTheme.typography.labelLarge)
                Choice(Brightness.entries, appearance.brightness, { it.label }) { save(appearance.copy(brightness = it)) }

                Text("Bar colors", style = MaterialTheme.typography.labelLarge)
                Choice(BarStyle.entries, appearance.bars, { it.label }) { save(appearance.copy(bars = it)) }

                Text("Background opacity: ${opacity.roundToInt()}%", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = opacity,
                    onValueChange = { opacity = it },
                    onValueChangeFinished = { save(appearance.copy(opacity = opacity.roundToInt())) },
                    valueRange = 0f..100f,
                    steps = 19,
                )
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun <T> Choice(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, option ->
                SegmentedButton(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                    icon = {},
                ) { Text(label(option), maxLines = 1, style = MaterialTheme.typography.labelMedium) }
            }
        }
    }

    /** A mock 2×1 widget on a fake wallpaper, so changes are visible without leaving the app. */
    @Composable
    private fun WidgetPreview(appearance: Appearance) {
        val (day, night) = palettes(this, appearance)
        val p = if (isSystemInDarkTheme()) night else day
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF3A6073), Color(0xFF9D5C7B), Color(0xFFE2B56B))))
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(170.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(p.background)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    listOf(Triple("Session", 42, "resets 2h 13m"), Triple("Weekly", 83, "resets Thu 09:00"))
                        .forEach { (label, percent, resets) ->
                            Column {
                                Text(label, color = p.text, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp))
                                            .background(p.track)
                                    ) {
                                        Box(
                                            Modifier.fillMaxWidth(percent / 100f).height(4.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(p.bar(percent, appearance.bars))
                                        )
                                    }
                                    Text(
                                        "$percent%", color = p.text, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.End, modifier = Modifier.width(36.dp),
                                    )
                                }
                                Text(resets, color = p.muted, fontSize = 9.sp)
                            }
                        }
                }
                Text(
                    "4m ↻", color = p.text, fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
        }
    }

    private suspend fun connected() {
        RefreshWorker.schedulePeriodic(this)
        repo.refresh()
    }
}
