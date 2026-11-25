package pl.ambif.ambify.core.session

import android.content.Context
import pl.ambif.ambify.modules.timetable.ReminderScheduler
import pl.ambif.ambify.modules.timetable.TimetableCache
import pl.ambif.ambify.shared.ModuleId
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import pl.ambif.ambify.shared.ThemeState
import pl.ambif.ambify.shared.Themes
import pl.ambif.ambify.ui.theme.Luxury
import pl.ambif.ambify.ui.theme.OwnPalette
import pl.ambif.ambify.ui.theme.Palette
import pl.ambif.ambify.ui.theme.Palettes
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import pl.ambif.ambify.core.network.ServerApi
import pl.ambif.ambify.core.network.ServerError
import pl.ambif.ambify.core.network.TokenStore
import pl.ambif.ambify.core.settings.DevicePreferences
import pl.ambif.ambify.core.settings.ModuleOrderStore
import pl.ambif.ambify.shared.CommunityModules
import pl.ambif.ambify.shared.Me
import pl.ambif.ambify.shared.ModuleOrder
import pl.ambif.ambify.shared.ProfileUpdate
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
    private val appContext = context.applicationContext
    private val preferences = context.getSharedPreferences("session", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val moduleOrderStore = ModuleOrderStore(context)

    /** Settings of this phone (card animations). */
    val device = DevicePreferences(context)

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

    init {
        pl.ambif.ambify.ui.components.AvatarImages.init(context.applicationContext, api)
        pl.ambif.ambify.ui.theme.WallpaperImages.init(context.applicationContext, api)
    }

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

    /** 0.7.7: themes of my account and communities; kept on the phone so the right colors show from the start. */
    private val _themes = MutableStateFlow(cachedThemes())
    val themes: StateFlow<ThemeState?> = _themes.asStateFlow()

    init {
        applyTheme()
    }

    /** The theme being edited: while set, the whole app wears it, so every change shows live. */
    private var preview: Palette? = null

    fun previewTheme(palette: Palette?) {
        preview = palette
        applyTheme()
    }

    /** Paints the app in the theme for the current community (or the account's, outside one). */
    fun applyTheme() {
        preview?.let { Luxury.palette = it; return }
        val state = _themes.value
        val community = (_state.value as? SessionState.SignedIn)?.me?.communityId
        if (state == null || !isSignedIn) {
            Luxury.palette = Palettes.LUXURY
            return
        }
        val key = Themes.effective(state, community)
        Luxury.palette = paletteOf(state, key)
    }

    /** The palette of [key]: a ready theme, or an own one (mine or a community's). */
    fun paletteOf(state: ThemeState, key: String): Palette {
        Palettes.of(key)?.let { return it }
        val id = Themes.customId(key) ?: return Palettes.LUXURY
        val own = state.own.firstOrNull { it.id == id } ?: state.communities.flatMap { it.own }.firstOrNull { it.id == id }
        return own?.let { OwnPalette.from(it) } ?: Palettes.LUXURY
    }

    suspend fun refreshThemes() {
        showThemes(api.themes())
    }

    fun showThemes(state: ThemeState) {
        preferences.edit { putString(KEY_THEMES, json.encodeToString(state)) }
        _themes.value = state
        applyTheme()
    }

    suspend fun setAccountTheme(theme: String?) = showThemes(api.setAccountTheme(theme))
    suspend fun setMemberTheme(communityId: String, theme: String?) = showThemes(api.forCommunity(communityId).setMemberTheme(theme))
    suspend fun setCommunityTheme(communityId: String, theme: String, locked: Boolean) =
        showThemes(api.forCommunity(communityId).setCommunityTheme(theme, locked))
    /** An own theme: mine ([communityId] null) or that community's. */
    suspend fun saveOwnTheme(theme: pl.ambif.ambify.shared.OwnTheme, communityId: String?) =
        showThemes((communityId?.let { api.forCommunity(it) } ?: api).saveOwnTheme(theme, community = communityId != null))
    suspend fun deleteOwnTheme(id: String, communityId: String?) = showThemes((communityId?.let { api.forCommunity(it) } ?: api).deleteOwnTheme(id))
    suspend fun uploadWallpaper(jpeg: java.io.File, communityId: String?): String =
        (communityId?.let { api.forCommunity(it) } ?: api).uploadWallpaper(jpeg, community = communityId != null)

    private fun cachedThemes(): ThemeState? =
        preferences.getString(KEY_THEMES, null)?.let { runCatching { json.decodeFromString<ThemeState>(it) }.getOrNull() }

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
        applyTheme()
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

    /** 0.7.6: my new profile photo ([jpeg]: the cropped square), or none. */
    suspend fun setMyPhoto(jpeg: java.io.File?) {
        val me = if (jpeg != null) api.uploadMyPhoto(jpeg) else api.deleteMyPhoto()
        preferences.edit { putString(KEY_ME, json.encodeToString(me)) }
        _state.value = SessionState.SignedIn(me)
    }

    suspend fun updateProfile(update: ProfileUpdate) {
        val me = api.updateProfile(update)
        preferences.edit { putString(KEY_ME, json.encodeToString(me)) }
        _state.value = SessionState.SignedIn(me)
    }

    /** Leaves [communityId]; when it was the current one, the server's next answer picks another (or none). */
    suspend fun leaveCommunity(communityId: String) {
        api.forCommunity(communityId).leaveCommunity()
        if ((_state.value as? SessionState.SignedIn)?.me?.communityId == communityId) {
            preferences.edit { remove(KEY_COMMUNITY) }
        }
        refreshProfile()
        refreshCommunity()
    }

    suspend fun joinCommunity(code: String) = switchCommunity(api.joinCommunity(code).id)

    /** Board 16: the Owner asks someone of [communityId] to take it over ([leave]: then leaves it). */
    suspend fun requestOwnerTransfer(communityId: String, toUserId: String, leave: Boolean) {
        api.forCommunity(communityId).requestOwnerTransfer(toUserId, leave)
        refreshProfile()
    }

    suspend fun cancelOwnerTransfer(communityId: String) {
        api.forCommunity(communityId).cancelOwnerTransfer()
        refreshProfile()
    }

    /** The asked person answers; accepting makes them the Owner (the former one may leave at once). */
    suspend fun answerOwnerTransfer(communityId: String, accept: Boolean) {
        api.forCommunity(communityId).answerOwnerTransfer(accept)
        refreshProfile()
        refreshCommunity()
    }

    /** The Owner alone removes [communityId]; like leaving, the server's next answer picks another (or none). */
    suspend fun deleteCommunity(communityId: String, name: String) {
        api.forCommunity(communityId).deleteCommunity(name)
        if ((_state.value as? SessionState.SignedIn)?.me?.communityId == communityId) {
            preferences.edit { remove(KEY_COMMUNITY) }
        }
        refreshProfile()
        refreshCommunity()
    }

    /** After the Owner or an Admin switched a module, so the bar follows at once. */
    fun showModules(modules: CommunityModules) {
        preferences.edit { putString(KEY_MODULES, json.encodeToString(modules)) }
        _modules.value = modules
    }

    private suspend fun refreshCommunity() {
        runCatching { refreshThemes() }
        if ((_state.value as? SessionState.SignedIn)?.me?.communityId == null) return
        runCatching { showModules(api.modules()) }
        runCatching { syncModuleOrder() }
        refreshReminders()
    }

    /** My reminders of extra activities (Plan lekcji), planned on this phone for this and next week. */
    suspend fun refreshReminders() {
        val me = (_state.value as? SessionState.SignedIn)?.me ?: return
        if (_modules.value?.enabled?.contains(ModuleId.TIMETABLE) != true) return
        runCatching { ReminderScheduler.refresh(appContext, api, me.id) }
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
        TimetableCache.clear(appContext)
        pl.ambif.ambify.features.lists.data.ListRepository.clear(appContext)
        pl.ambif.ambify.features.home.DashboardStore(appContext).clear()
        _modules.value = null
        _themes.value = null
        _state.value = SessionState.SignedOut
        applyTheme()
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
        const val KEY_THEMES = "themes"
    }
}

val LocalAppSession = staticCompositionLocalOf<AppSession> { error("AppSession not provided") }
