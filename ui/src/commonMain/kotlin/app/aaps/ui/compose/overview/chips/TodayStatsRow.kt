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
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsCard
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
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
