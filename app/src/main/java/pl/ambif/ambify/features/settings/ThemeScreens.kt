package pl.ambif.ambify.features.settings

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.ambif.ambify.core.network.ServerError
import pl.ambif.ambify.core.session.LocalAppSession
import pl.ambif.ambify.features.account.SwitchRow
import pl.ambif.ambify.features.timetable.Chip
import pl.ambif.ambify.features.timetable.FramedButton
import pl.ambif.ambify.features.timetable.GoldButton
import pl.ambif.ambify.shared.BorderStyle
import pl.ambif.ambify.shared.CommunityTheme
import pl.ambif.ambify.shared.OwnTheme
import pl.ambif.ambify.shared.ThemeState
import pl.ambif.ambify.shared.Themes
import pl.ambif.ambify.shared.Wallpaper
import pl.ambif.ambify.shared.WallpaperPatterns
import pl.ambif.ambify.ui.components.MessageBar
import pl.ambif.ambify.ui.components.MessageKind
import pl.ambif.ambify.ui.icons.AppIcons
import pl.ambif.ambify.ui.theme.Luxury
import pl.ambif.ambify.ui.theme.LuxuryCard
import pl.ambif.ambify.ui.theme.LuxuryCardShape
import pl.ambif.ambify.ui.theme.LuxurySectionTitle
import pl.ambif.ambify.ui.theme.OwnPalette
import pl.ambif.ambify.ui.theme.Palette
import pl.ambif.ambify.ui.theme.Palettes
import pl.ambif.ambify.ui.theme.TouchEffect
import pl.ambif.ambify.ui.theme.WallpaperImages
import pl.ambif.ambify.ui.theme.WallpaperView
import pl.ambif.ambify.ui.theme.drawPattern

private const val AS_COMMUNITY = "-"

private val ReadyNames = mapOf(
    Themes.LUXURY to "Luxury", Themes.PEARL to "Perła", Themes.NEON to "Neon", Themes.CODE to "Kod", Themes.GAMING to "Gaming",
)

/** A theme's name: a ready one or an own one (mine or a community's). */
internal fun themeName(state: ThemeState, key: String): String =
    ReadyNames[key] ?: Themes.customId(key)?.let { id -> (state.own + state.communities.flatMap { it.own }).firstOrNull { it.id == id }?.name } ?: "Luxury"

private val EffectNames = mapOf(
    TouchEffect.RINGS to "fale", TouchEffect.SPARKS to "iskry", TouchEffect.LASER to "laser", TouchEffect.RAIN to "spadające znaki", TouchEffect.HIT to "trafienie",
)

/** A theme's tile: its background, its button and its border, as on the canvas (board 2). */
@Composable
private fun ThemeTile(palette: Palette, name: String, selected: Boolean, locked: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier.clickable(role = Role.RadioButton, onClickLabel = "Wybierz motyw $name", onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val shape = RoundedCornerShape(14.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .then(if (selected) Modifier.border(2.5.dp, Luxury.GoldLight, RoundedCornerShape(17.dp)).padding(4.dp) else Modifier)
                .clip(shape)
                .background(palette.background)
                .border(1.dp, palette.borderBrush, shape),
        ) {
            // On the small tile the wallpaper is dimmed less, so its pattern is recognisable.
            palette.wallpaper?.let { WallpaperView(it.copy(dim = minOf(it.dim, 40)), palette) }
            Box(
                modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp).fillMaxWidth().height(14.dp)
                    .clip(RoundedCornerShape(7.dp)).background(palette.buttonBrush),
            )
            if (locked) {
                Box(modifier = Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                    Icon(AppIcons.Lock, contentDescription = "Premium", tint = Luxury.GoldLight, modifier = Modifier.size(20.dp))
                }
            }
        }
        Text(name, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 2, lineHeight = 16.sp, color = if (selected) Luxury.GoldText else Luxury.Text)
    }
}

private class TileItem(val key: String, val name: String, val palette: Palette, val locked: Boolean)

@Composable
private fun TileGrid(items: List<TileItem>, selected: String?, onPick: (TileItem) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { item ->
                    ThemeTile(item.palette, item.name, item.key == selected, item.locked, Modifier.weight(1f)) { onPick(item) }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Board 2 A: Ustawienia → Wygląd — the account's theme (screens outside a community) and my own themes. */
@Composable
fun AccountThemeScreen(onBack: () -> Unit, onEdit: (themeId: String?) -> Unit) {
    val session = LocalAppSession.current
    val scope = rememberCoroutineScope()
    val themes by session.themes.collectAsState()
    var notice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { runCatching { session.refreshThemes() } }

    SettingsLayout(title = "Wygląd", onBack = onBack, notice = notice) {
        val state = themes ?: run {
            Text("Wczytuję motywy…", color = Luxury.TextMuted)
            return@SettingsLayout
        }
        LuxuryCard {
            LuxurySectionTitle("Mój motyw")
            Text("W całej aplikacji. W wybranej społeczności możesz mieć inny (Społeczności → Wygląd). Gdy Owner zablokuje motyw, widzisz motyw społeczności.", fontSize = 13.sp, color = Luxury.TextMuted, lineHeight = 18.sp)
            val current = state.communities.firstOrNull { it.communityId == (session.state.value as? pl.ambif.ambify.core.session.SessionState.SignedIn)?.me?.communityId }
            val asCommunity = current?.let { session.paletteOf(state, it.theme) } ?: Palettes.LUXURY
            val items = listOf(TileItem(AS_COMMUNITY, "Jak w społeczności", asCommunity, locked = false)) + (Themes.BASIC + Themes.PREMIUM).map { key ->
                TileItem(key, ReadyNames.getValue(key), Palettes.of(key)!!, locked = !Themes.allowedForMe(state, null, key))
            } + state.own.map { TileItem(Themes.custom(it.id), it.name, OwnPalette.from(it), locked = !state.accountPremium) }
            TileGrid(items, state.accountTheme ?: AS_COMMUNITY) { item ->
                if (item.locked) notice = "„${item.name}” jest w Premium konta."
                else scope.launch {
                    try { session.setAccountTheme(item.key.takeIf { it != AS_COMMUNITY }); notice = null } catch (e: ServerError) { notice = e.message }
                }
            }
            val me = (session.state.value as? pl.ambif.ambify.core.session.SessionState.SignedIn)?.me
            val communityName = me?.familyName ?: "tej społeczności"
            if (current?.locked == true) {
                MessageBar(MessageKind.STATUS, "W „$communityName” motyw jest zablokowany", "Owner ustalił: ${themeName(state, current.theme)}. Twój motyw widać w społecznościach bez blokady.")
            } else if (current?.myChoice != null) {
                // A pick in the community wins over my theme: say so, and let it go with one tap (Michał, 25.11).
                MessageBar(
                    MessageKind.INFO, "W „$communityName” masz osobno: ${themeName(state, current.myChoice!!)}",
                    "Wybór w społeczności ma pierwszeństwo przed Twoim motywem.",
                )
                FramedButton("Używaj tam mojego motywu", modifier = Modifier.fillMaxWidth()) {
                    scope.launch { try { session.setMemberTheme(current.communityId, null) } catch (e: ServerError) { notice = e.message } }
                }
            }
            Text(
                "Efekt dotknięcia przychodzi z motywem: Luxury — fale, Perła — iskry, Neon — laser, Kod — spadające znaki, Gaming — trafienie.",
                fontSize = 12.sp, color = Luxury.TextMuted, lineHeight = 17.sp,
            )
        }
        LuxuryCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LuxurySectionTitle("Własne motywy")
                PremiumBadge()
            }
            if (state.accountPremium) {
                state.own.forEach { own ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onEdit(own.id) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(own.name, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Text("Edytuj", color = Luxury.GoldText, fontSize = 14.sp)
                    }
                }
                if (state.own.size < Themes.MAX_OWN) FramedButton("+ Nowy motyw", modifier = Modifier.fillMaxWidth()) { onEdit(null) }
            } else {
                Text("Kod i Gaming, edytor własnych motywów (do 3 kolorów) i tapeta. Działa w każdej społeczności, w której Owner nie zablokował motywu.", fontSize = 13.sp, color = Luxury.TextMuted, lineHeight = 18.sp)
                MessageBar(MessageKind.INFO, "Premium konta", "Do czasu płatności przyznaje je Owner platformy.")
            }
        }
    }
}

@Composable
private fun PremiumBadge(text: String = "PREMIUM") {
    Box(modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(Luxury.GoldFill).padding(horizontal = 7.dp, vertical = 2.dp)) {
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = Luxury.OnGold)
    }
}

/** Boards 2 B–D: a community's look — my own pick there, and for the Owner the community's theme and its lock. */
@Composable
fun CommunityThemeScreen(communityId: String, onBack: () -> Unit, onEdit: (themeId: String?) -> Unit) {
    val session = LocalAppSession.current
    val scope = rememberCoroutineScope()
    val themes by session.themes.collectAsState()
    var notice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(communityId) { runCatching { session.refreshThemes() } }

    fun act(block: suspend () -> Unit) {
        scope.launch {
            try { block(); notice = null } catch (e: ServerError) { notice = e.message }
        }
    }

    SettingsLayout(title = "Wygląd", onBack = onBack, notice = notice) {
        val state = themes ?: return@SettingsLayout
        val community = state.communities.firstOrNull { it.communityId == communityId } ?: return@SettingsLayout
        val shared = themeName(state, community.theme)
        if (community.locked) {
            MessageBar(
                MessageKind.STATUS, "Motyw ustalił ${community.setBy ?: "Owner"}: $shared",
                if (community.myChoice != null) "Twój wybór („${themeName(state, community.myChoice!!)}”) wróci, gdy motyw zostanie odblokowany." else "Wszyscy tutaj widzą ten sam motyw.",
            )
        } else {
            MessageBar(MessageKind.INFO, "Motyw społeczności: $shared", "Możesz mieć tu inny — widzisz go tylko Ty.")
        }
        MyPick(state, community, enabled = !community.locked) { key -> act { session.setMemberTheme(communityId, key) } }
        if (community.canSet) CommunityPick(state, community, onPick = { key, locked -> act { session.setCommunityTheme(communityId, key, locked) } }, onEdit = onEdit)
    }
}

@Composable
private fun MyPick(state: ThemeState, community: CommunityTheme, enabled: Boolean, onPick: (String?) -> Unit) {
    LuxuryCard(modifier = if (enabled) Modifier else Modifier.graphicsLayer { alpha = 0.45f }) {
        LuxurySectionTitle("Mój motyw w tej społeczności")
        val options = buildList {
            add(null to (state.accountTheme?.takeIf { Themes.allowedForMe(state, community, it) }?.let { "Jak mój motyw (${themeName(state, it)})" }
                ?: "Motyw społeczności (${themeName(state, community.theme)})"))
            (Themes.BASIC + Themes.PREMIUM).forEach { add(it to ReadyNames.getValue(it)) }
            state.own.forEach { add(Themes.custom(it.id) to it.name) }
        }
        options.forEach { (key, label) ->
            val allowed = key == null || Themes.allowedForMe(state, community, key)
            val selected = community.myChoice == key
            Row(
                modifier = Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = enabled && allowed, role = Role.RadioButton) { onPick(key) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier.size(20.dp).border(2.dp, Luxury.GoldShadow, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(Luxury.GoldLight)) }
                val palette = key?.let { session(state, it) }
                if (palette != null) Box(Modifier.size(22.dp).clip(CircleShape).drawBehind {
                    drawRect(palette.background)
                    drawCircle(palette.accent, size.minDimension / 2f, center.copy(x = size.width))
                })
                Text(label, fontSize = 15.sp, color = if (allowed) Luxury.Text else Luxury.Faint, modifier = Modifier.weight(1f))
                if (!allowed) Text("PREMIUM KONTA", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Luxury.GoldText, letterSpacing = 1.sp)
            }
        }
        if (!state.accountPremium && !community.premium) {
            Text("Kod, Gaming i własne motywy — z Premium konta.", fontSize = 12.sp, color = Luxury.TextMuted)
        }
    }
}

private fun session(state: ThemeState, key: String): Palette? =
    Palettes.of(key) ?: Themes.customId(key)?.let { id -> (state.own + state.communities.flatMap { it.own }).firstOrNull { it.id == id } }?.let { OwnPalette.from(it) }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CommunityPick(state: ThemeState, community: CommunityTheme, onPick: (String, Boolean) -> Unit, onEdit: (String?) -> Unit) {
    LuxuryCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LuxurySectionTitle("Motyw społeczności")
            if (community.premium) PremiumBadge("PREMIUM")
        }
        Text("Dla wszystkich, którzy nie wybrali u siebie innego.", fontSize = 13.sp, color = Luxury.TextMuted)
        val items = (Themes.BASIC + Themes.PREMIUM).map { key ->
            TileItem(key, ReadyNames.getValue(key), Palettes.of(key)!!, locked = !Themes.allowedForCommunity(community, key))
        } + community.own.map { TileItem(Themes.custom(it.id), it.name, OwnPalette.from(it), locked = false) }
        TileGrid(items, community.theme) { item -> if (!item.locked) onPick(item.key, community.locked) }
        if (community.premium && community.canEdit) {
            community.own.forEach { own ->
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onEdit(own.id) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(own.name, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Text("Edytuj", color = Luxury.GoldText, fontSize = 14.sp)
                }
            }
            if (community.own.size < Themes.MAX_OWN) FramedButton("+ Nowy motyw społeczności", modifier = Modifier.fillMaxWidth()) { onEdit(null) }
        }
        SwitchRow(
            label = "Zablokuj motyw",
            checked = community.locked,
            enabled = true,
            below = { Text("wszyscy widzą motyw społeczności, także osoby z Premium konta", fontSize = 12.sp, color = Luxury.TextMuted) },
        ) { onPick(community.theme, it) }
        if (!community.premium) {
            Text("Społeczność bez Premium: Luxury, Perła i Neon. Kod, Gaming, własne motywy i tapeta — z Premium społeczności.", fontSize = 12.sp, color = Luxury.TextMuted, lineHeight = 17.sp)
        }
    }
}

private val PaletteColors = listOf(
    "Biały" to "#F7F7F7", "Srebrny" to "#B4BCC6", "Szary" to "#737373", "Czarny" to "#141414", "Złoty" to "#D4AF37", "Miedziany" to "#C27A47",
    "Żółty" to "#F5C400", "Pomarańczowy" to "#FF7A00", "Czerwony" to "#D7142A", "Bordowy" to "#821733", "Różowy" to "#FF4FA3", "Fioletowy" to "#8A4CF0",
    "Granatowy" to "#1B3580", "Niebieski" to "#0A84FF", "Błękitny" to "#4FC3F0", "Turkusowy" to "#00B8B8", "Zielony" to "#00733E", "Jasnozielony" to "#62C02A",
)

private val PatternNames = mapOf(
    "diag" to "Ukośne pasy", "scarf" to "Szalik", "vert" to "Pionowe pasy", "flag" to "Flaga", "check" to "Szachownica",
    "rhomb" to "Romby", "dots" to "Kropki", "zigzag" to "Zygzak", "tartan" to "Krata",
)

/**
 * Board 4: the editor (Premium). Up to three colors, the border style, a wallpaper (a pattern or a photo) and the
 * touch effect; the whole app wears the edited theme live. [communityId] null = my own theme.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemeEditorScreen(communityId: String?, themeId: String?, onBack: () -> Unit) {
    val session = LocalAppSession.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val themes by session.themes.collectAsState()
    val existing = themes?.let { state ->
        (if (communityId == null) state.own else state.communities.firstOrNull { it.communityId == communityId }?.own.orEmpty())
            .firstOrNull { it.id == themeId }
    }
    var theme by remember(existing?.id) {
        mutableStateOf(existing ?: OwnTheme(id = UUID.randomUUID().toString(), name = "", colors = listOf("#D4AF37", "#C9A227", "#821733")))
    }
    var slot by remember { mutableStateOf(0) }
    var notice by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var tried by remember { mutableStateOf(false) }
    val palette = remember(theme) { OwnPalette.from(theme) }
    // The whole screen (and the app behind it) wears the theme being edited; leaving restores the real one.
    LaunchedEffect(palette) { session.previewTheme(palette) }
    DisposableEffect(Unit) { onDispose { session.previewTheme(null) } }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            try {
                val jpeg = withContext(Dispatchers.IO) {
                    val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                        val long = maxOf(info.size.width, info.size.height)
                        if (long > WallpaperPatterns.MAX_SIDE) {
                            val k = WallpaperPatterns.MAX_SIDE.toFloat() / long
                            decoder.setTargetSize((info.size.width * k).toInt(), (info.size.height * k).toInt())
                        }
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                    File(context.cacheDir, "wallpaper-${UUID.randomUUID()}.jpg").also { file ->
                        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
                    }
                }
                val photoId = session.uploadWallpaper(jpeg, communityId)
                WallpaperImages.keep(photoId, jpeg)
                jpeg.delete()
                theme = theme.copy(wallpaper = Wallpaper(photoId = photoId, dim = theme.wallpaper?.dim ?: 78))
            } catch (e: ServerError) {
                notice = e.message
            } catch (e: Exception) {
                notice = "Nie udało się wczytać zdjęcia."
            } finally {
                busy = false
            }
        }
    }

    SettingsLayout(title = if (existing == null) "Nowy motyw" else "Edytuj motyw", onBack = onBack, subtitle = if (communityId != null) "motyw społeczności" else "", notice = notice) {
        LuxuryCard {
            OutlinedTextField(
                value = theme.name, onValueChange = { theme = theme.copy(name = it.take(Themes.MAX_NAME)) },
                label = { Text("Nazwa motywu") }, singleLine = true, isError = tried && theme.name.isBlank(), modifier = Modifier.fillMaxWidth(),
            )
            LuxurySectionTitle("Tło")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Ciemne", theme.dark) { theme = theme.copy(dark = true) }
                Chip("Jasne", !theme.dark) { theme = theme.copy(dark = false) }
            }
            // The tracks show the colors the background takes (a bit brighter or darker, so the hues tell apart).
            val light = if (theme.dark) 0.26f else 0.84f
            val rainbow = (0..12).map { OwnPalette.hsl(it * 30f, 0.65f, light) }
            LabeledSlider("Odcień tła", "${theme.bgHue}°", theme.bgHue.toFloat(), 0f..359f, track = Brush.horizontalGradient(rainbow)) { theme = theme.copy(bgHue = it.toInt()) }
            val sat = Brush.horizontalGradient(listOf(OwnPalette.hsl(theme.bgHue.toFloat(), 0f, light), OwnPalette.hsl(theme.bgHue.toFloat(), 0.65f, light)))
            LabeledSlider("Ile koloru w tle", "${theme.bgSat}%", theme.bgSat.toFloat(), 0f..100f, track = sat) { theme = theme.copy(bgSat = it.toInt()) }
        }
        LuxuryCard {
            LuxurySectionTitle("Kolory · do trzech")
            val roles = listOf("1 · Akcent" to "nagłówki, obwódki", "2 · Przyciski" to "przyciski, przełączniki", "3 · Dodatki" to "Pasy, Neon, wzory tapety")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                roles.forEachIndexed { i, (label, role) ->
                    val hex = theme.colors.getOrNull(i)
                    Column(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                            .border(if (slot == i) 2.dp else 1.dp, if (slot == i) Luxury.GoldLight else Luxury.Outline, RoundedCornerShape(14.dp))
                            .clickable(role = Role.Tab) { if (i <= theme.colors.size) slot = i }.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            Modifier.size(36.dp).clip(CircleShape)
                                .background(hex?.let { OwnPalette.parse(it) } ?: Luxury.Surface)
                                .border(1.dp, Luxury.Outline, CircleShape),
                        )
                        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text(if (hex == null && i > 0) "brak — jak kolor 1" else role, fontSize = 10.sp, color = Luxury.TextMuted, textAlign = TextAlign.Center, lineHeight = 13.sp)
                    }
                }
            }
            Text("Wybierz pole wyżej, potem kolor:", fontSize = 12.sp, color = Luxury.TextMuted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PaletteColors.forEach { (name, hex) ->
                    val on = theme.colors.getOrNull(slot).equals(hex, ignoreCase = true)
                    Box(
                        modifier = Modifier.size(34.dp)
                            .then(if (on) Modifier.border(2.dp, Luxury.GoldLight, CircleShape).padding(3.dp) else Modifier)
                            .clip(CircleShape).background(OwnPalette.parse(hex)!!).border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                            .clickable(onClickLabel = name) {
                                val colors = theme.colors.toMutableList()
                                if (slot < colors.size) colors[slot] = hex else colors.add(hex)
                                theme = theme.copy(colors = colors)
                            }
                            .semantics { contentDescription = name },
                    )
                }
            }
            if (slot == 2 && theme.colors.size == 3) FramedButton("Bez trzeciego koloru", gold = false) { theme = theme.copy(colors = theme.colors.take(2)); slot = 1 }
            if (slot == 1 && theme.colors.size == 2) FramedButton("Bez drugiego koloru", gold = false) { theme = theme.copy(colors = theme.colors.take(1)); slot = 0 }
            if (slot == 2 && theme.colors.size == 3 && theme.style !in setOf(BorderStyle.STRIPES, BorderStyle.NEON) && theme.wallpaper?.pattern == null) {
                Text("Kolor 3 widać w stylu obwódek Pasy albo Neon i we wzorze tapety — wybierz jedno z nich niżej.", fontSize = 12.sp, color = Luxury.TextMuted)
            }
            val fixed = palette.accentText != OwnPalette.parse(theme.colors[0])
            if (fixed) MessageBar(MessageKind.STATUS, "Poprawiono czytelność", "Kolor 1 w napisach jest trochę jaśniejszy albo ciemniejszy, inaczej byłby nieczytelny na tym tle.")
        }
        LuxuryCard {
            LuxurySectionTitle("Styl obwódek")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(BorderStyle.METAL to "Metal", BorderStyle.FLAT to "Płaski", BorderStyle.STRIPES to "Pasy", BorderStyle.NEON to "Neon").forEach { (style, label) ->
                    Chip(label, theme.style == style) { theme = theme.copy(style = style) }
                }
            }
            LuxurySectionTitle("Efekt dotknięcia")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("rings" to "Fale", "sparks" to "Iskry", "laser" to "Laser", "rain" to "Spadające znaki", "hit" to "Trafienie").forEach { (key, label) ->
                    Chip(label, theme.effect == key) { theme = theme.copy(effect = key) }
                }
            }
        }
        LuxuryCard {
            LuxurySectionTitle("Tapeta")
            val wall = theme.wallpaper
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Brak", wall == null) { theme = theme.copy(wallpaper = null) }
                Chip("Wzór z kolorów", wall?.pattern != null) { theme = theme.copy(wallpaper = Wallpaper(pattern = wall?.pattern ?: "scarf", dim = wall?.dim ?: 82)) }
                Chip("Zdjęcie", wall?.photoId != null, enabled = !busy) { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            }
            if (busy) Text("Wysyłam zdjęcie…", fontSize = 13.sp, color = Luxury.TextMuted)
            if (wall?.pattern != null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    WallpaperPatterns.ALL.forEach { key ->
                        val on = wall.pattern == key
                        Column(
                            modifier = Modifier.width(92.dp).clip(RoundedCornerShape(12.dp))
                                .border(if (on) 2.dp else 1.dp, if (on) Luxury.GoldLight else Luxury.Outline, RoundedCornerShape(12.dp))
                                .clickable { theme = theme.copy(wallpaper = wall.copy(pattern = key)) }.padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(8.dp)).drawBehind { drawPattern(key, palette.trio) })
                            Text(PatternNames.getValue(key), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
            if (wall?.photoId != null) {
                LabeledSlider("Kadr: w poziomie", "", wall.focusX, 0f..1f) { theme = theme.copy(wallpaper = wall.copy(focusX = it)) }
                LabeledSlider("Kadr: w pionie", "", wall.focusY, 0f..1f) { theme = theme.copy(wallpaper = wall.copy(focusY = it)) }
                Text("Pionowo i poziomo ekran jest wycinany wokół tego samego miejsca zdjęcia.", fontSize = 12.sp, color = Luxury.TextMuted)
            }
            if (wall != null) LabeledSlider("Przyciemnienie tapety", "${wall.dim}%", wall.dim.toFloat(), 40f..95f) { theme = theme.copy(wallpaper = wall.copy(dim = it.toInt())) }
        }
        GoldButton(if (busy) "Chwilę…" else "Zapisz motyw", enabled = !busy) {
            tried = true
            if (theme.name.isBlank()) {
                notice = "Wpisz nazwę motywu."
                return@GoldButton
            }
            busy = true
            scope.launch {
                try {
                    session.saveOwnTheme(theme, communityId)
                    onBack()
                } catch (e: ServerError) {
                    notice = e.message
                } finally {
                    busy = false
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FramedButton("Anuluj", gold = false, modifier = Modifier.weight(1f), onClick = onBack)
            if (existing != null) FramedButton("Usuń motyw", gold = false, modifier = Modifier.weight(1f)) {
                scope.launch {
                    try {
                        session.deleteOwnTheme(existing.id, communityId)
                        onBack()
                    } catch (e: ServerError) {
                        notice = e.message
                    }
                }
            }
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String, value: String, current: Float, range: ClosedFloatingPointRange<Float>, track: Brush? = null, onChange: (Float) -> Unit,
) {
    Column {
        Row {
            Text(label, fontSize = 13.sp, color = Luxury.TextMuted, modifier = Modifier.weight(1f))
            Text(value, fontSize = 13.sp, color = Luxury.TextMuted)
        }
        if (track == null) {
            Slider(
                value = current, onValueChange = onChange, valueRange = range,
                colors = SliderDefaults.colors(thumbColor = Luxury.GoldText, activeTrackColor = Luxury.GoldText, inactiveTrackColor = Luxury.Outline),
            )
        } else {
            // A painted track under a slider with see-through tracks.
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 4.dp).height(12.dp).clip(RoundedCornerShape(6.dp)).background(track).border(1.dp, Luxury.Outline, RoundedCornerShape(6.dp)))
                Slider(
                    value = current, onValueChange = onChange, valueRange = range,
                    colors = SliderDefaults.colors(
                        thumbColor = Luxury.GoldText, activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent,
                        activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent,
                    ),
                )
            }
        }
    }
}

