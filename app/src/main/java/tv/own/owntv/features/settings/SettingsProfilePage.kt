package tv.own.owntv.features.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import tv.own.owntv.R

/**
 * Settings › App › Profiles: the multi-profile rows in a page of their own. The group page keeps a
 * single "Profiles" row (with the profile count), so profiles no longer take one of the eight cards
 * but keep every ability they had — add, edit, delete, Kids, PIN and download permission.
 */
@Composable
internal fun SettingsProfilePage(onBack: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler { onBack() }
    val scroll = rememberScrollState()
    val count = profileCount()
    StageSettingsPage(
        group = stringResource(R.string.profiles_title),
        count = pluralStringResource(R.plurals.settings_profile_count, count, count),
        searchQuery = "",
        onSearchQuery = {},
        scroll = scroll,
        modifier = modifier,
        parents = listOf(stringResource(R.string.settings_group_app)),
        rows = { ProfileSettingsRows() },
    )
}