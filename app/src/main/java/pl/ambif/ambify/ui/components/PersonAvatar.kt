package pl.ambif.ambify.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.launch
import pl.ambif.ambify.core.network.ServerError
import pl.ambif.ambify.core.session.LocalAppSession
import pl.ambif.ambify.core.session.SessionState
import pl.ambif.ambify.shared.Action
import pl.ambif.ambify.shared.Mode
import pl.ambif.ambify.ui.theme.LuxuryPopup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import pl.ambif.ambify.shared.Avatar
import pl.ambif.ambify.shared.AvatarShape
import pl.ambif.ambify.shared.Avatars
import pl.ambif.ambify.ui.theme.Luxury

/**
 * A person's icon: initials on their color, in their shape, with a thin gold rim. The only place that draws a
 * person, so they look the same on every screen and in every module. Without an [avatar] from the server
 * (e.g. offline data from an older version) it falls back to initials in a circle.
 */
@Composable
fun PersonAvatar(name: String, avatar: Avatar?, size: Dp = 40.dp, modifier: Modifier = Modifier, zoom: AvatarZoom = AvatarZoom.TAP) {
    var zoomed by remember { mutableStateOf(false) }
    val hidden by AvatarImages.hidden.collectAsState()
    val photo = avatar?.photo?.takeUnless { it in hidden }
    val image = rememberAvatarImage(photo)
    val gesture = when (zoom) {
        AvatarZoom.TAP -> Modifier.clickable(onClickLabel = "Powiększ: $name") { zoomed = true }
        AvatarZoom.HOLD -> Modifier.onHold { zoomed = true }
        AvatarZoom.NONE -> Modifier
    }
    if (zoomed) AvatarZoomDialog(name, avatar, photo, onDismiss = { zoomed = false })
    if (image != null) {
        // A photo is always in a circle (canvas "Zdjęcia profilowe": the shapes stay for the initials).
        Box(
            modifier = modifier.size(size).clip(CircleShape).background(Luxury.BorderBrush).then(gesture).clearAndSetSemantics { },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                image, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().padding(size * 0.06f).clip(CircleShape),
            )
        }
        return
    }
    Initials(name, avatar, size, modifier.then(gesture))
}

/** How a tap on a person's icon shows it bigger (canvas "Zdjęcia profilowe", board 1 C/D). */
enum class AvatarZoom {
    /** A tap: where the icon only shows the person. */
    TAP,
    /** Holding the finger: where a tap picks the person (it still does). */
    HOLD,
    NONE,
}

/** Calls [action] when the finger stays down; a short tap goes on to what is under it, untouched. */
private fun Modifier.onHold(action: () -> Unit) = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { waitForUpOrCancellation() }
        if (up == null) {
            action()
            // The rest of this touch is ours, so the row under it does not take it as a tap.
            do {
                val event = awaitPointerEvent()
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    }
}

/** The big icon: the whole photo (or big initials), the name; who may, hides the photo in this community. */
@Composable
private fun AvatarZoomDialog(name: String, avatar: Avatar?, photo: String?, onDismiss: () -> Unit) {
    val session = LocalAppSession.current
    val state = session.state.collectAsState()
    val me = (state.value as? SessionState.SignedIn)?.me
    val scope = rememberCoroutineScope()
    var problem by remember { mutableStateOf<String?>(null) }
    val big = rememberAvatarImage(photo, small = false) ?: rememberAvatarImage(photo)
    val owner = photo?.let(AvatarImages::ownerOf)
    val mayHide = photo != null && owner != null && owner != me?.id && me?.permissions?.get(Action.PHOTOS_MODERATE) == Mode.ALLOWED
    LuxuryPopup(onDismiss = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (big != null) {
                Box(Modifier.size(240.dp).clip(CircleShape).background(Luxury.BorderBrush), contentAlignment = Alignment.Center) {
                    Image(big, contentDescription = "Zdjęcie: $name", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().padding(5.dp).clip(CircleShape))
                }
            } else {
                Initials(name, avatar, 200.dp, Modifier)
            }
            Text(name, fontSize = 22.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
            if (photo == null && avatar?.photo != null) Text("zdjęcie ukryte w tej społeczności", fontSize = 12.sp, color = Luxury.TextMuted)
            problem?.let { Text(it, fontSize = 13.sp, color = Color(0xFFE79A86), textAlign = TextAlign.Center) }
            PopupButton("Zamknij", Luxury.Text, Color(0xFF3A3A40), onDismiss)
            if (mayHide) {
                PopupButton("Ukryj zdjęcie w tej społeczności", Color(0xFFE79A86), Color(0xFFE79A86).copy(alpha = 0.6f)) {
                    scope.launch {
                        try {
                            session.api.setPhotoHidden(owner!!, true)
                            AvatarImages.hide(photo!!)
                            onDismiss()
                        } catch (e: ServerError) {
                            problem = e.message
                        }
                    }
                }
                Text(
                    "Tutaj ta osoba będzie na inicjałach; w innych społecznościach zdjęcie zostaje. Przywrócisz je w ustawieniach społeczności.",
                    fontSize = 12.sp, color = Luxury.TextMuted, textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun PopupButton(label: String, color: Color, border: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp))
            .border(1.dp, border, RoundedCornerShape(24.dp)).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = color, fontSize = 15.sp) }
}

@Composable
private fun Initials(name: String, avatar: Avatar?, size: Dp, modifier: Modifier) {
    val shape = (avatar?.shape ?: AvatarShape.CIRCLE).toShape()
    val color = avatar?.color?.let(::parseHex) ?: Luxury.GoldBase
    val initials = avatar?.initials ?: Avatars.initials(name)
    val triangle = avatar?.shape == AvatarShape.TRIANGLE
    val fontScale = when (avatar?.shape) {
        AvatarShape.TRIANGLE, AvatarShape.DIAMOND -> 0.28f
        AvatarShape.STAR -> 0.25f
        else -> 0.36f
    }
    // The shape has a fixed size, so the letters must not grow with the phone's font setting (they got cut off).
    val letters = (size.value * fontScale / LocalDensity.current.fontScale).sp
    Box(
        modifier = modifier.size(size).clip(shape).background(Luxury.BorderBrush).clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(size * 0.06f).clip(shape).background(color),
            contentAlignment = if (triangle) Alignment.BottomCenter else Alignment.Center,
        ) {
            Text(
                initials,
                modifier = if (triangle) Modifier.padding(bottom = size * 0.12f) else Modifier,
                color = Luxury.CardBottom,
                fontWeight = FontWeight.Bold,
                fontSize = letters,
                letterSpacing = 0.3.sp,
                maxLines = 1,
                softWrap = false,
                style = TextStyle(
                    lineHeight = letters,
                    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
                ),
            )
        }
    }
}

private fun AvatarShape.toShape(): Shape = when (this) {
    AvatarShape.CIRCLE -> CircleShape
    AvatarShape.SQUARE -> RoundedCornerShape(22)
    AvatarShape.TRIANGLE -> polygon(listOf(0.5f to 0.02f, 0.98f to 0.94f, 0.02f to 0.94f))
    AvatarShape.DIAMOND -> polygon(listOf(0.5f to 0f, 1f to 0.5f, 0.5f to 1f, 0f to 0.5f))
    AvatarShape.HEXAGON -> polygon(listOf(0.25f to 0.03f, 0.75f to 0.03f, 1f to 0.5f, 0.75f to 0.97f, 0.25f to 0.97f, 0f to 0.5f))
    AvatarShape.STAR -> polygon(
        List(10) { i ->
            val radius = if (i % 2 == 0) 0.5f else 0.21f
            val angle = Math.toRadians(-90.0 + i * 36.0)
            (0.5f + radius * cos(angle).toFloat()) to (0.53f + radius * sin(angle).toFloat())
        },
    )
}

/** A shape from corner points given as fractions of the width and height. */
private fun polygon(points: List<Pair<Float, Float>>) = GenericShape { size, _ ->
    points.forEachIndexed { i, (x, y) ->
        if (i == 0) moveTo(x * size.width, y * size.height) else lineTo(x * size.width, y * size.height)
    }
    close()
}

private fun parseHex(hex: String): Color? =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()
