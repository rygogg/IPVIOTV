package com.ipvio.stream

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Default long-press threshold in ms. */
const val LONG_OK_MS = 350L

/**
 * Stores customizable Short-Press and Long-Press actions for every remote control button
 * (including onn. 4K Pro dedicated Channel Up/Down and Guide keys) per profile.
 */
object RemoteKeyPrefs {
    private const val PREF = "ipvio_remote_keys"

    // Assignable actions in Live TV Player
    const val ACT_CONTROLS = 0
    const val ACT_GUIDE = 1
    const val ACT_CHANNELS = 2
    const val ACT_LAST_CH = 3
    const val ACT_MULTIVIEW = 4
    const val ACT_SECOND_PIP = 5
    const val ACT_FAV_TOGGLE = 6
    const val ACT_FAV_MENU = 7
    const val ACT_RECORD = 8
    const val ACT_RESTART_SHOW = 9
    const val ACT_AUDIO_SUBS = 10
    const val ACT_ASPECT = 11
    const val ACT_QUALITY = 12
    const val ACT_SCORE_OVERLAY = 13
    const val ACT_TECH_INFO = 14
    const val ACT_PLAYER_MENU = 15
    const val ACT_PLAY_PAUSE = 16
    const val ACT_ZAP_PREV = 17
    const val ACT_ZAP_NEXT = 18
    const val ACT_EXIT = 19
    const val ACT_NONE = 20

    val ACTION_LABELS = listOf(
        "Steuerleiste / Info zeigen",
        "TV-Guide (EPG) öffnen",
        "Senderliste öffnen",
        "Letzter Sender (Recall)",
        "Mehrfachansicht (8x Multi-View)",
        "Zusatzbild (PiP / Split-Screen)",
        "Favorit an/aus",
        "Sender-Menü & Eigene Gruppen",
        "Aufnahme starten / stoppen",
        "Sendung von Anfang an (Catchup)",
        "Audio & Untertitel",
        "Bildformat wechseln",
        "Videoqualität / Quelle wählen",
        "Live-Score-Overlay (Sports Hub)",
        "Technik- & Stream-Info an/aus",
        "Player-Einstellungen öffnen",
        "Pause / Weiter (Timeshift)",
        "Vorheriger Sender (Zap −)",
        "Nächster Sender (Zap +)",
        "Player verlassen (Zurück)",
        "Keine Aktion"
    )

    // Remote buttons
    const val BTN_OK = "ok"
    const val BTN_BACK = "back"
    const val BTN_UP = "up"
    const val BTN_DOWN = "down"
    const val BTN_LEFT = "left"
    const val BTN_RIGHT = "right"
    const val BTN_CH_UP = "ch_up"
    const val BTN_CH_DOWN = "ch_down"
    const val BTN_MENU = "menu"
    const val BTN_PLAY = "play"
    const val BTN_GUIDE = "guide"

    class BtnDef(val id: String, val label: String, val defShort: Int, val defLong: Int)

    val BUTTONS = listOf(
        BtnDef(BTN_OK, "OK / Auswählen-Taste", ACT_CONTROLS, ACT_FAV_MENU),
        BtnDef(BTN_BACK, "Zurück-Taste (Back)", ACT_EXIT, ACT_LAST_CH),
        BtnDef(BTN_UP, "Steuerkreuz Hoch (Up)", ACT_ZAP_PREV, ACT_MULTIVIEW),
        BtnDef(BTN_DOWN, "Steuerkreuz Runter (Down)", ACT_ZAP_NEXT, ACT_LAST_CH),
        BtnDef(BTN_LEFT, "Steuerkreuz Links (Left)", ACT_CHANNELS, ACT_FAV_TOGGLE),
        BtnDef(BTN_RIGHT, "Steuerkreuz Rechts (Right)", ACT_GUIDE, ACT_SCORE_OVERLAY),
        BtnDef(BTN_CH_UP, "Kanal Hoch (CH+ / PageUp)", ACT_ZAP_PREV, ACT_GUIDE),
        BtnDef(BTN_CH_DOWN, "Kanal Runter (CH− / PageDown)", ACT_ZAP_NEXT, ACT_CHANNELS),
        BtnDef(BTN_MENU, "Menü-Taste", ACT_PLAYER_MENU, ACT_TECH_INFO),
        BtnDef(BTN_PLAY, "Play / Pause-Taste", ACT_PLAY_PAUSE, ACT_RECORD),
        BtnDef(BTN_GUIDE, "TV-Guide / Info-Taste", ACT_GUIDE, ACT_MULTIVIEW)
    )

    val HOLD_MS_OPTIONS = listOf(250L, 350L, 500L, 700L)
    val HOLD_MS_LABELS = listOf("250 ms (Sehr schnell)", "350 ms (Standard)", "500 ms (Normal)", "700 ms (Lang)")

    var version by mutableIntStateOf(0)
        private set

    fun holdMs(c: Context): Long {
        val idx = holdIdx(c)
        return HOLD_MS_OPTIONS.getOrElse(idx) { LONG_OK_MS }
    }

    fun holdIdx(c: Context): Int =
        Profiles.sp(c, PREF).getInt("hold_idx", 1).coerceIn(0, HOLD_MS_OPTIONS.lastIndex)

    fun setHoldIdx(c: Context, idx: Int) {
        Profiles.sp(c, PREF).edit().putInt("hold_idx", idx.coerceIn(0, HOLD_MS_OPTIONS.lastIndex)).apply()
        version++
    }

    fun getShort(c: Context, btnId: String): Int {
        val def = BUTTONS.firstOrNull { it.id == btnId }?.defShort ?: ACT_NONE
        return Profiles.sp(c, PREF).getInt("s_$btnId", def).coerceIn(0, ACTION_LABELS.lastIndex)
    }

    fun getLong(c: Context, btnId: String): Int {
        val def = BUTTONS.firstOrNull { it.id == btnId }?.defLong ?: ACT_NONE
        return Profiles.sp(c, PREF).getInt("l_$btnId", def).coerceIn(0, ACTION_LABELS.lastIndex)
    }

    fun setShort(c: Context, btnId: String, act: Int) {
        Profiles.sp(c, PREF).edit().putInt("s_$btnId", act.coerceIn(0, ACTION_LABELS.lastIndex)).apply()
        version++
    }

    fun setLong(c: Context, btnId: String, act: Int) {
        Profiles.sp(c, PREF).edit().putInt("l_$btnId", act.coerceIn(0, ACTION_LABELS.lastIndex)).apply()
        version++
    }

    fun resetAll(c: Context) {
        Profiles.sp(c, PREF).edit().clear().apply()
        version++
    }

    fun buttonIdOf(k: Key): String? = when (k) {
        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> BTN_OK
        Key.Back, Key.Escape -> BTN_BACK
        Key.DirectionUp -> BTN_UP
        Key.DirectionDown -> BTN_DOWN
        Key.DirectionLeft -> BTN_LEFT
        Key.DirectionRight -> BTN_RIGHT
        Key.ChannelUp, Key.PageUp -> BTN_CH_UP
        Key.ChannelDown, Key.PageDown -> BTN_CH_DOWN
        Key.Menu -> BTN_MENU
        Key.MediaPlayPause, Key.Spacebar -> BTN_PLAY
        Key.Guide, Key.Info, Key.Tv -> BTN_GUIDE
        else -> null
    }
}

@Composable
internal fun RemoteKeySettingsSection(ctx: Context) {
    @Suppress("UNUSED_VARIABLE")
    val ver = RemoteKeyPrefs.version
    SectionTitle("Fernbedienung & Tastenbelegung (TiviMate-Stil)")
    Hint("Lege für jede Taste deiner Fernbedienung (inkl. CH+/CH− und Guide auf dem onn. 4K Pro) fest, was bei kurzem Druck und bei langem Gedrückthalten im Live-TV-Player passiert.")
    val holdI = RemoteKeyPrefs.holdIdx(ctx)
    SelectRow(
        "Verzögerung für langen Tastendruck",
        RemoteKeyPrefs.HOLD_MS_LABELS[holdI],
        RemoteKeyPrefs.HOLD_MS_LABELS,
        holdI
    ) { RemoteKeyPrefs.setHoldIdx(ctx, it) }

    RemoteKeyPrefs.BUTTONS.forEach { b ->
        val sAct = RemoteKeyPrefs.getShort(ctx, b.id)
        val lAct = RemoteKeyPrefs.getLong(ctx, b.id)
        SelectRow(
            b.label + " · Kurz drücken",
            RemoteKeyPrefs.ACTION_LABELS[sAct],
            RemoteKeyPrefs.ACTION_LABELS,
            sAct,
            active = sAct != b.defShort
        ) { RemoteKeyPrefs.setShort(ctx, b.id, it) }
        SelectRow(
            b.label + " · Lang gedrückt halten",
            RemoteKeyPrefs.ACTION_LABELS[lAct],
            RemoteKeyPrefs.ACTION_LABELS,
            lAct,
            active = lAct != b.defLong
        ) { RemoteKeyPrefs.setLong(ctx, b.id, it) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PillButton("Tastenbelegung auf Standard zurücksetzen", icon = AIcon.Refresh) {
            RemoteKeyPrefs.resetAll(ctx)
        }
    }
}

/**
 * Long-OK detection shared by [favKeys] and [menuKeys]. Returns true when the event was consumed.
 * The action runs on release: if it opens a menu, that menu would otherwise receive the release as a click on its first entry.
 */
private class LongOk {
    var done by mutableStateOf(false)
    var timer: Job? = null
}

@Composable
private fun Modifier.longOkKeys(onMenu: () -> Unit, onLong: () -> Unit): Modifier {
    val st = remember { LongOk() }
    val scope = rememberCoroutineScope()
    return this.onPreviewKeyEvent { e ->
        when {
            (e.key == Key.Menu || e.key == Key.Guide || e.key == Key.Info) && e.type == KeyEventType.KeyDown -> {
                onMenu()
                true
            }
            (e.key == Key.DirectionCenter || e.key == Key.Enter) && e.type == KeyEventType.KeyDown -> {
                if (e.nativeKeyEvent.repeatCount == 0) {
                    st.done = false
                    st.timer?.cancel()
                    st.timer = scope.launch { delay(LONG_OK_MS); st.done = true }
                    false
                } else if (st.done || e.nativeKeyEvent.isLongPress) {
                    st.done = true
                    true
                } else false
            }
            (e.key == Key.DirectionCenter || e.key == Key.Enter) && e.type == KeyEventType.KeyUp -> {
                st.timer?.cancel()
                st.timer = null
                if (st.done) {
                    st.done = false
                    onLong()
                    true
                } else false
            }
            else -> false
        }
    }
}

/** Menu key or a long OK runs [onFav] (on release). */
@Composable
fun Modifier.favKeys(onFav: () -> Unit): Modifier = longOkKeys(onFav, onFav)

/**
 * Menu key opens a menu ([onMenu]), OK held long does [onLong] (on release).
 * Many remotes (Fire TV) have no menu key: then a long OK opens the same menu.
 */
@Composable
fun Modifier.menuKeys(onMenu: () -> Unit, onLong: () -> Unit): Modifier = longOkKeys(onMenu, onLong)