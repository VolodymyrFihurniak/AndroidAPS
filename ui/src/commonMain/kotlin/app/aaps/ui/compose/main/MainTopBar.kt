package app.aaps.ui.compose.main

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.core.ui.compose.icons.IcSettingsOff
import app.aaps.core.ui.compose.statusLevelToColor
import app.aaps.core.ui.compose.statusLevelToDescription
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.search.M3SearchBar
import app.aaps.ui.search.SearchUiState

/**
 * autoISF build: a device icon in the top bar. [level] is the worst state of the device, [onClick] opens its screen.
 */
data class TopBarDevice(val icon: ImageVector, val label: String, val level: StatusLevel, val onClick: () -> Unit)

/** The worst of [levels]: critical before warning before normal. Null and unknown count as nothing. */
internal fun worstStatusLevel(vararg levels: StatusLevel?): StatusLevel = when {
    StatusLevel.CRITICAL in levels -> StatusLevel.CRITICAL
    StatusLevel.WARNING in levels  -> StatusLevel.WARNING
    StatusLevel.NORMAL in levels   -> StatusLevel.NORMAL
    else                           -> StatusLevel.UNSPECIFIED
}

/**
 * Main top bar with M3-style search bar.
 * Layout: [Menu] [----Search Bar----] [Pump] [Sensor] [Settings]
 *
 * @param searchUiState Current search UI state
 * @param onMenuClick Called when menu button is clicked
 * @param onPreferencesClick Called when preferences button is clicked
 * @param onSearchQueryChange Called when search query changes
 * @param onSearchClear Called when search query is cleared
 * @param onSearchActiveChange Called when search active state changes
 * @param isSimpleMode When true, shows a non-interactive simple-mode indicator next to Settings
 * @param modifier Modifier for the component
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTopBar(
    searchUiState: SearchUiState,
    onMenuClick: () -> Unit,
    onPreferencesClick: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchClear: () -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    isSimpleMode: Boolean = false,
    devices: List<TopBarDevice> = emptyList(),
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                M3SearchBar(
                    query = searchUiState.query,
                    isActive = searchUiState.isSearchActive,
                    onQueryChange = onSearchQueryChange,
                    onClearClick = onSearchClear,
                    onActiveChange = onSearchActiveChange,
                    modifier = Modifier.weight(1f)
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = stringResource(CoreUiStrings.open_navigation)
                )
            }
        },
        actions = {
            // autoISF build: pump and sensor from the user's concept, in the colour of their worst state
            for (device in devices) {
                val state = statusLevelToDescription(device.level)?.let { stringResource(it) }
                IconButton(onClick = device.onClick) {
                    Icon(
                        imageVector = device.icon,
                        contentDescription = if (state != null) stringResource(InterfacesStrings.values_with_comma, device.label, state) else device.label,
                        tint = if (device.level == StatusLevel.WARNING || device.level == StatusLevel.CRITICAL) statusLevelToColor(device.level)
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onPreferencesClick) {
                // In simple mode the gear shows "crossed" (IcSettingsOff) to signal the mode;
                // the button action (open settings) is unchanged.
                Icon(
                    imageVector = if (isSimpleMode) IcSettingsOff else Icons.Default.Settings,
                    contentDescription = stringResource(CoreUiStrings.settings)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent
        ),
        windowInsets = WindowInsets(0),
        modifier = modifier
    )
}
