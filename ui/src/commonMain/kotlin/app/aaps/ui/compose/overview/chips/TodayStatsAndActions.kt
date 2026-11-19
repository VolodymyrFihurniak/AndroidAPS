package app.aaps.ui.compose.overview.chips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsCard
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalClientRole
import app.aaps.core.ui.compose.navigation.NavigationRequest
import app.aaps.core.ui.compose.navigation.color
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings

/**
 * autoISF build: the statistics of today on the overview, from the user's concept: time in range, CV and the average.
 * Each with a bar: TIR full at 100 %, CV full at 50 % (too high), the average full at the top of the range.
 */
@Composable
fun TodayStatsRow(state: TodayStatsUiState, onOpenStatistics: () -> Unit, modifier: Modifier = Modifier) {
    if (!state.visible) return
    Column(verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium), modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = ElementType.STATISTICS.icon(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(AapsSpacing.chipIconSize)
            )
            Text(
                text = stringResource(UiStrings.today_stats_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = AapsSpacing.medium)
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            StatCard(
                title = stringResource(UiStrings.today_tir_short),
                value = state.timeInRangeText,
                fraction = state.timeInRange,
                color = AapsTheme.generalColors.statusNormal,
                onClick = onOpenStatistics,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = stringResource(UiStrings.today_cv_short),
                value = state.cvText,
                fraction = state.cvFraction,
                color = AapsTheme.generalColors.statusWarning,
                onClick = onOpenStatistics,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = stringResource(CoreUiStrings.average),
                value = state.averageText,
                unit = state.unitText,
                fraction = state.averageFraction,
                color = AapsTheme.elementColors.tempBasal,
                onClick = onOpenStatistics,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, fraction: Float, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier, unit: String = "") {
    AapsCard(modifier = modifier.fillMaxHeight(), onClick = onClick) {
        Column(
            verticalArrangement = Arrangement.spacedBy(AapsSpacing.small),
            modifier = Modifier.padding(AapsSpacing.large)
        ) {
            Text(text = title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                if (unit.isNotEmpty())
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.padding(start = AapsSpacing.small, bottom = AapsSpacing.extraSmall)
                    )
            }
            LinearProgressIndicator(
                progress = { fraction },
                color = color,
                trackColor = color.copy(alpha = 0.2f),
                strokeCap = StrokeCap.Round,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * autoISF build: the quick actions of the overview, from the user's concept: carbs, bolus, temp target and "more",
 * which opens all the treatments. They open the same dialogs as the treatments sheet, with its protection and its
 * role rules (they go through the same navigation). Not clickable on a client that is not paired.
 */
@Composable
fun QuickActionsRow(commandsAllowed: Boolean, onNavigate: (NavigationRequest) -> Unit, onMore: () -> Unit, modifier: Modifier = Modifier) {
    val role = LocalClientRole.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        for ((element, label) in listOf(
            ElementType.CARBS to InterfacesStrings.carbs,
            ElementType.INSULIN to InterfacesStrings.bolus,
            ElementType.TEMP_TARGET_MANAGEMENT to UiStrings.quick_action_temp_target
        )) {
            ActionCard(
                icon = element.icon(),
                color = element.color(),
                label = stringResource(label),
                enabled = commandsAllowed && role.allows(element.role),
                onClick = { onNavigate(NavigationRequest.Element(element)) },
                modifier = Modifier.weight(1f)
            )
        }
        ActionCard(
            icon = Icons.Filled.MoreHoriz,
            color = MaterialTheme.colorScheme.primary,
            label = stringResource(UiStrings.quick_action_more),
            enabled = commandsAllowed,
            onClick = onMore,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ActionCard(icon: ImageVector, color: Color, label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AapsCard(modifier = modifier.fillMaxHeight(), onClick = if (enabled) onClick else null) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AapsSpacing.small, Alignment.CenterVertically),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = AapsSpacing.large, horizontal = AapsSpacing.small)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = if (enabled) color else color.copy(alpha = 0.4f), modifier = Modifier.size(AapsSpacing.chipIconSize))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
