package pl.ambif.ambify

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.edit
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import pl.ambif.ambify.core.session.AppSession
import pl.ambif.ambify.core.session.LocalAppSession
import pl.ambif.ambify.ui.navigation.AppNavHost
import pl.ambif.ambify.ui.theme.AMBiFyTheme
import pl.ambif.ambify.ui.theme.LocalCardMotion

class MainActivity : ComponentActivity() {
    private val session by lazy { AppSession(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (BuildConfig.TEST_LOGIN) offerHomeScreenShortcut()
        setContent {
            val cardAnimations by session.device.cardAnimations.collectAsState()
            val cardMotion = cardAnimations && !session.device.systemAnimationsOff()
            CompositionLocalProvider(LocalAppSession provides session, LocalCardMotion provides cardMotion) {
                AMBiFyTheme {
                    AppNavHost()
                }
            }
        }
    }

    /**
     * AMBiFy TEST only: asks once to put its icon on the home screen (Samsung adds new apps only to the drawer),
     * and keeps an existing home-screen shortcut's icon up to date.
     */
    private fun offerHomeScreenShortcut() {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return
        val shortcut = ShortcutInfoCompat.Builder(this, "home")
            .setShortLabel("AMBiFy TEST")
            .setIcon(IconCompat.createWithResource(this, R.mipmap.ic_launcher_test))
            .setIntent(launch)
            .build()
        val preferences = getSharedPreferences("test_app", MODE_PRIVATE)
        if (preferences.getBoolean("shortcut_offered", false)) {
            ShortcutManagerCompat.updateShortcuts(this, listOf(shortcut))
            return
        }
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(this)) return
        ShortcutManagerCompat.requestPinShortcut(this, shortcut, null)
        preferences.edit { putBoolean("shortcut_offered", true) }
    }
}
