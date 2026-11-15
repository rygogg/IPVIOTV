package com.ipvio.stream

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Default TiviMate-inspired Deep Navy (#080D1A), Steel Blue (#4682B4), and Icy Blue (#7DD3FC) palette
private val TiviBg = Color(0xFF080D1A)
private val TiviSurf = Color(0xFF0F172A)
private val TiviSurf2 = Color(0xFF1E293B)
private val TiviSteel = Color(0xFF4682B4)
private val TiviIcy = Color(0xFF7DD3FC)

val Bg: Color get() = if (Look.bgIdx == Look.DEFAULT_BG) TiviBg else Look.bg
val Surf: Color get() = if (Look.bgIdx == Look.DEFAULT_BG) TiviSurf else Look.surf
val Surf2: Color get() = if (Look.bgIdx == Look.DEFAULT_BG) TiviSurf2 else Look.surf2
val Accent1: Color get() = if (Look.accentIdx == Look.DEFAULT_ACCENT) TiviSteel else Look.accent1
val Accent2: Color get() = if (Look.accentIdx == Look.DEFAULT_ACCENT) TiviIcy else Look.accent2
val OnAccent: Color get() = if (Look.accentIdx == Look.DEFAULT_ACCENT) Color.White else Look.onAccent
val Muted = Color(0xFF94A3B8)

/**
 * Color tokens tuned for Deep Navy (#080D1A), Steel Blue (#4682B4), and Icy Blue (#7DD3FC).
 */
object Tok {
    val TextHi = Color(0xFFF8FAFC)
    val TextMid = Color(0xFFCBD5E1)
    val TextLow = Color(0xFF94A3B8)
    val Card: Color get() = lerp(Surf, Accent1, 0.06f)
    val CardFocus: Color get() = lerp(Surf2, Accent1, 0.28f)
    val Glass: Color get() = Bg.copy(alpha = 0.84f)
    val GlassStrong: Color get() = Bg.copy(alpha = 0.95f)
    val Line = Color(0x2E7DD3FC)
    val Lift = Color(0xE6040914)
}

/**
 * Design system: central measures and colors.
 * Focus uses Icy Blue (#7DD3FC) and Steel Blue (#4682B4) instead of plain white.
 */
object DS {
    val SpaceXs = 4.dp
    val PageX = 48.dp
    val PageY = 28.dp
    val SidebarW: Dp get() = if (SidebarPrefs.labels) SidebarOpenW else SidebarCollapsedW
    val SidebarCollapsedW: Dp get() = dpx(92)
    val SidebarOpenW: Dp get() = dpx(250)

    val RadiusXs = 8.dp
    val RadiusS = 12.dp
    val RadiusM = 16.dp
    val RadiusL = 20.dp
    val RadiusXl = 24.dp

    val Border = 2.dp
    val FocusBorder = 3.dp

    val TextCaption = 12.sp
    val TextSmall = 14.sp
    val TextBody = 16.sp
    val TextLead = 18.sp

    val Overlay1 = Color(0x184682B4)
    val Overlay2 = Color(0x264682B4)
    val Overlay3 = Color(0x384682B4)
    val Overlay4 = Color(0x507DD3FC)
    val Hairline = Color(0x267DD3FC)
    val Scrim = Color(0xB3040914)

    val TextSoft = Color(0xFFD7DAE5)
    val Danger = Color(0xFFFF6B6B)
    val Gold = Color(0xFFFFD166)

    // Focus for buttons and pills: Icy Blue surface with Deep Navy ink
    val FocusFill = Color(0xFF7DD3FC)
    val FocusInk = Color(0xFF080D1A)
}

/** Surface of a row with focus (blended with Steel Blue accent). */
val RowFocus: Color get() = lerp(Surf, Accent1, 0.28f)

class ButtonColors(val bg: Color, val text: Color)

fun buttonColors(focused: Boolean, primary: Boolean): ButtonColors = when {
    focused -> ButtonColors(DS.FocusFill, DS.FocusInk)
    primary -> ButtonColors(lerp(DS.Overlay3, Accent1, 0.45f), Color.White)
    else -> ButtonColors(DS.Overlay3, Color.White)
}

/** Soft icy-blue glow around focused cards and tiles. */
fun Modifier.focusGlow(focused: Boolean, shape: androidx.compose.ui.graphics.Shape): Modifier =
    if (!focused) this else this.shadow(
        12.dp,
        shape,
        clip = false,
        ambientColor = Accent2.copy(alpha = 0.55f),
        spotColor = Accent2.copy(alpha = 0.65f)
    )

@Composable
fun FocusButton(text: String, modifier: Modifier = Modifier, primary: Boolean = false, compact: Boolean = false, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (f) focusScale(1.07f) else 1f, label = "btn")
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .graphicsLayer { scaleX = s; scaleY = s }
            .clip(shape)
            .background(
                if (f) SolidColor(DS.FocusFill)
                else if (primary) SolidColor(lerp(DS.Overlay3, Accent1, 0.45f))
                else SolidColor(DS.Overlay3)
            )
            .border(if (f) 2.dp else 1.dp, if (f) Color.White else DS.Hairline, shape)
            .onFocusChanged { f = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = if (compact) 20.dp else 28.dp, vertical = if (compact) 9.dp else 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (f) DS.FocusInk else Color.White,
            fontSize = if (compact) 16.sp else DS.TextLead,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun Field(
    value: String, onChange: (String) -> Unit, hint: String,
    modifier: Modifier = Modifier, inner: Modifier = Modifier, password: Boolean = false,
    number: Boolean = false,
    onDone: (() -> Unit)? = null,
    autoOpen: Boolean = false
) {
    var f by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(autoOpen) }
    var hadFocus by remember { mutableStateOf(false) }
    var returnToBox by remember { mutableStateOf(false) }
    val boxReq = remember { FocusRequester() }
    val textReq = remember { FocusRequester() }
    val fm = LocalFocusManager.current
    val kb = LocalSoftwareKeyboardController.current
    LaunchedEffect(editing) {
        if (editing) {
            runCatching { textReq.requestFocus() }
            kb?.show()
        } else if (returnToBox) {
            returnToBox = false
            runCatching { boxReq.requestFocus() }
        }
    }
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .fillMaxWidth().height(62.dp)
            .clip(shape).background(if (f) RowFocus else Surf)
            .border(if (f) DS.FocusBorder else DS.Border, if (f) Accent2 else Surf2, shape)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            inner.fillMaxSize()
                .focusRequester(boxReq)
                .onFocusChanged { f = it.isFocused || it.hasFocus }
                .clickable { editing = true }
        )
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = true, enabled = editing,
            textStyle = TextStyle(color = Color.White, fontSize = 20.sp),
            cursorBrush = SolidColor(Accent2),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done)
                else KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                onDone?.invoke()
                kb?.hide()
                returnToBox = true
                editing = false
            }),
            modifier = Modifier.fillMaxWidth()
                .focusRequester(textReq)
                .onFocusChanged {
                    if (it.isFocused) { hadFocus = true; f = true }
                    else if (hadFocus) { hadFocus = false; if (editing) editing = false }
                }
                .onPreviewKeyEvent { e ->
                    val up = e.key == Key.DirectionUp
                    if (e.type == KeyEventType.KeyDown && (up || e.key == Key.DirectionDown)) {
                        kb?.hide()
                        fm.moveFocus(if (up) FocusDirection.Up else FocusDirection.Down)
                        true
                    } else false
                }
        )
        if (value.isEmpty()) Text(hint, color = Muted, fontSize = 20.sp)
    }
}

@Composable
fun Spinner(color: Color, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "spin")
    val a by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "spinA")
    Canvas(modifier.rotate(if (Look.motion > 0) a else 0f)) {
        val w = size.minDimension * 0.09f
        val tl = androidx.compose.ui.geometry.Offset(w / 2, w / 2)
        val sz = androidx.compose.ui.geometry.Size(size.width - w, size.height - w)
        drawArc(color.copy(alpha = 0.18f), 0f, 360f, false, tl, sz, style = Stroke(w))
        drawArc(color, -90f, 100f, false, tl, sz, style = Stroke(w, cap = StrokeCap.Round))
    }
}

@Composable
fun shimmerColor(): Color {
    val t = rememberInfiniteTransition(label = "sk")
    val a by t.animateFloat(0.05f, 0.12f, infiniteRepeatable(tween(900), androidx.compose.animation.core.RepeatMode.Reverse), label = "ska")
    return Accent2.copy(alpha = if (Look.motion > 0) a else 0.08f)
}

@Composable
fun SkeletonBox(modifier: Modifier, color: Color = shimmerColor(), shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(DS.RadiusXs)) {
    Box(modifier.clip(shape).background(color))
}