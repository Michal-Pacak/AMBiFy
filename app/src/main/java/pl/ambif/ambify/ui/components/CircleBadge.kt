package pl.ambif.ambify.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.ambif.ambify.shared.CircleAvatar
import pl.ambif.ambify.ui.theme.Luxury

/**
 * A circle's icon (0.7.8, canvas board 1 A): a rounded square — people are always round, so the two never mix up.
 * Its photo, or its initials on its color (none = the theme's gold), with a thin gold ring.
 */
@Composable
fun CircleBadge(name: String, circle: CircleAvatar?, size: Dp = 60.dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(size * 0.27f)
    val image = rememberAvatarImage(circle?.photo, small = size <= 96.dp)
    val initials = circle?.initials ?: pl.ambif.ambify.shared.Avatars.initials(name)
    val color = circle?.color?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
    Box(
        modifier = modifier
            .size(size)
            .border(1.5.dp, Luxury.GoldBase, shape)
            .padding(3.dp)
            .clip(shape)
            .then(if (color != null) Modifier.background(color) else Modifier.background(Luxury.GoldFill))
            .semantics { contentDescription = "Krąg $name" },
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        } else {
            Text(
                initials, fontSize = (size.value * 0.36f).sp, fontWeight = FontWeight.Bold,
                color = if (color != null) Color(0xFF14141A) else Luxury.OnGold,
            )
        }
    }
}
