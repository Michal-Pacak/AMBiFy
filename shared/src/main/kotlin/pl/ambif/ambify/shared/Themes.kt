package pl.ambif.ambify.shared

import kotlinx.serialization.Serializable

/**
 * 0.7.7 themes (the "Motywy" canvas, approved 25.11). Three ready themes for everyone (Basic), two Premium ones and
 * own themes made in the editor. Premium comes two ways:
 * - **Premium konta** ([ThemeState.accountPremium]): Kod, Gaming, the editor and a wallpaper for oneself, in every
 *   community whose theme is not locked;
 * - **Premium społeczności** (the [Themes.COMMUNITY_GRANT] grant): the Owner (and roles with [Action.THEME_SET]) may
 *   give the community Kod, Gaming or an own community theme with a wallpaper; members may pick Kod/Gaming there.
 * The Owner may lock the community theme: then everyone sees it, people with Premium konta too.
 */
object Themes {
    const val LUXURY = "luxury"
    const val PEARL = "pearl"
    const val NEON = "neon"
    const val CODE = "code"
    const val GAMING = "gaming"

    val BASIC = listOf(LUXURY, PEARL, NEON)
    val PREMIUM = listOf(CODE, GAMING)

    /** The community grant (Platform Admin, later the shop) that gives a community the Premium themes. */
    const val COMMUNITY_GRANT = "THEMES"

    /** An own theme's key: "custom:<id>". */
    fun custom(id: String) = "custom:$id"
    fun customId(key: String?): String? = key?.takeIf { it.startsWith("custom:") }?.removePrefix("custom:")

    const val MAX_OWN = 10
    const val MAX_NAME = 30

    /** Paths: GET/PUT the theme choices, own themes and wallpapers. */
    const val PATH = "/themes"
    const val ACCOUNT = "/themes/account"
    const val MEMBER = "/themes/member"
    const val COMMUNITY = "/themes/community"
    const val OWN = "/themes/own"
    const val WALLPAPERS = "/themes/wallpapers"

    /**
     * The theme to show. [communityId] null = the screens outside a community (login, list of communities, account).
     * Anything not allowed (any more) falls back: an expired Premium brings back the community's theme, a lost
     * community grant brings back Luxury.
     */
    fun effective(state: ThemeState, communityId: String?): String {
        val community = state.communities.firstOrNull { it.communityId == communityId }
        if (community == null) return state.accountTheme?.takeIf { allowedForMe(state, null, it) } ?: LUXURY
        val shared = community.theme.takeIf { allowedForCommunity(community, it) } ?: LUXURY
        // Order (Michał, 25.11): the Owner's lock, then my pick in this community, then my theme, then the community's.
        if (community.locked) return shared
        community.myChoice?.takeIf { allowedForMe(state, community, it) }?.let { return it }
        state.accountTheme?.takeIf { allowedForMe(state, community, it) }?.let { return it }
        return shared
    }

    /** May the community have [key] as its theme? Premium and own community themes need the community grant. */
    fun allowedForCommunity(community: CommunityTheme, key: String): Boolean = when {
        key in BASIC -> true
        key in PREMIUM -> community.premium
        else -> community.premium && community.own.any { custom(it.id) == key }
    }

    /** May I pick [key] for myself (in [community], or outside one when null)? */
    fun allowedForMe(state: ThemeState, community: CommunityTheme?, key: String): Boolean = when {
        key in BASIC -> true
        key in PREMIUM -> state.accountPremium || community?.premium == true
        state.own.any { custom(it.id) == key } -> state.accountPremium
        community != null -> allowedForCommunity(community, key)
        else -> false
    }
}

/** How an own theme's cards are framed. */
@Serializable
enum class BorderStyle { METAL, FLAT, STRIPES, NEON }

/** An own theme's wallpaper behind the cards: a [pattern] in the theme's colors or a [photoId] from the gallery. */
@Serializable
data class Wallpaper(
    val pattern: String? = null,
    val photoId: String? = null,
    /** How much the background color covers it, 40–95 %, so text stays readable. */
    val dim: Int = 80,
    /** The photo's point kept in view (0..1 of its width and height); the screen, upright or turned, is cut around it. */
    val focusX: Float = 0.5f,
    val focusY: Float = 0.5f,
)

/** The patterns an own theme's wallpaper can be drawn with (canvas board 4). */
object WallpaperPatterns {
    val ALL = listOf("diag", "scarf", "vert", "flag", "check", "rhomb", "dots", "zigzag", "tartan")
    const val MAX_BYTES = 8L * 1024 * 1024
    /** The server keeps the photo no longer than this on its long side. */
    const val MAX_SIDE = 2048
}

/**
 * A theme made in the editor: background, up to three colors (hex "#RRGGBB": 1 accent, 2 buttons, 3 extras) and a
 * touch effect ([TouchEffectKey] names: rings, sparks, laser, rain, hit).
 */
@Serializable
data class OwnTheme(
    val id: String,
    val name: String,
    val dark: Boolean = true,
    val bgHue: Int = 225,
    val bgSat: Int = 30,
    val colors: List<String> = listOf("#D4AF37"),
    val style: BorderStyle = BorderStyle.METAL,
    val effect: String = "rings",
    val wallpaper: Wallpaper? = null,
)

/** A community's theme as I see it. */
@Serializable
data class CommunityTheme(
    val communityId: String,
    /** Set by the Owner (or a role with [Action.THEME_SET]); Luxury by default. */
    val theme: String = Themes.LUXURY,
    val locked: Boolean = false,
    /** The community has the Premium themes ([Themes.COMMUNITY_GRANT]). */
    val premium: Boolean = false,
    /** The community's own themes (Premium społeczności). */
    val own: List<OwnTheme> = emptyList(),
    /** My own pick in this community; null = the community's theme. */
    val myChoice: String? = null,
    /** I may set and lock the community's theme. */
    val canSet: Boolean = false,
    /** I may make the community's own themes ([Action.THEME_EDIT]; they also need [premium]). */
    val canEdit: Boolean = false,
    /** Who set the theme (shown when it is locked). */
    val setBy: String? = null,
)

/** Everything about themes for my account, from GET [Themes.PATH]. */
@Serializable
data class ThemeState(
    /** My theme, everywhere in the app (unless a community's pick or lock says otherwise); null = as the community. */
    val accountTheme: String? = null,
    val accountPremium: Boolean = false,
    /** My own themes (Premium konta). */
    val own: List<OwnTheme> = emptyList(),
    val communities: List<CommunityTheme> = emptyList(),
)

@Serializable
data class ThemeChoice(val theme: String?)

@Serializable
data class CommunityThemeUpdate(val theme: String, val locked: Boolean)

/** A wallpaper photo sent with PUT [Themes.WALLPAPERS] (JPEG); the answer's [photoId] goes into a [Wallpaper]. */
@Serializable
data class WallpaperUploaded(val photoId: String)

/** Platform Admin: Premium konta for the account with this e-mail. */
@Serializable
data class AccountPremiumRequest(val email: String, val premium: Boolean)
