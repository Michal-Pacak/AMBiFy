package pl.ambif.ambify

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import pl.ambif.ambify.ui.navigation.AppNavHost
import pl.ambif.ambify.ui.theme.AMBiFyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AMBiFyTheme {
                AppNavHost()
            }
        }
    }
}