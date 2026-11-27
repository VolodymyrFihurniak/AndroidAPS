package app.aaps.ui.compose.scenes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.SceneAction
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.NumberInputRow
import app.aaps.core.ui.compose.stringResource

/**
 * autoISF build: editor of [SceneAction.LoopSettings] (loop settings while a scene runs, like the overrides of iAPS).
 * BG limits are shown in the user's units and stored in mg/dL.
 */
@Composable
internal fun LoopSettingsEditor(
    action: SceneAction.LoopSettings,
    onUpdate: (SceneAction) -> Unit,
    isMmol: Boolean
) {
    val percentRange = SceneAction.LoopSettings.MIN_PERCENT.toDouble()..SceneAction.LoopSettings.MAX_PERCENT.toDouble()
    Column(verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
        NumberInputRow(
            labelRef = CoreUiStrings.scene_loop_isf_percent,
            value = action.isfPercent.toDouble(),
            onValueChange = { onUpdate(action.copy(isfPercent = it.toInt())) },
            valueRange = percentRange,
            step = 5.0,
            unitLabel = CoreUiStrings.units_percent
        )
        NumberInputRow(
            labelRef = CoreUiStrings.scene_loop_cr_percent,
            value = action.crPercent.toDouble(),
            onValueChange = { onUpdate(action.copy(crPercent = it.toInt())) },
            valueRange = percentRange,
            step = 5.0,
            unitLabel = CoreUiStrings.units_percent
        )
        NumberInputRow(
            labelRef = CoreUiStrings.scene_loop_basal_percent,
            value = action.basalPercent.toDouble(),
            onValueChange = { onUpdate(action.copy(basalPercent = it.toInt())) },
            valueRange = percentRange,
            step = 5.0,
            unitLabel = CoreUiStrings.units_percent
        )
        OptionalSwitch(stringResource(CoreUiStrings.scene_loop_max_iob), action.maxIob != null) { on ->
            onUpdate(action.copy(maxIob = if (on) 3.0 else null))
        }
        action.maxIob?.let { value ->
            NumberInputRow(
                labelRef = null, value = value, onValueChange = { onUpdate(action.copy(maxIob = it)) },
                valueRange = 0.0..25.0, step = 0.5, decimalPlaces = 1, unitLabel = CoreUiStrings.insulin_unit_shortname
            )
        }
        OptionalSwitch(stringResource(CoreUiStrings.scene_loop_smb_minutes), action.smbMinutes != null) { on ->
            onUpdate(action.copy(smbMinutes = if (on) 30 else null))
        }
        action.smbMinutes?.let { value ->
            NumberInputRow(
                labelRef = null, value = value.toDouble(), onValueChange = { onUpdate(action.copy(smbMinutes = it.toInt())) },
                valueRange = 15.0..120.0, step = 15.0, unitLabel = CoreUiStrings.units_min
            )
        }
        OptionalSwitch(stringResource(CoreUiStrings.scene_loop_uam_minutes), action.uamMinutes != null) { on ->
            onUpdate(action.copy(uamMinutes = if (on) 30 else null))
        }
        action.uamMinutes?.let { value ->
            NumberInputRow(
                labelRef = null, value = value.toDouble(), onValueChange = { onUpdate(action.copy(uamMinutes = it.toInt())) },
                valueRange = 15.0..120.0, step = 15.0, unitLabel = CoreUiStrings.units_min
            )
        }
        OptionalSwitch(stringResource(CoreUiStrings.scene_loop_iob_th), action.iobThPercent != null) { on ->
            onUpdate(action.copy(iobThPercent = if (on) 100 else null))
        }
        action.iobThPercent?.let { value ->
            NumberInputRow(
                labelRef = null, value = value.toDouble(), onValueChange = { onUpdate(action.copy(iobThPercent = it.toInt())) },
                valueRange = 10.0..100.0, step = 5.0, unitLabel = CoreUiStrings.units_percent
            )
        }
        OptionalSwitch(stringResource(CoreUiStrings.scene_loop_autoisf_weights_off), action.autoIsfWeightsOff) { on ->
            onUpdate(action.copy(autoIsfWeightsOff = on))
        }
        OptionalSwitch(stringResource(CoreUiStrings.scene_loop_end_on_carbs), action.endOnCarbs) { on ->
            onUpdate(action.copy(endOnCarbs = on))
        }
        BgLimit(stringResource(CoreUiStrings.scene_loop_end_above), action.endAboveMgdl, isMmol, 180.0) { onUpdate(action.copy(endAboveMgdl = it)) }
        BgLimit(stringResource(CoreUiStrings.scene_loop_end_below), action.endBelowMgdl, isMmol, 80.0) { onUpdate(action.copy(endBelowMgdl = it)) }
    }
}

@Composable
private fun BgLimit(label: String, mgdl: Double?, isMmol: Boolean, defaultMgdl: Double, onChange: (Double?) -> Unit) {
    OptionalSwitch(label, mgdl != null) { on -> onChange(if (on) defaultMgdl else null) }
    mgdl?.let { value ->
        if (isMmol) NumberInputRow(
            labelRef = null, value = value * Constants.MGDL_TO_MMOLL, onValueChange = { onChange(it * Constants.MMOLL_TO_MGDL) },
            valueRange = 3.0..20.0, step = 0.1, decimalPlaces = 1, unitLabel = CoreUiStrings.mmol
        )
        else NumberInputRow(
            labelRef = null, value = value, onValueChange = { onChange(it) },
            valueRange = 54.0..360.0, step = 1.0, unitLabel = CoreUiStrings.mgdl
        )
    }
}

@Composable
private fun OptionalSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
