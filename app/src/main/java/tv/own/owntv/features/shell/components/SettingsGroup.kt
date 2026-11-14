package tv.own.owntv.features.shell.components

import tv.own.owntv.R
import tv.own.owntv.features.settings.VideoGroup

/**
 * The eight Settings groups, in the order of More › Settings' cards — a card is a stable group, not
 * a position, so a card survives a group being added or removed. [video] marks the four groups whose
 * rows are the Video player's; Player now holds the old Picture group's rows as its first section.
 */
enum class SettingsGroup(val titleRes: Int, val slug: String, val video: VideoGroup? = null) {
    QUICK(R.string.settings_group_quick, "quick"),
    SOURCES(R.string.settings_group_sources, "sources"),
    PLAYER(R.string.settings_vp_cat_player, "player", VideoGroup.PLAYBACK),
    SOUND(R.string.settings_group_sound_subtitles, "sound", VideoGroup.SOUND),
    LIVE(R.string.settings_live_tv, "live", VideoGroup.LIVE),
    WATCHING(R.string.settings_group_watching_recording, "watching", VideoGroup.WATCHING),
    APPEARANCE(R.string.settings_group_appearance, "appearance"),
    APP(R.string.settings_group_app, "app"),
    ;

    /** The stable route a Settings page lives under; a card opens `settings/<group>`. */
    val route: String get() = "settings/$slug"

    companion object {
        /** The group a Video player row lives in; Diagnostics sits at the end of App. */
        fun of(group: VideoGroup): SettingsGroup = when (group) {
            VideoGroup.PICTURE, VideoGroup.PLAYER, VideoGroup.PLAYBACK -> PLAYER
            VideoGroup.SOUND -> SOUND
            VideoGroup.LIVE -> LIVE
            VideoGroup.WATCHING -> WATCHING
            VideoGroup.DIAGNOSTICS -> APP
        }

        /** Reads a saved group name, falling back to Quick for state written by an older release. */
        fun fromName(name: String?): SettingsGroup = entries.firstOrNull { it.name == name } ?: QUICK
    }
}