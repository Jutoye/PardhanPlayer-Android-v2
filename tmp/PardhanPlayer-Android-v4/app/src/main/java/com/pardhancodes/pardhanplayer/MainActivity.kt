package com.pardhancodes.pardhanplayer

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Context
import android.media.AudioManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.media3.common.MimeTypes
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.pardhancodes.pardhanplayer.service.AudioFx
import com.pardhancodes.pardhanplayer.service.PardhanPlaybackService
import com.pardhancodes.pardhanplayer.vault.PrivateVaultManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class Screen { TABS, VAULT, EQ }

class MainActivity : FragmentActivity() {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val controller = mutableStateOf<MediaController?>(null)
    private val pendingUri = mutableStateOf<Uri?>(null)
    private val inPlayer = mutableStateOf(false)
    private val vaultUnlocked = mutableStateOf(false)
    private val historyVersion = mutableIntStateOf(0)
    private var pickingFile = false
    private val vault by lazy { PrivateVaultManager(this) }
    private val prefs by lazy { Prefs(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingUri.value = intent?.data

        val token = SessionToken(this, ComponentName(this, PardhanPlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener(
            { runCatching { controller.value = future.get() } },
            ContextCompat.getMainExecutor(this)
        )

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF000000),
                    surface = Color(0xFF000000),
                    primary = Blue,
                    secondary = Purple
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize()) { PardhanApp() }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingUri.value = intent.data
    }

    override fun onStop() {
        super.onStop()
        if (!pickingFile && !isInPictureInPictureMode) vaultUnlocked.value = false
    }

    override fun onUserLeaveHint() {
        val c = controller.value
        if (inPlayer.value && c != null && c.isPlaying && c.videoSize.width > 0) {
            enterVideoPip(c.videoSize.width, c.videoSize.height)
        }
    }

    override fun onDestroy() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        if (isFinishing) vault.clearTemp()
        super.onDestroy()
    }

    fun enterVideoPip(width: Int, height: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ratio = Rational(width.coerceAtLeast(1), height.coerceAtLeast(1))
            runCatching {
                enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(ratio).build())
            }
        }
    }

    // ---------- permissions ----------
    private fun mediaPerms(): Array<String> =
        if (Build.VERSION.SDK_INT >= 33)
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    private fun requestPerms(): Array<String> =
        if (Build.VERSION.SDK_INT >= 33) mediaPerms() + Manifest.permission.POST_NOTIFICATIONS
        else mediaPerms()

    private fun hasMediaPerms() = mediaPerms().all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    // ---------- playback ----------
    private fun playItems(items: List<MediaItem>, index: Int, startOverride: Long? = null) {
        val c = controller.value ?: run { toast("Player start ho raha hai, dobara try karein"); return }
        val uri = items.getOrNull(index)?.localConfiguration?.uri?.toString()
        val saved = startOverride ?: (uri?.let { prefs.position(it) } ?: 0L)
        c.setMediaItems(items, index, if (saved > 5000 || startOverride != null) saved else 0L)
        c.prepare()
        c.play()
        inPlayer.value = true
    }

    // ---------- UI ----------
    @Composable
    private fun PardhanApp() {
        val scope = rememberCoroutineScope()
        var granted by remember { mutableStateOf(hasMediaPerms()) }
        val permLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { granted = hasMediaPerms() }
        LaunchedEffect(Unit) { if (!granted) permLauncher.launch(requestPerms()) }

        var tab by remember { mutableIntStateOf(0) }
        var screen by remember { mutableStateOf(Screen.TABS) }
        var videos by remember { mutableStateOf<List<MediaEntry>>(emptyList()) }
        var audios by remember { mutableStateOf<List<MediaEntry>>(emptyList()) }
        var scanKey by remember { mutableIntStateOf(0) }
        var showStream by remember { mutableStateOf(false) }
        var showPin by remember { mutableStateOf(false) }

        LaunchedEffect(granted, scanKey) {
            if (granted) {
                videos = withContext(Dispatchers.IO) { MediaScanner.scan(this@MainActivity, true) }
                audios = withContext(Dispatchers.IO) { MediaScanner.scan(this@MainActivity, false) }
            }
        }

        val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            pickingFile = false
            if (uris.isNotEmpty()) playItems(uris.map { MediaItem.fromUri(it) }, 0, 0L)
        }
        val subtitleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            pickingFile = false
            val player = controller.value
            if (uri != null && player != null) runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val current = player.currentMediaItem
                if (current == null) toast("Pehle video play karein")
                else {
                    val mime = when (uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase()) {
                        "vtt" -> MimeTypes.TEXT_VTT
                        else -> MimeTypes.APPLICATION_SUBRIP
                    }
                    val subtitle = MediaItem.SubtitleConfiguration.Builder(uri)
                        .setMimeType(mime).setLanguage("und").setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()
                    val updated = current.buildUpon().setSubtitleConfigurations(listOf(subtitle)).build()
                    val pos = player.currentPosition
                    player.replaceMediaItem(player.currentMediaItemIndex, updated)
                    player.seekTo(pos)
                    toast("Subtitle load ho gayi")
                }
            }.onFailure { toast("Subtitle load nahi hui: ${it.message}") }
        }
        val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            pickingFile = false
            if (uri != null) runCatching {
                contentResolver.openOutputStream(uri)!!.use { it.write(prefs.exportJson().toByteArray()) }
            }.onSuccess { toast("Backup save ho gaya") }.onFailure { toast("Backup fail") }
        }
        val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            pickingFile = false
            if (uri != null) runCatching {
                val s = contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
                prefs.importJson(s)
                AudioFx.applyPrefs(prefs)
                historyVersion.intValue++
            }.onSuccess { toast("Backup restore ho gaya") }.onFailure { toast("Restore fail") }
        }

        // Media opened from other apps / links
        val pending = pendingUri.value
        val c = controller.value
        LaunchedEffect(pending, c) {
            if (pending != null && c != null) {
                playItems(listOf(MediaItem.fromUri(pending)), 0)
                pendingUri.value = null
            }
        }

        val hv = historyVersion.intValue
        when {
            inPlayer.value && c != null -> PlayerScreen(c, prefs, onSubtitle = { pickingFile = true; subtitleLauncher.launch(arrayOf("application/x-subrip", "text/vtt", "text/plain", "*/*")) }) {
                if (c.videoSize.width > 0) c.pause()
                inPlayer.value = false
            }
            screen == Screen.VAULT -> VaultScreen(onShowPin = { showPin = true }) { screen = Screen.TABS }
            screen == Screen.EQ -> EqualizerScreen(prefs) { screen = Screen.TABS }
            else -> Scaffold(
                topBar = {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp, 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Pardhan Player", style = MaterialTheme.typography.titleLarge)
                        Button(onClick = {
                            pickingFile = true
                            importLauncher.launch(arrayOf("video/*", "audio/*"))
                        }, shape = RoundedCornerShape(20.dp)) {
                            Icon(Icons.Default.Upload, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Import Media")
                        }
                    }
                },
                bottomBar = {
                    Column {
                        if (c != null && c.currentMediaItem != null &&
                            c.currentMediaItem?.localConfiguration?.uri?.toString()?.let {
                                it.startsWith("content:") || it.startsWith("file:")
                            } == true
                        ) {
                            MiniAudioBar(
                                title = c.currentMediaItem?.mediaMetadata?.title?.toString() ?: "Now Playing",
                                playing = c.isPlaying,
                                hasNext = c.hasNextMediaItem(),
                                onOpen = { inPlayer.value = true },
                                onToggle = { if (c.isPlaying) c.pause() else c.play() },
                                onNext = { c.seekToNextMediaItem() },
                                onClose = { c.stop() }
                            )
                        }
                        NavigationBar {
                            NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Default.Home, null) }, label = { Text("Home") })
                            NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Default.VideoLibrary, null) }, label = { Text("Videos") })
                            NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Default.MusicNote, null) }, label = { Text("Music") })
                            NavigationBarItem(tab == 3, { tab = 3 }, { Icon(Icons.Default.Folder, null) }, label = { Text("Folders") })
                            NavigationBarItem(tab == 4, { tab = 4 }, { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") })
                        }
                    }
                }
            ) { pad ->
                Box(Modifier.padding(pad).fillMaxSize()) {
                    val onPlay: (List<MediaEntry>, Int) -> Unit = { list, i -> playItems(list.map { it.toItem() }, i) }
                    when (tab) {
                        0 -> HomeScreen(
                            lastTitle = prefs.lastTitle(), lastPos = prefs.lastPos(),
                            onResume = {
                                val u = prefs.lastUri()
                                if (u != null) playItems(
                                    listOf(
                                        MediaItem.Builder().setUri(Uri.parse(u))
                                            .setMediaMetadata(MediaMetadata.Builder().setTitle(prefs.lastTitle()).build())
                                            .build()
                                    ), 0, prefs.lastPos()
                                )
                            },
                            onScan = {
                                if (granted) { scanKey++; toast("Scan ho raha hai...") }
                                else permLauncher.launch(requestPerms())
                            },
                            onFolders = { tab = 3 },
                            onNetwork = { showStream = true },
                            onVault = { screen = Screen.VAULT },
                            onEq = { screen = Screen.EQ }
                        )
                        1 -> SearchableMediaList(videos, "Search videos...", onPlay)
                        2 -> SearchableMediaList(audios, "Search music...", onPlay)
                        3 -> FoldersScreen(videos + audios, onPlay)
                        else -> SettingsScreen(
                            onGrant = { permLauncher.launch(requestPerms()); scanKey++ },
                            onPin = { showPin = true },
                            onBackup = { pickingFile = true; backupLauncher.launch("pardhan-player-backup.json") },
                            onRestore = { pickingFile = true; restoreLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                            onClear = { prefs.clearHistory(); historyVersion.intValue++; toast("Watch history clear") }
                        )
                    }
                }
            }
        }

        if (showStream) StreamDialog(onDismiss = { showStream = false }) { url ->
            showStream = false
            playItems(listOf(MediaItem.fromUri(url)), 0, 0L)
        }
        if (showPin) PinDialog { showPin = false }
    }

    @Composable
    private fun StreamDialog(onDismiss: () -> Unit, onPlay: (String) -> Unit) {
        var url by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Network Stream") },
            text = {
                OutlinedTextField(
                    value = url, onValueChange = { url = it }, singleLine = true,
                    placeholder = { Text("https://.../playlist.m3u8") }
                )
            },
            confirmButton = { TextButton(onClick = { if (url.isNotBlank()) onPlay(url.trim()) }) { Text("Play") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        )
    }

    @Composable
    private fun PinDialog(onDismiss: () -> Unit) {
        val hasPin = prefs.hasPin()
        var old by remember { mutableStateOf("") }
        var new by remember { mutableStateOf("") }
        val num = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(if (hasPin) "Change Vault PIN" else "Set Vault PIN") },
            text = {
                Column {
                    if (hasPin) {
                        OutlinedTextField(
                            old, { old = it }, singleLine = true, label = { Text("Current PIN") },
                            visualTransformation = PasswordVisualTransformation(), keyboardOptions = num
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    OutlinedTextField(
                        new, { new = it }, singleLine = true, label = { Text("New PIN (4+ digits)") },
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = num
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    when {
                        hasPin && !prefs.checkPin(old) -> toast("Purana PIN ghalat hai")
                        new.length < 4 -> toast("PIN kam az kam 4 digits ka ho")
                        else -> { prefs.setPin(new); toast("PIN set ho gaya"); onDismiss() }
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        )
    }

    @Composable
    private fun VaultScreen(onShowPin: () -> Unit, onBack: () -> Unit) {
        BackHandler(onBack = onBack)
        val scope = rememberCoroutineScope()
        var refresh by remember { mutableIntStateOf(0) }
        var pin by remember { mutableStateOf("") }
        val unlocked = vaultUnlocked.value
        val hasPin = prefs.hasPin()

        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            pickingFile = false
            if (uri != null) scope.launch {
                withContext(Dispatchers.IO) { runCatching { vault.importUri(uri) } }
                refresh++
                toast("Vault mein add ho gaya (original khud delete karein)")
            }
        }

        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹ Back") }
                Text("Private Vault", style = MaterialTheme.typography.headlineSmall)
            }
            Spacer(Modifier.height(12.dp))
            when {
                !hasPin -> {
                    Text("Pehle vault ka PIN set karein.")
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onShowPin) { Text("Set PIN") }
                }
                !unlocked -> {
                    OutlinedTextField(
                        pin, { pin = it }, singleLine = true, label = { Text("Vault PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            if (prefs.checkPin(pin)) { vaultUnlocked.value = true; pin = "" }
                            else toast("PIN ghalat hai")
                        }) { Text("Unlock") }
                        FilledTonalButton(onClick = {
                            vault.authenticateBiometric(
                                this@MainActivity,
                                onSuccess = { vaultUnlocked.value = true },
                                onError = { toast(it) }
                            )
                        }) { Icon(Icons.Default.Fingerprint, null); Spacer(Modifier.width(6.dp)); Text("Biometric") }
                    }
                }
                else -> {
                    Button(onClick = { pickingFile = true; picker.launch(arrayOf("video/*", "audio/*")) }) {
                        Text("Add file to Vault")
                    }
                    val files = remember(refresh) { vault.listVaultFiles() }
                    if (files.isEmpty()) Text("Vault khali hai.", Modifier.padding(top = 12.dp))
                    LazyColumn {
                        items(files.size) { i ->
                            val f = files[i]
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    scope.launch {
                                        val plain: File? = withContext(Dispatchers.IO) {
                                            runCatching { vault.decryptToCache(f) }.getOrNull()
                                        }
                                        if (plain != null) playItems(listOf(MediaItem.fromUri(Uri.fromFile(plain))), 0, 0L)
                                        else toast("Decrypt fail")
                                    }
                                }.padding(0.dp, 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Lock, null, tint = Purple)
                                Spacer(Modifier.width(12.dp))
                                Text(f.name.removeSuffix(".pvault"), maxLines = 2)
                            }
                            HorizontalDivider(color = Color(0xFF16181F))
                        }
                    }
                }
            }
        }
    }
}
