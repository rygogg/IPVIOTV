package com.ipvio.stream

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView

/**
 * Second stream in the live player and multi-view tiles.
 * Tuned for up to 8 concurrent streams on Amlogic S905X4 (onn. 4K Pro):
 * - Disables audio track decoding completely on muted tiles.
 * - Tiers resolution and RAM buffer sizes for slots 0..3 vs slots 4..7.
 * - Falls back to alternate matching channels if a provider rejects a connection.
 */
@Composable
internal fun SecondStream(
    item: Item,
    split: Boolean,
    audioOn: Boolean,
    modifier: Modifier,
    small: Boolean = false,
    slotIndex: Int = 0,
    backups: List<Item> = emptyList()
) {
    val ctx = LocalContext.current
    var failed by remember { mutableStateOf(false) }
    var tip by remember { mutableStateOf("") }
    var retries by remember { mutableIntStateOf(0) }
    var retryTick by remember { mutableIntStateOf(0) }
    var backupIdx by remember(item.id) { mutableIntStateOf(-1) }

    val candidates = remember(item.id, backups) {
        val list = ArrayList<Item>()
        list.add(item)
        backups.forEach { b -> if (b.id != item.id && b.url != item.url) list.add(b) }
        list
    }
    val activeItem = candidates.getOrElse(if (backupIdx >= 0) backupIdx else 0) { item }
    val ultraLight = small && slotIndex >= 4

    val p = remember {
        runCatching {
            val http = DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(10_000)
                .setReadTimeoutMs(15_000)
            val lc = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    if (ultraLight) 2_000 else if (small) 2_500 else 4_000,
                    if (ultraLight) 6_000 else if (small) 10_000 else 15_000,
                    1_000,
                    1_500
                )
                .apply {
                    if (ultraLight) setTargetBufferBytes(2 * 1024 * 1024)
                    else if (small) setTargetBufferBytes(3 * 1024 * 1024)
                }
                .build()
            ExoPlayer.Builder(ctx, SoftAudio.factory(ctx))
                .setLoadControl(lc)
                .setMediaSourceFactory(
                    DefaultMediaSourceFactory(ctx).setDataSourceFactory(
                        HostFailover.DataSourceWrapper.Factory(DefaultDataSource.Factory(ctx, http))
                    )
                )
                .build()
                .also {
                    it.addListener(object : Player.Listener {
                        override fun onPlayerError(error: PlaybackException) {
                            tip = secondStreamTip(error)
                            if (retries < 1) {
                                retries++
                                retryTick++
                            } else if (backupIdx + 1 < candidates.size - 1) {
                                backupIdx++
                                retries = 0
                                retryTick++
                            } else {
                                failed = true
                            }
                        }
                        override fun onPlaybackStateChanged(state: Int) {
                            if (state == Player.STATE_READY) failed = false
                        }
                    })
                }
        }.getOrNull()
    }
    DisposableEffect(Unit) { onDispose { runCatching { p?.release() } } }

    // Tiered resolution, bitrate, and audio decoder shutoff for 8-way Multi-View stability
    LaunchedEffect(split, small, audioOn, slotIndex) {
        runCatching {
            if (p != null) {
                val maxW = when {
                    !small -> if (split) 1280 else 854
                    audioOn || slotIndex == 0 -> 1280
                    slotIndex in 1..3 -> 854
                    else -> 640
                }
                val maxH = when {
                    !small -> if (split) 720 else 480
                    audioOn || slotIndex == 0 -> 720
                    slotIndex in 1..3 -> 480
                    else -> 360
                }
                val maxBitrate = when {
                    !small -> Int.MAX_VALUE
                    audioOn || slotIndex == 0 -> 4_000_000
                    slotIndex in 1..3 -> 1_800_000
                    else -> 900_000
                }
                p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                    .setMaxVideoSize(maxW, maxH)
                    .setMaxVideoBitrate(maxBitrate)
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, small && !audioOn)
                    .build()
            }
        }
    }

    LaunchedEffect(activeItem.url, retryTick) {
        if (retryTick > 0) kotlinx.coroutines.delay(1_200) else { retries = 0; tip = "" }
        failed = false
        val playUrl = runCatching {
            if (StalkerLogic.isItem(activeItem.url)) StalkerApi.resolve(ctx, activeItem.url) else activeItem.url
        }.getOrNull()
        if (p == null || playUrl == null || !openStream(p, playUrl, C.TIME_UNSET)) failed = true
    }

    LaunchedEffect(audioOn) { runCatching { p?.volume = if (audioOn) 1f else 0f } }

    val shape = RoundedCornerShape(if (split) 0.dp else 10.dp)
    Box(
        modifier
            .clip(shape)
            .background(Color.Black)
            .border(
                if (split || small) 0.dp else 2.dp,
                if (audioOn) Accent2 else Color(0x66FFFFFF),
                shape
            )
    ) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    this.player = p
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        Row(
            Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xB3080D1A))
                .padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val prefix = if (small) "#${slotIndex + 1} " else ""
            val sound = if (audioOn) "♪ " else ""
            Text(
                raw = true,
                text = prefix + sound + activeItem.title,
                color = if (audioOn) Color(0xFF7DD3FC) else Color.White,
                fontSize = if (small) 12.sp else 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (failed) Box(Modifier.fillMaxSize().background(Color(0xCC080D1A)), contentAlignment = Alignment.Center) {
            Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Zusatzbild nicht abspielbar", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                if (tip.isNotEmpty()) Text(
                    tip,
                    color = Color(0xFFD0D0D6),
                    fontSize = 12.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 4
                )
            }
        }
    }
}

/** Short hint for a failed extra picture: what most likely happened and what to try. German key text (translated by Text). */
internal fun secondStreamTip(e: PlaybackException): String {
    val http = (e.cause as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)?.responseCode ?: 0
    return when {
        http == 404 || http == 410 -> "Dieser Sender ist beim Anbieter gerade nicht erreichbar. Probiere einen anderen."
        http in 400..599 -> "Dein Anbieter erlaubt vermutlich nur eine Verbindung gleichzeitig. Frage nach einem Zugang mit mehr Verbindungen."
        e.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED || e.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
            "Die Verbindung ist zu langsam oder wurde abgelehnt. Ein Sender in niedrigerer Qualität hilft oft."
        e.errorCode in PlaybackException.ERROR_CODE_DECODER_INIT_FAILED..PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ->
            "Dein Gerät schafft zwei Videos gleichzeitig nicht. Wähle für beide Bilder SD-Sender."
        else -> "Probiere einen anderen Sender oder eine niedrigere Qualität."
    }
}

/** Saved settings for the second stream: arrangement, window size and corner (picture-in-picture), sound. */
object DualPrefs {
    val SIZES = listOf(220, 300, 400, 520)
    val SIZE_NAMES = listOf("Klein", "Mittel", "Groß", "Sehr groß")
    val CORNER_NAMES = listOf("Unten rechts", "Unten links", "Oben rechts", "Oben links")
    val ARRANGE_NAMES = listOf("Bild-in-Bild", "Geteilt")

    var split by mutableIntStateOf(0)
        private set
    var size by mutableIntStateOf(1)
        private set
    var corner by mutableIntStateOf(0)
        private set
    var audioSecond by mutableStateOf(false)
        private set
    private var inited = false

    fun init(c: Context) {
        if (inited) return
        inited = true
        val p = Profiles.sp(c, "ipvio_dual")
        split = p.getInt("split", 0).coerceIn(0, 1)
        size = p.getInt("size", 1).coerceIn(0, SIZES.lastIndex)
        corner = p.getInt("corner", 0).coerceIn(0, CORNER_NAMES.lastIndex)
        audioSecond = p.getBoolean("audio", false)
    }

    fun set(c: Context, split: Int = this.split, size: Int = this.size, corner: Int = this.corner, audio: Boolean = this.audioSecond) {
        this.split = split.coerceIn(0, 1)
        this.size = size.coerceIn(0, SIZES.lastIndex)
        this.corner = corner.coerceIn(0, CORNER_NAMES.lastIndex)
        this.audioSecond = audio
        Profiles.sp(c, "ipvio_dual").edit().putInt("split", this.split).putInt("size", this.size).putInt("corner", this.corner).putBoolean("audio", audio).apply()
    }
}

/** Position and size of the small window (picture-in-picture). Space remains at the bottom for the control bar. */
internal fun BoxScope.pipModifier(): Modifier {
    val align = when (DualPrefs.corner) {
        1 -> Alignment.BottomStart
        2 -> Alignment.TopEnd
        3 -> Alignment.TopStart
        else -> Alignment.BottomEnd
    }
    val bottom = DualPrefs.corner < 2
    val side = 28.dp
    return Modifier.align(align)
        .padding(start = side, end = side, top = if (bottom) 0.dp else 60.dp, bottom = if (bottom) 140.dp else 0.dp)
        .width(DualPrefs.SIZES[DualPrefs.size].dp).aspectRatio(16f / 9f)
}