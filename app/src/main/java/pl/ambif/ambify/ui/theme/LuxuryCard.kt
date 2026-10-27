package pl.ambif.ambify.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val LuxuryCardShape = RoundedCornerShape(20.dp)

/** Graphite card with a gold gradient border and a shadow under it, so it stands out of the background. */
@Composable
fun LuxuryCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    spacing: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(14.dp, LuxuryCardShape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(LuxuryCardShape)
            .background(Luxury.CardBrush)
            .border(1.dp, Luxury.BorderBrush, LuxuryCardShape)
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

/** Card section title: small gold capitals ("BASIC · W CENIE"). */
@Composable
fun LuxurySectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        color = Luxury.GoldText,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.6.sp,
    )
}

/** Thin gold line between rows of a card. */
@Composable
fun LuxuryDivider() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Luxury.Divider))
}
