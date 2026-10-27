package pl.ambif.ambify.shared

import kotlinx.serialization.Serializable

/** BASIC comes with every community; PREMIUM is paid per community (for now granted only by the Platform Admin). */
@Serializable
enum class Tier { BASIC, PREMIUM }

/**
 * The module catalog. Basic modules are the light, text-only ones (little space and server work);
 * [route] is the app's navigation route of the module screen. [label] is the one name of the module
 * everywhere in the app, the module bar too, so it follows the bar label rule (docs/03_Naming_Convention.md).
 */
@Serializable
enum class ModuleId(val route: String, val tier: Tier, val label: String) {
    CALENDAR("calendar", Tier.BASIC, "Kalendarz"),
    LISTS("lists", Tier.BASIC, "Listy"),
    COMMUNICATION("communication", Tier.BASIC, "Czat"),
    TIMETABLE("timetable", Tier.PREMIUM, "Plan lekcji"),
    SCHOOL("school", Tier.PREMIUM, "Szkoła"),
    MISSIONS("missions", Tier.PREMIUM, "Misje"),
    ASSISTANT("assistant", Tier.PREMIUM, "Asystent AI");

    companion object {
        fun byRoute(route: String): ModuleId? = entries.firstOrNull { it.route == route }
    }
}

/**
 * Premium extras inside a module; a feature works only while its [module] is available too.
 * [label] is shown under its module; [fullLabel] where features of all modules are listed together.
 */
@Serializable
enum class Feature(val module: ModuleId, val label: String) {
    LISTS_REMINDERS(ModuleId.LISTS, "Przypomnienia"),
    LISTS_MEAL_PLAN(ModuleId.LISTS, "Zakupy z posiłków"),
    LISTS_TO_MISSIONS(ModuleId.LISTS, "Przenoszenie do Misji"),
    COMMUNICATION_ATTACHMENTS(ModuleId.COMMUNICATION, "Załączniki"),
    ;

    val fullLabel: String get() = "${module.label}: $label"

    companion object {
        fun of(module: ModuleId): List<Feature> = entries.filter { it.module == module }
    }
}

/**
 * What a community may use. A grant is the name of a premium [ModuleId] or of a [Feature];
 * a module the community has can still be switched off by its Owner or Admin.
 */
object Entitlements {
    val grantKeys: List<String> =
        ModuleId.entries.filter { it.tier == Tier.PREMIUM }.map { it.name } + Feature.entries.map { it.name }

    fun owned(grants: Set<String>): List<ModuleId> =
        ModuleId.entries.filter { it.tier == Tier.BASIC || it.name in grants }

    fun enabled(grants: Set<String>, disabled: Set<ModuleId>): List<ModuleId> = owned(grants) - disabled

    /** Features the community has, whether their module is on or not. */
    fun ownedFeatures(grants: Set<String>): List<Feature> = Feature.entries.filter { it.name in grants }

    /** Owned features that work now: their module is switched on. */
    fun features(grants: Set<String>, disabled: Set<ModuleId>): List<Feature> {
        val modules = enabled(grants, disabled)
        return ownedFeatures(grants).filter { it.module in modules }
    }
}

object ModulePaths {
    /** Modules of the current community (see [ApiHeaders.COMMUNITY]). */
    const val MODULES = "/family/modules"
}

@Serializable
data class CommunityModules(
    /** Basic modules plus granted premium ones. */
    val owned: List<ModuleId>,
    /** Owned and not switched off: the module bar shows these. */
    val enabled: List<ModuleId>,
    /** Owned features whose module is on: the ones that work now. */
    val features: List<Feature>,
    /** Every feature the community has, also while its module is switched off. */
    val ownedFeatures: List<Feature> = emptyList(),
)

@Serializable
data class ModuleSwitch(val enabled: Boolean)
