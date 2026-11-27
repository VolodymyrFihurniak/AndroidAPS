package app.aaps.ui.compose.scenes.wizard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import app.aaps.core.data.model.SceneAction
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.compose.scenes.LoopSettingsEditor

/** autoISF build: wizard step for the loop settings of a scene (see [SceneAction.LoopSettings]) */
@Composable
internal fun LoopSettingsStep(
    state: SceneWizardViewModel.WizardState,
    onToggle: (Boolean) -> Unit,
    onUpdate: (SceneAction) -> Unit,
    isMmol: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    WizardStepLayout(
        secondaryButton = WizardButton(text = stringResource(CoreUiStrings.back), onClick = onBack),
        primaryButton = WizardButton(text = stringResource(CoreUiStrings.next), onClick = onNext)
    ) {
        Text(
            text = stringResource(CoreUiStrings.scene_loop_settings),
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = stringResource(CoreUiStrings.scene_wizard_loop_settings_info),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ActionToggle(
            label = stringResource(CoreUiStrings.scene_wizard_include_action, stringResource(CoreUiStrings.scene_loop_settings)),
            checked = state.loopSettingsEnabled,
            onCheckedChange = onToggle
        )
        AnimatedVisibility(
            visible = state.loopSettingsEnabled,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            LoopSettingsEditor(action = state.loopSettingsAction, onUpdate = onUpdate, isMmol = isMmol)
        }
    }
}
