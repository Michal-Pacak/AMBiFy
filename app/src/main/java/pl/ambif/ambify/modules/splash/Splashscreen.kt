package pl.ambif.ambify.modules.splash

import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import pl.ambif.ambify.ui.theme.AMBiFyTheme
import pl.ambif.ambify.ui.theme.SplashDimensions
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.res.painterResource
import pl.ambif.ambify.R
import pl.ambif.ambify.ui.theme.SplashTitleStyle
import androidx.compose.ui.layout.ContentScale
import pl.ambif.ambify.ui.theme.SplashMottoStyle
import pl.ambif.ambify.ui.theme.SplashPoweredByStyle


@Composable
fun SplashScreen() {

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),

        contentAlignment = Alignment.TopCenter
    ) {
        Column(    modifier = Modifier
            .padding(top = 135.dp),
            horizontalAlignment = Alignment.CenterHorizontally)

        {
            Image(
                painter = painterResource(id = R.drawable.ambify_symbol),
                contentDescription = "AMBiFy Logo",
                modifier = Modifier
                    .offset(x = SplashDimensions.LogoHorizontalOffset)
                    .size(SplashDimensions.LogoSize)

            )
             Spacer(modifier = Modifier.height(SplashDimensions.LogoTitleSpacing))
            Text(
                text = "AMBiFy",
                style = SplashTitleStyle,
                color = Color.White

            )
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Achieve. Manage. Balance.",
                color = Color(0xFF9A8452),
                style = SplashMottoStyle
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "Powered by",
                color = Color(0xFFB89A52),
                style = SplashPoweredByStyle
            )
            Image(
                painter = painterResource(id = R.drawable.ambif_logo),
                contentDescription = "AMBiF Logo",
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SplashScreenPreview() {
    AMBiFyTheme {
        SplashScreen()
    }
}