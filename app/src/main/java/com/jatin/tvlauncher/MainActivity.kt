package com.jatin.tvlauncher

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

data class AppItem(
    val label: String,
    val intent: Intent,
    val banner: ImageBitmap?,
    val icon: ImageBitmap?,
)

class MainActivity : ComponentActivity() {
    private var apps by mutableStateOf<List<AppItem>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Launcher(apps) { startSafe(it) } }
    }

    override fun onResume() {
        super.onResume()
        apps = loadApps()
    }

    private fun startSafe(i: Intent) {
        try { startActivity(i) } catch (_: Exception) {}
    }

    private fun loadApps(): List<AppItem> {
        val pm = packageManager
        val seen = HashSet<String>()
        val out = ArrayList<AppItem>()
        for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val q = Intent(Intent.ACTION_MAIN).addCategory(cat)
            for (ri in pm.queryIntentActivities(q, 0)) {
                val pkg = ri.activityInfo.packageName
                if (pkg == packageName || !seen.add(pkg)) continue
                val launch = pm.getLaunchIntentForPackage(pkg) ?: continue
                val banner = try { ri.activityInfo.loadBanner(pm)?.toBitmap(320, 180)?.asImageBitmap() } catch (_: Exception) { null }
                val icon = try { ri.loadIcon(pm).toBitmap(128, 128).asImageBitmap() } catch (_: Exception) { null }
                out.add(AppItem(ri.loadLabel(pm).toString(), launch, banner, icon))
            }
        }
        out.sortBy { it.label.lowercase() }
        val settings = Intent(Settings.ACTION_SETTINGS)
        out.add(AppItem("Settings", settings, null, null))
        return out
    }
}

private val Ink = Color(0xFF0A0E1A)
private val Dusk = Color(0xFF1B2A4A)
private val Plum = Color(0xFF3A1F4D)
private val Teal = Color(0xFF0F3D4A)

@Composable
fun Launcher(apps: List<AppItem>, onOpen: (Intent) -> Unit) {
    BackHandler { }
    var focusedLabel by remember { mutableStateOf("") }
    val t = rememberInfiniteTransition(label = "bg")
    val shift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(18000, easing = LinearEasing), RepeatMode.Reverse), label = "s")
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(1000) } }
    val hour = Calendar.getInstance().apply { time = now }.get(Calendar.HOUR_OF_DAY)
    val greeting = when { hour < 5 -> "Good night"; hour < 12 -> "Good morning"; hour < 17 -> "Good afternoon"; else -> "Good evening" }

    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(listOf(lerp(Ink, Dusk, shift), lerp(Plum, Teal, shift), Ink))
        )
    ) {
        Column(Modifier.fillMaxSize().padding(start = 64.dp, top = 48.dp)) {
            Text(SimpleDateFormat("h:mm", Locale.getDefault()).format(now), 96, FontWeight.Light)
            Text(SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now), 22, FontWeight.Normal, Color(0xCCFFFFFF))
            Spacer(Modifier.height(12.dp))
            Text(greeting, 22, FontWeight.Normal, Color(0x99FFFFFF))
            Spacer(Modifier.weight(1f))
            Text(focusedLabel, 40, FontWeight.SemiBold, modifier = Modifier.padding(bottom = 20.dp))
            val first = remember { FocusRequester() }
            LaunchedEffect(apps.isNotEmpty()) { if (apps.isNotEmpty()) try { first.requestFocus() } catch (_: Exception) {} }
            LazyRow(
                contentPadding = PaddingValues(end = 64.dp, top = 20.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.padding(bottom = 56.dp)
            ) {
                itemsIndexed(apps) { i, app ->
                    AppCard(app, if (i == 0) Modifier.focusRequester(first) else Modifier,
                        onFocus = { focusedLabel = app.label }, onClick = { onOpen(app.intent) })
                }
            }
        }
    }
}

@Composable
private fun Text(
    s: String, size: Int, weight: FontWeight, color: Color = Color.White,
    modifier: Modifier = Modifier,
) = androidx.compose.foundation.text.BasicText(
    s, modifier,
    style = androidx.compose.ui.text.TextStyle(color = color, fontSize = size.sp, fontWeight = weight, fontFamily = FontFamily.SansSerif)
)

@Composable
private fun AppCard(app: AppItem, modifier: Modifier, onFocus: () -> Unit, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.14f else 1f, spring(stiffness = 400f), label = "scale")
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .size(208.dp, 117.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(if (focused) 28.dp else 0.dp, shape)
            .clip(shape)
            .background(Color(0x26FFFFFF))
            .border(if (focused) 3.dp else 0.dp, Color.White, shape)
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        when {
            app.banner != null -> Image(app.banner, app.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            app.icon != null -> Image(app.icon, app.label, Modifier.size(64.dp))
            else -> Text(app.label, 20, FontWeight.Medium)
        }
    }
}
