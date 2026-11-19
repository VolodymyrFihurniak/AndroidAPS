package app.aaps.ui.compose.overview.chips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.aaps.core.graph.BasalProfileGraphCompose
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings

/**
 * autoISF build: the details of the basal card. Read only: it changes nothing, and "Profiles" opens the existing
 * profile management, where the profile and a profile switch are made as before.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BasalDetailsSheet(
    state: BasalUiState,
    onOpenProfiles: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(AapsSpacing.small),
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AapsSpacing.xxLarge)
                .padding(bottom = AapsSpacing.xxLarge)
        ) {
            Text(
                text = stringResource(UiStrings.basal_card),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            state.details.forEachIndexed { i, (label, value) ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.End,
                        modifier = Modifier.padding(start = AapsSpacing.medium)
                    )
                }
            }
            state.profile?.let { profile ->
                Text(
                    text = stringResource(UiStrings.basal_details_daily_profile),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = AapsSpacing.medium)
                )
                BasalProfileGraphCompose(
                    profile1 = profile,
                    profile1Name = state.profileName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                )
            }
            OutlinedButton(
                onClick = { onDismiss(); onOpenProfiles() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = AapsSpacing.medium)
            ) {
                Text(stringResource(UiStrings.basal_details_open_profiles))
            }
        }
    }
}
