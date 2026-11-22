package app.aaps.ui.compose.insulinDialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.LocalProfileUtil
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings

/**
 * autoISF build: the advice of the personal prediction (experimental) in the Insulin dialog, under the advice of the
 * loop: one sentence, then the numbers it comes from. It has no button: the model is a test, a dose is typed by hand.
 */
@Composable
internal fun ModelAdviceCard(advice: ModelAdvice, modifier: Modifier = Modifier) {
    val profileUtil = LocalProfileUtil.current
    val dateUtil = LocalDateUtil.current
    val warning = advice.kind == ModelAdviceKind.LOW_NOW || advice.kind == ModelAdviceKind.LOW_PREDICTED || advice.kind == ModelAdviceKind.TOO_MUCH
    val headline = when (advice.kind) {
        ModelAdviceKind.NO_DATA       -> stringResource(UiStrings.model_adv_no_data)
        ModelAdviceKind.LOW_NOW       -> stringResource(UiStrings.bolus_rec_low_now)
        ModelAdviceKind.NOT_READY     -> stringResource(UiStrings.model_adv_not_ready)
        ModelAdviceKind.LOW_PREDICTED -> stringResource(UiStrings.bolus_rec_low_predicted, profileUtil.fromMgdlToStringWithUnits(advice.minPredictedMgdl))
        ModelAdviceKind.FALLING       -> stringResource(UiStrings.model_adv_falling)
        ModelAdviceKind.MISSING       -> stringResource(UiStrings.model_adv_missing, stringResource(InterfacesStrings.format_insulin_units, advice.units))
        ModelAdviceKind.TOO_MUCH      -> stringResource(
            UiStrings.model_adv_too_much, stringResource(InterfacesStrings.format_insulin_units, advice.units), stringResource(InterfacesStrings.format_carbs, advice.carbs)
        )
        ModelAdviceKind.NONE          -> stringResource(UiStrings.model_adv_none)
    }
    val forecast = advice.forecast
    val details = buildList {
        if (advice.kind == ModelAdviceKind.MISSING && advice.loopMayGive) add(stringResource(UiStrings.model_adv_loop_may_give))
        if (forecast != null) {
            for (minutes in listOf(30, 60, 120)) {
                val noInsulin = forecast.at(minutes) ?: continue
                val expected = forecast.expectedAt(minutes)
                val time = stringResource(CoreUiStrings.format_mins, minutes)
                add(
                    if (expected != null) stringResource(
                        UiStrings.model_adv_in_both, time, profileUtil.fromMgdlToStringWithUnits(expected), profileUtil.fromMgdlToStringWithUnits(noInsulin)
                    )
                    else stringResource(UiStrings.model_adv_in, time, profileUtil.fromMgdlToStringWithUnits(noInsulin))
                )
            }
            add(stringResource(UiStrings.bolus_rec_eventual, profileUtil.fromMgdlToStringWithUnits(forecast.eventualMgdl)))
            forecast.parts60?.let { parts ->
                fun signed(mgdl: Double) = profileUtil.fromMgdlToSignedStringInUnits(mgdl)
                val unit = stringResource(if (profileUtil.units == GlucoseUnit.MGDL) CoreUiStrings.mgdl else CoreUiStrings.mmol)
                add(
                    stringResource(
                        UiStrings.model_adv_parts, unit, signed(parts.insulin), signed(parts.carbs), signed(parts.meal), signed(parts.drift),
                        signed(parts.trend), signed(parts.network)
                    )
                )
            }
            add(stringResource(UiStrings.model_adv_isf, profileUtil.fromMgdlToStringWithUnits(forecast.isfMgdl)))
            forecast.typicalErrorMgdl.getOrNull(11)?.let { add(stringResource(UiStrings.model_adv_error, profileUtil.fromMgdlToStringWithUnits(it))) }
            add(stringResource(UiStrings.model_adv_trained, dateUtil.dateAndTimeString(forecast.modelTrainedAt)))
        }
    }

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
                    imageVector = Icons.Default.Science,
                    contentDescription = null,
                    tint = AapsTheme.generalColors.learnedPrediction,
                    modifier = Modifier.size(AapsSpacing.chipIconSize)
                )
                Text(
                    text = stringResource(UiStrings.model_adv_title),
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
        }
    }
}
