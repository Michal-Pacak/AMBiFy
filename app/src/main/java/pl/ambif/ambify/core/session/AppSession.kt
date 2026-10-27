package pl.ambif.ambify.core.session

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import pl.ambif.ambify.core.network.ServerApi
import pl.ambif.ambify.core.network.ServerError
import pl.ambif.ambify.core.network.TokenStore
import pl.ambif.ambify.core.settings.ModuleOrderStore
import pl.ambif.ambify.shared.CommunityModules
import pl.ambif.ambify.shared.Me
import pl.ambif.ambify.shared.ModuleOrder
import pl.ambif.ambify.shared.Tokens

sealed interface SessionState {
    data object SignedOut : SessionState
    /**
     * Signed in; [me] is the last known profile (kept on the phone, so it works offline too).
     * `me.communityId == null`: a new account that has no community yet.
     */
    data class SignedIn(val me: Me) : SessionState
}

/**
 * The signed-in person on this phone: tokens, profile and the server connection that uses them.
 * One instance for the whole app (see [LocalAppSession]).
 */
class AppSession(context: Context) {
    private val preferences = context.getSharedPreferences("session", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val moduleOrderStore = ModuleOrderStore(context)

    private val tokenStore = object : TokenStore {
        override var accessToken: String?
            get() = preferences.getString(KEY_ACCESS, null)
            set(value) = preferences.edit { putString(KEY_ACCESS, value) }
        override var refreshToken: String?
            get() = preferences.getString(KEY_REFRESH, null)
            set(value) = preferences.edit { putString(KEY_REFRESH, value) }

        override fun onSessionExpired() = clear()
    }

    val api = ServerApi(tokens = tokenStore, community = { preferences.getString(KEY_COMMUNITY, null) })

    /** Shown on the "My devices" list, e.g. "Galaxy S24 Ultra". */
    val deviceName: String = Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
        ?.takeIf { it.isNotBlank() } ?: Build.MODEL

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    /** Bumped whenever the module order arrives from the server, so the bar reloads it. */
    private val _moduleOrderVersion = MutableStateFlow(0)
    val moduleOrderVersion: StateFlow<Int> = _moduleOrderVersion.asStateFlow()

    /** Modules of the current community; null until the server answered once (the bar then shows the basic ones). */
    private val _modules = MutableStateFlow(cachedModules())
    val modules: StateFlow<CommunityModules?> = _modules.asStateFlow()

    val isSignedIn: Boolean get() = tokenStore.refreshToken != null

    suspend fun signIn(tokens: Tokens) {
        tokenStore.accessToken = tokens.accessToken
        tokenStore.refreshToken = tokens.refreshToken
        refreshProfile()
        refreshCommunity()
    }

    /**
     * Reloads the profile; a network problem keeps the cached one. The server answers for the community
     * kept on the phone, or the first one when that is gone (e.g. the person was removed from it).
     */
    suspend fun refreshProfile() {
        val me = api.me()
        preferences.edit {
            putString(KEY_ME, json.encodeToString(me))
            putString(KEY_COMMUNITY, me.communityId)
        }
        if (isSignedIn) _state.value = SessionState.SignedIn(me)
    }

    /** On start: refresh the profile in the background (a revoked session signs out by itself). */
    fun refreshInBackground() {
        scope.launch {
            runCatching { refreshProfile() }
            refreshCommunity()
        }
    }

    /** Makes [communityId] the current community: profile, modules and module order follow it. */
    suspend fun switchCommunity(communityId: String) {
        preferences.edit { putString(KEY_COMMUNITY, communityId) }
        refreshProfile()
        refreshCommunity()
    }

    suspend fun createCommunity(name: String) = switchCommunity(api.createCommunity(name).id)

    suspend fun joinCommunity(code: String) = switchCommunity(api.joinCommunity(code).id)

    /** After the Owner or an Admin switched a module, so the bar follows at once. */
    fun showModules(modules: CommunityModules) {
        preferences.edit { putString(KEY_MODULES, json.encodeToString(modules)) }
        _modules.value = modules
    }

    private suspend fun refreshCommunity() {
        if ((_state.value as? SessionState.SignedIn)?.me?.communityId == null) return
        runCatching { showModules(api.modules()) }
        runCatching { syncModuleOrder() }
    }

    suspend fun logout() {
        try {
            api.logout()
        } catch (_: ServerError) {
            // Signed out on the phone anyway; the server forgets the device after 90 days.
        }
        clear()
    }

    /** The server keeps the order per person; a person who has none yet gets this phone's order. */
    private suspend fun syncModuleOrder() {
        val remote = api.moduleOrder().routes
        if (remote.isEmpty()) {
            moduleOrderStore.load()?.let { api.saveModuleOrder(ModuleOrder(it)) }
        } else {
            moduleOrderStore.save(remote)
            _moduleOrderVersion.value += 1
        }
    }

    fun saveModuleOrder(order: List<String>) {
        moduleOrderStore.save(order)
        if (isSignedIn) scope.launch { runCatching { api.saveModuleOrder(ModuleOrder(order)) } }
    }

    private fun clear() {
        preferences.edit { clear() }
        _modules.value = null
        _state.value = SessionState.SignedOut
    }

    private fun cachedModules(): CommunityModules? =
        preferences.getString(KEY_MODULES, null)?.let { runCatching { json.decodeFromString<CommunityModules>(it) }.getOrNull() }

    private fun initialState(): SessionState {
        val me = preferences.getString(KEY_ME, null)?.let { runCatching { json.decodeFromString<Me>(it) }.getOrNull() }
        return if (tokenStore.refreshToken != null && me != null) SessionState.SignedIn(me) else SessionState.SignedOut
    }

    private companion object {
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_ME = "me"
        const val KEY_COMMUNITY = "community"
        const val KEY_MODULES = "modules"
    }
}

val LocalAppSession = staticCompositionLocalOf<AppSession> { error("AppSession not provided") }
