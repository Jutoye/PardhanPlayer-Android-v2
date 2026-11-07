package com.pardhancodes.pardhanplayer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import android.app.Activity
import android.media.AudioManager
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.pardhancodes.pardhanplayer.service.AudioFx
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

val CardBg = Color(0xFF0B0D14)
val Blue = Color(0xFF2563EB)
val Purple = Color(0xFF8B5CF6)

// ---------------- Home ----------------
@Composable
fun HomeScreen(
    lastTitle: String?, lastPos: Long,
    onResume: () -> Unit, onScan: () -> Unit,
    onFolders: () -> Unit, onNetwork: () -> Unit, onVault: () -> Unit, onEq: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF1B2A5C), Color(0xFF05060B))))
                .padding(24.dp)
        ) {
            Column {
                Text("Featured · Pardhan Player", color = Blue, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    lastTitle ?: "Welcome to Pardhan Player",
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Hardware-accelerated playback, 200% audio pre-amp boost, 5-band equalizer, " +
                        "network streams and a private vault.",
                    color = Color(0xFFB8BCC8)
                )
                Spacer(Modifier.height(16.dp))
                if (lastTitle != null) {
                    Button(onClick = onResume, shape = RoundedCornerShape(20.dp)) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Resume ${fmtDur(lastPos)}")
                    }
                    Spacer(Modifier.height(8.dp))
                }
                FilledTonalButton(onClick = onScan, shape = RoundedCornerShape(20.dp)) {
                    Icon(Icons.Default.FolderOpen, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Scan Device Storage")
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(Icons.Default.FolderOpen, Blue, "Scan Folders", "Internal & SD", Modifier.weight(1f), onFolders)
            Tile(Icons.Default.Sensors, Purple, "Network Streams", "HLS · M3U8 · URL", Modifier.weight(1f), onNetwork)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(Icons.Default.Lock, Blue, "Private Vault", "PIN & Biometrics", Modifier.weight(1f), onVault)
            Tile(Icons.Default.Equalizer, Purple, "Equalizer", "5-band DSP", Modifier.weight(1f), onEq)
        }
    }
}

@Composable
fun Tile(icon: ImageVector, tint: Color, title: String, sub: String, modifier: Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = tint) }
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = Color(0xFF9CA3AF), maxLines = 1)
        }
    }
}

// ---------------- Media lists ----------------
@Composable
fun SearchableMediaList(list: List<MediaEntry>, hint: String, onPlay: (List<MediaEntry>, Int) -> Unit) {
    var q by remember { mutableStateOf("") }
    val shown = if (q.isBlank()) list else list.filter { it.title.contains(q, ignoreCase = true) }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = q, onValueChange = { q = it }, singleLine = true,
            placeholder = { Text(hint) },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp)
        )
        MediaList(shown, Modifier.weight(1f), onPlay)
    }
}

@Composable
fun MediaList(list: List<MediaEntry>, modifier: Modifier = Modifier, onPlay: (List<MediaEntry>, Int) -> Unit) {
    if (list.isEmpty()) {
        Text("Koi file nahi mili. Settings mein Grant Access dabayein.", Modifier.padding(16.dp)); return
    }
    LazyColumn(modifier.fillMaxWidth()) {
        items(list.size) { i ->
            val e = list[i]
            Row(
                Modifier.fillMaxWidth().clickable { onPlay(list, i) }.padding(16.dp, 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(CardBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (e.isVideo) Icons.Default.VideoLibrary else Icons.Default.MusicNote,
                        null, tint = if (e.isVideo) Blue else Purple
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.title, maxLines = 2)
                    Text(
                        "${fmtDur(e.durationMs)} · ${fmtSize(e.size)}",
                        style = MaterialTheme.typography.labelSmall, color = Color(0xFF9CA3AF)
                    )
                }
            }
            HorizontalDivider(color = Color(0xFF16181F))
        }
    }
}

// ---------------- Folders ----------------
@Composable
fun FoldersScreen(all: List<MediaEntry>, onPlay: (List<MediaEntry>, Int) -> Unit) {
    var storage by remember { mutableIntStateOf(0) }
    var folder by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    fun isInternal(p: String) = p.startsWith("/storage/emulated") || p.startsWith("/sdcard")
    val internal = all.filter { isInternal(it.path) }
    val sd = all.filter { !isInternal(it.path) }
    val current = if (storage == 0) internal else sd
    val openFolder = folder
    BackHandler(enabled = openFolder != null) { folder = null }

    Column(Modifier.fillMaxSize()) {
        Text("Storage & File Manager", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp, 8.dp))
        StorageCard("Internal Storage", internal, storage == 0) { storage = 0; folder = null }
        StorageCard("SD Card", sd, storage == 1) { storage = 1; folder = null }
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text("Search inside ${if (storage == 0) "Internal Storage" else "SD Card"}...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp)
        )
        if (openFolder == null) {
            val groups = current.groupBy { it.folder }
                .filter { query.isBlank() || it.key.substringAfterLast('/').contains(query, true) }
                .toList().sortedBy { it.first.substringAfterLast('/').lowercase() }
            LazyColumn(Modifier.weight(1f)) {
                items(groups.size) { i ->
                    val (path, files) = groups[i]
                    Row(
                        Modifier.fillMaxWidth().clickable { folder = path }.padding(16.dp, 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Folder, null, tint = Blue)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(path.substringAfterLast('/').ifBlank { "/" }, maxLines = 1)
                            Text(
                                "${files.size} files · ${fmtSize(files.sumOf { it.size })}",
                                style = MaterialTheme.typography.labelSmall, color = Color(0xFF9CA3AF)
                            )
                        }
                    }
                    HorizontalDivider(color = Color(0xFF16181F))
                }
            }
        } else {
            Text(
                "‹  ${openFolder.substringAfterLast('/')}",
                color = Blue,
                modifier = Modifier.clickable { folder = null }.padding(16.dp, 8.dp)
            )
            val files = current.filter { it.folder == openFolder && (query.isBlank() || it.title.contains(query, true)) }
            MediaList(files, Modifier.weight(1f), onPlay)
        }
    }
}

@Composable
private fun StorageCard(title: String, list: List<MediaEntry>, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp).clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = if (selected) BorderStroke(1.dp, Blue) else null,
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(if (selected) Blue else Color(0xFF16181F)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Storage, null) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${list.size} media files · ${fmtSize(list.sumOf { it.size })} indexed",
                    style = MaterialTheme.typography.labelSmall, color = Color(0xFF9CA3AF)
                )
            }
            Text("Browse", color = Blue)
        }
    }
}

// ---------------- Settings ----------------
@Composable
fun SettingsScreen(
    onGrant: () -> Unit, onPin: () -> Unit, onBackup: () -> Unit,
    onRestore: () -> Unit, onClear: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "Aap ki media sirf aap ke device par rehti hai. Koi file ya watch history kisi server par upload nahi hoti.",
            color = Color(0xFF9CA3AF)
        )
        Spacer(Modifier.height(16.dp))
        SettingButton(Icons.Default.Storage, Blue, "Grant Folder Access & Scan", onGrant)
        SettingButton(Icons.Default.Lock, Purple, "Configure Vault PIN & Biometrics", onPin)
        SettingButton(Icons.Default.Download, Blue, "Backup Settings (.JSON)", onBackup)
        SettingButton(Icons.Default.Upload, Purple, "Restore Backup", onRestore)
        SettingButton(Icons.Default.Refresh, Color(0xFFF87171), "Clear Watch History", onClear, Color(0xFFF87171))
        Spacer(Modifier.height(20.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBg),
            shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFF070A18)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.PlayArrow, null, tint = Purple, modifier = Modifier.size(40.dp)) }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Pardhan Player", style = MaterialTheme.typography.titleMedium)
                    Text("Developed by PardhanCode")
                    Text(
                        "com.pardhancodes.pardhanplayer · Release 1.0.0",
                        style = MaterialTheme.typography.labelSmall, color = Color(0xFF9CA3AF)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("POWERED BY PARDHANCODE", color = Blue, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun SettingButton(icon: ImageVector, tint: Color, text: String, onClick: () -> Unit, textColor: Color = Color.White) {
    FilledTonalButton(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = CardBg),
        modifier = Modifier.padding(bottom = 10.dp)
    ) {
        Icon(icon, null, tint = tint)
        Spacer(Modifier.width(10.dp))
        Text(text, color = textColor)
    }
}

// ---------------- Equalizer ----------------
@Composable
fun EqualizerScreen(prefs: Prefs, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val eq = AudioFx.equalizer
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Text("Equalizer", style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(8.dp))
        if (eq == null) {
            Text("Pehle koi video ya audio chalayein, phir Equalizer kaam karega.")
        } else {
            val min = eq.bandLevelRange[0].toFloat()
            val max = eq.bandLevelRange[1].toFloat()
            for (b in 0 until eq.numberOfBands) {
                key(b) {
                    var v by remember { mutableFloatStateOf(eq.getBandLevel(b.toShort()).toFloat()) }
                    Text("${eq.getCenterFreq(b.toShort()) / 1000} Hz   ·   ${"%.1f".format(v / 100f)} dB")
                    Slider(
                        value = v, valueRange = min..max,
                        onValueChange = { v = it; AudioFx.setBand(b, it.toInt()) },
                        onValueChangeFinished = { prefs.setEqLevel(b, v.toInt()) }
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        var pre by remember { mutableFloatStateOf(prefs.preampPct.toFloat()) }
        Text("Pre-amp boost · ${pre.roundToInt()}%")
        Slider(
            value = pre, valueRange = 100f..200f,
            onValueChange = { pre = it; AudioFx.setPreamp(it.roundToInt()) },
            onValueChangeFinished = { prefs.preampPct = pre.roundToInt() }
        )
        var bass by remember { mutableFloatStateOf(prefs.bassStrength.toFloat()) }
        Text("Bass boost · ${(bass / 10).roundToInt()}%")
        Slider(
            value = bass, valueRange = 0f..1000f,
            onValueChange = { bass = it; AudioFx.setBass(it.roundToInt()) },
            onValueChangeFinished = { prefs.bassStrength = bass.roundToInt() }
        )
    }
}

@Composable
fun MiniAudioBar(title: String, playing: Boolean, onOpen: () -> Unit, onToggle: () -> Unit, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF111827)).clickable(onClick = onOpen).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.MusicNote, null, tint = Purple)
        Spacer(Modifier.width(10.dp))
        Text(title, Modifier.weight(1f), maxLines = 1, color = Color.White)
        IconButton(onClick = onToggle) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null) }
        IconButton(onClick = onClose) { Icon(Icons.Default.Close, null) }
    }
}

// ---------------- Player ----------------
@Composable
fun PlayerScreen(player: Player, prefs: Prefs, onSubtitle: () -> Unit, onBack: () -> Unit) {
    fun save() {
        val item = player.currentMediaItem ?: return
        val uri = item.localConfiguration?.uri?.toString() ?: return
        if (uri.contains("vault_tmp")) return // vault playback never leaves history
        val dur = player.duration
        var pos = player.currentPosition
        if (dur > 0 && pos > dur - 3000) pos = 0
        prefs.savePosition(uri, item.mediaMetadata.title?.toString() ?: uri.substringAfterLast('/'), pos)
    }
    BackHandler { save(); onBack() }
    LaunchedEffect(player) { while (true) { delay(5000); save() } }
    DisposableEffect(player) { onDispose { save() } }
    val context = LocalContext.current
    var gestureHint by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    controllerShowTimeoutMs = 3500
                    setShowNextButton(true)
                    setShowPreviousButton(true)
                }
            },
            onRelease = { it.player = null }
        )
        Box(Modifier.fillMaxSize().pointerInput(player) {
            detectDragGestures(
                onDragEnd = { gestureHint = "" },
                onDragCancel = { gestureHint = "" },
                onDrag = { change, drag ->
                    change.consume()
                    val left = change.position.x < size.width / 2f
                    if (left) {
                        val activity = context as? Activity
                        val attrs = activity?.window?.attributes
                        if (attrs != null) {
                            val current = if (attrs.screenBrightness >= 0f) attrs.screenBrightness else 0.5f
                            val next = (current - drag.y / size.height).coerceIn(0.02f, 1f)
                            attrs.screenBrightness = next
                            activity.window.attributes = attrs
                            gestureHint = "Brightness ${(next * 100).toInt()}%"
                        }
                    } else {
                        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                        val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                        val next = (current - (drag.y / size.height * max).toInt()).coerceIn(0, max)
                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
                        gestureHint = "Volume ${next * 100 / max}%"
                    }
                }
            )
        })
        Column(Modifier.align(Alignment.TopEnd).padding(12.dp)) {
            FilledTonalButton(onClick = onSubtitle) {
                Icon(Icons.Default.Subtitles, null)
                Spacer(Modifier.width(6.dp))
                Text("Subtitle")
            }
        }
        if (gestureHint.isNotBlank()) {
            Surface(Modifier.align(Alignment.Center), color = Color(0xCC10131C), shape = RoundedCornerShape(16.dp)) {
                Text(gestureHint, Modifier.padding(horizontal = 22.dp, vertical = 12.dp), color = Color.White)
            }
        }
    }
}
