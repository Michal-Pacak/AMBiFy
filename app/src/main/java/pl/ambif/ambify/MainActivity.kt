package pl.ambif.ambify

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.edit
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import pl.ambif.ambify.core.session.AppSession
import pl.ambif.ambify.core.session.LocalAppSession
import pl.ambif.ambify.features.update.ForcedUpdateScreen
import pl.ambif.ambify.features.update.InstallPermissionScreen
import pl.ambif.ambify.modules.monitor.MonitorCollector
import pl.ambif.ambify.modules.monitor.MonitorScheduler
import pl.ambif.ambify.modules.update.AppUpdater
import pl.ambif.ambify.modules.update.UpdateRules
import pl.ambif.ambify.modules.update.UpdatedReceiver
import pl.ambif.ambify.modules.update.UpdateState
import pl.ambif.ambify.ui.navigation.AppNavHost
import pl.ambif.ambify.ui.theme.AMBiFyTheme
import pl.ambif.ambify.ui.theme.LocalCardMotion

class MainActivity : ComponentActivity() {
    private val session by lazy { AppSession(applicationContext) }
    private val updater by lazy { AppUpdater.of(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (BuildConfig.TEST_LOGIN) offerHomeScreenShortcut()
        askForLocalNetwork()
        setContent {
            val cardAnimations by session.device.cardAnimations.collectAsState()
            val cardMotion = cardAnimations && !session.device.systemAnimationsOff()
            CompositionLocalProvider(LocalAppSession provides session, LocalCardMotion provides cardMotion) {
                AMBiFyTheme {
                    val update by updater.state.collectAsState()
                    val latest by updater.latest.collectAsState()
                    val forced = latest?.takeIf { UpdateRules.isForced(it) }
                    when {
                        update is UpdateState.NeedsPermission -> InstallPermissionScreen(
                            onOpenSettings = { startActivity(updater.permissionSettings()) },
                            onCancel = updater::cancelPermission,
                        )
                        // This app no longer works with the server: nothing but updating.
                        forced != null -> ForcedUpdateScreen(update, forced, onUpdate = { updater.installNow(forced) })
                        else -> AppNavHost()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Only once really on the screen: after a self-update Android may start the app hidden behind the home screen
        // (no starting itself from the background), and then this notification is the only sign the update is done.
        UpdatedReceiver.clearNotice(this)
        updater.onResume()
        if (BuildConfig.KIDS) runCatching {
            MonitorCollector.collect(applicationContext)
            MonitorScheduler.scheduleNight(applicationContext)
            lifecycleScope.launch { runCatching { MonitorCollector.sync(applicationContext) } }
        }
    }

    /** Android 17+ blocks addresses on the home network (where the AMBiFy server is) without this permission. */
    private fun askForLocalNetwork() {
        if (Build.VERSION.SDK_INT < 37) return
        if (checkSelfPermission(LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED) return
        localNetwork.launch(LOCAL_NETWORK)
    }

    private val localNetwork = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

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

    private companion object {
        const val LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
    }
}
