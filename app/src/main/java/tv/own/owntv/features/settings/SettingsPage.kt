package tv.own.owntv.features.settings

import androidx.annotation.StringRes
import tv.own.owntv.R
import tv.own.owntv.features.shell.components.SettingsGroup

/**
 * Every page Settings can push, with the group it belongs to and a stable slug. This is the
 * counterpart of the reference project's `SettingsLeaf`: a page's group is data, not the position it
 * happens to have in a hand-written list, so a search result or a Quick pin can name the group it
 * should open without a second mapping table.
 *
 * [legacyKey] is the row key the page has always used (`tab_LANGUAGE`, …). Preference keys and the
 * focus restore that finds a row by key must not change, so the slug and the key are deliberately
 * separate: slugs can be renamed, keys cannot.
 */
enum class SettingsPage(
    val group: SettingsGroup,
    val slug: String,
    @StringRes val titleRes: Int,
) {
    /** The root page; it holds no rows of its own and is never pushed. */
    ROOT(SettingsGroup.QUICK, "root", R.string.common_nav_settings),
    LANGUAGE(SettingsGroup.APP, "language", R.string.settings_language),
    PROFILES(SettingsGroup.APP, "profiles", R.string.profiles_title),
    SPORTS(SettingsGroup.APP, "sports", R.string.sports_title),
    NETWORK(SettingsGroup.APP, "proxy", R.string.common_proxy),
    DNS(SettingsGroup.APP, "dns", R.string.settings_dns),
    SOURCES(SettingsGroup.SOURCES, "playlists", R.string.settings_playlists),
    EPG(SettingsGroup.SOURCES, "epg", R.string.settings_epg_sources),
    CUSTOMIZE(SettingsGroup.SOURCES, "customize", R.string.settings_customize),
    METADATA(SettingsGroup.SOURCES, "metadata", R.string.settings_metadata),
    OPEN_SUBTITLES(SettingsGroup.SOUND, "opensubtitles", R.string.settings_open_subtitles),
    SUBTITLE_STYLE(SettingsGroup.SOUND, "subtitles", R.string.settings_subtitle_appearance),
    GLASS_EFFECT(SettingsGroup.APPEARANCE, "glass", R.string.settings_glass_bg_title),
    FONTS(SettingsGroup.APPEARANCE, "fonts", R.string.settings_font_customization),
    HOME(SettingsGroup.APPEARANCE, "home", R.string.settings_home_root),
    BROWSING(SettingsGroup.APPEARANCE, "browsing", R.string.settings_browsing_lists),
    CONTENT_MENUS(SettingsGroup.APPEARANCE, "menus", R.string.settings_content_menus_title),
    CH_NAV(SettingsGroup.APPEARANCE, "remote", R.string.settings_ch_paging),
    PANEL_WIDTH(SettingsGroup.APPEARANCE, "panel-width", R.string.settings_panel_width),
    GUIDE_WIDTH(SettingsGroup.APPEARANCE, "guide-width", R.string.settings_guide_width),
    ;

    /** Where the page lives: `settings/<group>/<page>`. */
    val route: String get() = "${group.route}/$slug"

    /** The key the page's row has always carried; Quick pins and focus restore depend on it. */
    val legacyKey: String get() = "tab_$name"

    companion object {
        /** The page a route opens, or null when it names none (an old deep link, say). */
        fun fromRoute(route: String?): SettingsPage? = entries.firstOrNull { it.route == route }
    }
}