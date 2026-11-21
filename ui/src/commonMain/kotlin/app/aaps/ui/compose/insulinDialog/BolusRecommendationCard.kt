package app.aaps.ui.compose.insulinDialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalProfileUtil
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings
import app.aaps.ui.compose.overview.chips.BolusRecommendation
import app.aaps.ui.compose.overview.chips.BolusRecommendationKind
import app.aaps.ui.compose.overview.chips.isWarning

/**
 * autoISF build: the advice of the loop at the top of the Insulin dialog (see [BolusRecommendation]): one sentence,
 * then the numbers it comes from. In open loop a button puts the dose into the amount field; the user still confirms
 * with OK. In closed loop with SMB there is no button, so the same insulin is not given twice.
 */
@Composable
internal fun BolusRecommendationCard(advice: BolusRecommendation, onUse: (Double) -> Unit, modifier: Modifier = Modifier) {
    val profileUtil = LocalProfileUtil.current
    val warning = advice.kind.isWarning()
    val headline = when (advice.kind) {
        BolusRecommendationKind.SUGGEST       -> stringResource(UiStrings.bolus_rec_suggest, stringResource(InterfacesStrings.format_insulin_units, advice.dose))
        BolusRecommendationKind.LOOP_GIVES    -> stringResource(UiStrings.bolus_rec_loop_gives, stringResource(InterfacesStrings.format_insulin_units, advice.insulinNeeded))
        BolusRecommendationKind.NONE          -> stringResource(UiStrings.bolus_rec_none)
        BolusRecommendationKind.CARBS         -> stringResource(
            UiStrings.bolus_rec_carbs,
            stringResource(InterfacesStrings.format_carbs, advice.carbs),
            stringResource(CoreUiStrings.format_mins, advice.carbsWithinMinutes)
        )
        BolusRecommendationKind.LOW_NOW       -> stringResource(UiStrings.bolus_rec_low_now)
        BolusRecommendationKind.LOW_PREDICTED -> stringResource(UiStrings.bolus_rec_low_predicted, profileUtil.fromMgdlToStringWithUnits(advice.minPredictedMgdl))
        BolusRecommendationKind.NO_DATA       -> stringResource(UiStrings.bolus_rec_no_data)
    }
    // The numbers, where the run gave them. Not for no data or a low now: there they would only distract.
    val details = if (advice.kind == BolusRecommendationKind.NO_DATA || advice.kind == BolusRecommendationKind.LOW_NOW) emptyList()
    else listOfNotNull(
        advice.inOneHourMgdl?.let { stringResource(UiStrings.bolus_rec_in_one_hour, profileUtil.fromMgdlToStringWithUnits(it)) },
        advice.eventualMgdl?.let { stringResource(UiStrings.bolus_rec_eventual, profileUtil.fromMgdlToStringWithUnits(it)) },
        stringResource(UiStrings.bolus_rec_needed, stringResource(InterfacesStrings.format_insulin_units, advice.insulinNeeded)),
        advice.isfMgdl?.let { stringResource(UiStrings.bolus_rec_isf, profileUtil.fromMgdlToStringWithUnits(it)) },
        stringResource(UiStrings.bolus_rec_run_age, stringResource(CoreUiStrings.format_mins, (advice.resultAgeMs / 60_000L).toInt()))
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(AapsSpacing.small),
            modifier = Modifier.padding(horizontal = AapsSpacing.extraLarge, vertical = AapsSpacing.medium)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = ElementType.LOOP.icon(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(AapsSpacing.chipIconSize)
                )
                Text(
                    text = stringResource(UiStrings.bolus_rec_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = AapsSpacing.medium)
                )
            }
            Text(
                text = headline,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (warning) AapsTheme.generalColors.statusWarning else MaterialTheme.colorScheme.onSurface
            )
            details.forEach {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (advice.kind == BolusRecommendationKind.SUGGEST) {
                FilledTonalButton(onClick = { onUse(advice.dose) }, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(UiStrings.bolus_rec_use, stringResource(InterfacesStrings.format_insulin_units, advice.dose)))
                }
            }
        }
    }
}
