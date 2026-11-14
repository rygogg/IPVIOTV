package com.ipvio.stream

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Layouts of the multi view: supports up to 8 concurrent streams across adaptive grid and hero layouts. */
object MultiViewLogic {
    const val GRID = 0       // adaptive grid (1x2, 2x2, 2x3, 2x4)
    const val BIG = 1        // one large hero tile on the left, remaining 1..7 tiles gridded on the right
    const val DUAL_HERO = 2  // two large hero tiles on top, remaining 1..6 tiles in a bottom strip
    val LAYOUT_NAMES = listOf("Zwei gleich groß", "Einer groß, einer klein", "2 Hero + Strip")

    /** Default initial slot count kept at 2 for backwards compatibility with unit tests. */
    const val SLOTS = 2
    /** Maximum simultaneous streams supported in Multi-View (tuned for 8-way sports viewing). */
    const val MAX_SLOTS = 8
    /** Default number of streams opened when launching Multi-View. */
    const val DEFAULT_OPEN_SLOTS = 4

    /** Queued channel IDs pushed from the Sports Hub ("+ Multi-View"). */
    val queuedIds = mutableStateListOf<String>()

    fun queueChannel(id: String) {
        queuedIds.remove(id)
        queuedIds.add(0, id)
        while (queuedIds.size > MAX_SLOTS) queuedIds.removeAt(queuedIds.lastIndex)
    }

    /** Start channels (unit-test compatible 2-slot signature). */
    fun startChannels(live: List<Item>, favs: List<String>, hist: List<String>): List<Item> =
        startChannels(live, favs, hist, SLOTS)

    /** Start channels up to [limit] distinct live channels from queued sports, favorites, history, then the full list. */
    fun startChannels(live: List<Item>, favs: List<String>, hist: List<String>, limit: Int): List<Item> {
        val cap = limit.coerceIn(1, MAX_SLOTS)
        val byId = live.associateBy { it.id }
        val out = LinkedHashMap<String, Item>()
        for (id in queuedIds + favs + hist) byId[id]?.let { out.putIfAbsent(it.id, it) }
        for (i in live) {
            if (out.size >= cap) break
            out.putIfAbsent(i.id, i)
        }
        return out.values.take(cap)
    }
}

private val MvBg = Color(0xFF080D1A)
private val MvTileBg = Color(0xFF0F172A)
private val MvFocusBorder = Color(0xFF7DD3FC)
private val MvAudioBorder = Color(0xFF4682B4)
private val MvIdleBorder = Color(0xFF1E293B)

/**
 * Multi view: up to 8 live streams at once.
 * Short OK on a tile = route audio to this tile.
 * Long OK on a tile = pick another channel for this tile.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MultiViewScreen(live: List<Item>, onClose: () -> Unit) {
    val openCount = remember {
        MultiViewLogic.queuedIds.size.coerceAtLeast(MultiViewLogic.DEFAULT_OPEN_SLOTS).coerceAtMost(MultiViewLogic.MAX_SLOTS)
    }
    val slots = remember {
        mutableStateListOf<Item>().also {
            it.addAll(MultiViewLogic.startChannels(live, Library.favs.toList(), History.ids.toList(), openCount))
        }
    }
    var layout by remember { mutableIntStateOf(MultiViewLogic.GRID) }
    var audio by remember { mutableIntStateOf(0) }
    var picking by remember { mutableIntStateOf(-1) }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Column(
            Modifier.fillMaxSize().background(MvBg).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Mehrfachansicht",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                SmallPill(
                    MultiViewLogic.LAYOUT_NAMES[layout],
                    icon = AIcon.Grid
                ) {
                    layout = (layout + 1) % MultiViewLogic.LAYOUT_NAMES.size
                }
                if (slots.size < MultiViewLogic.MAX_SLOTS) {
                    SmallPill("+ Stream (${slots.size}/${MultiViewLogic.MAX_SLOTS})", icon = AIcon.Live) {
                        picking = slots.size
                    }
                }
                if (slots.size > 2) {
                    SmallPill("- Stream", icon = AIcon.Close) {
                        slots.removeAt(slots.lastIndex)
                        if (audio >= slots.size) audio = (slots.size - 1).coerceAtLeast(0)
                    }
                }
                if (audio in 1 until slots.size) {
                    SmallPill("-> Main #1", icon = AIcon.Info) {
                        val current = slots[audio]
                        slots[audio] = slots[0]
                        slots[0] = current
                        audio = 0
                    }
                }
                SmallPill("Schließen", icon = AIcon.Close) { onClose() }
                Text(
                    "OK = Ton | OK lange = Sender",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            @Composable
            fun tile(i: Int, m: Modifier) {
                val item = slots.getOrNull(i)
                var focused by remember { mutableStateOf(false) }
                val isAudio = audio == i
                val shape = RoundedCornerShape(8.dp)
                val borderWidth = when {
                    focused -> 3.dp
                    isAudio -> 2.dp
                    else -> 1.dp
                }
                val borderColor = when {
                    focused -> MvFocusBorder
                    isAudio -> MvAudioBorder
                    else -> MvIdleBorder
                }
                Box(
                    m.clip(shape)
                        .background(MvTileBg)
                        .onFocusChanged { focused = it.isFocused }
                        .border(borderWidth, borderColor, shape)
                        .combinedClickable(
                            onClick = { audio = i },
                            onLongClick = { picking = i }
                        )
                ) {
                    if (item != null) {
                        val backups = remember(item.id, live) {
                            live.filter { it.id != item.id && it.title.equals(item.title, ignoreCase = true) }.take(3)
                        }
                        key(item.id, i) {
                            SecondStream(
                                item = item,
                                split = false,
                                audioOn = isAudio,
                                modifier = Modifier.fillMaxSize(),
                                small = true,
                                slotIndex = i,
                                backups = backups
                            )
                        }
                    }
                }
            }

            val count = slots.size
            when {
                count <= 2 -> {
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        tile(0, Modifier.weight(if (layout == MultiViewLogic.BIG) 2f else 1f).fillMaxSize())
                        if (count > 1) tile(1, Modifier.weight(1f).fillMaxSize())
                    }
                }
                layout == MultiViewLogic.BIG -> {
                    val sideIndices = (1 until count).toList()
                    val cols = if (sideIndices.size <= 3) 1 else 2
                    val rows = sideIndices.chunked(cols)
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        tile(0, Modifier.weight(1.5f).fillMaxSize())
                        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            rows.forEach { rowList ->
                                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    rowList.forEach { idx ->
                                        tile(idx, Modifier.weight(1f).fillMaxSize())
                                    }
                                }
                            }
                        }
                    }
                }
                layout == MultiViewLogic.DUAL_HERO -> {
                    val bottomIndices = (2 until count).toList()
                    Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.weight(1.35f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            tile(0, Modifier.weight(1f).fillMaxSize())
                            tile(1, Modifier.weight(1f).fillMaxSize())
                        }
                        if (bottomIndices.isNotEmpty()) {
                            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                bottomIndices.forEach { idx ->
                                    tile(idx, Modifier.weight(1f).fillMaxSize())
                                }
                            }
                        }
                    }
                }
                else -> {
                    val cols = when {
                        count <= 4 -> 2
                        count <= 6 -> 3
                        else -> 4
                    }
                    val rows = (0 until count).chunked(cols)
                    Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        rows.forEach { rowList ->
                            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                rowList.forEach { idx ->
                                    tile(idx, Modifier.weight(1f).fillMaxSize())
                                }
                            }
                        }
                    }
                }
            }
        }

        if (picking >= 0) {
            GuideSearchDialog(
                live = live,
                onPick = { ch ->
                    val p = picking
                    if (p in 0 until slots.size) {
                        slots[p] = ch
                    } else if (p == slots.size && slots.size < MultiViewLogic.MAX_SLOTS) {
                        slots.add(ch)
                    }
                    picking = -1
                },
                onClose = { picking = -1 }
            )
        }
    }
}