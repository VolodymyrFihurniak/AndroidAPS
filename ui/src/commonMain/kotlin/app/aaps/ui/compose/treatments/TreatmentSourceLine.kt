package app.aaps.ui.compose.treatments

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings

/** Size of the small icon in front of the source of a treatment */
private val SOURCE_ICON_SIZE = 14.dp

/**
 * autoISF build: one small line under a bolus or a temp basal in the treatment history that says who gave it: the
 * algorithm, an automation, a client with its device name, or the user with the screen it came from.
 */
@Composable
fun TreatmentSourceLine(source: TreatmentSource, modifier: Modifier = Modifier) {
    val (icon, text) = when (source) {
        TreatmentSource.Algorithm  -> ElementType.LOOP.icon() to stringResource(UiStrings.treatment_source_algorithm)
        TreatmentSource.Automation -> ElementType.AUTOMATION.icon() to stringResource(CoreUiStrings.automation)
        is TreatmentSource.Client  -> ElementType.PAIR_WITH_MASTER.icon() to
            if (source.name.isNotBlank()) stringResource(UiStrings.treatment_source_client, source.name)
            else stringResource(UiStrings.treatment_source_client_or_ns)
        is TreatmentSource.User    -> userIcon(source.via) to
            (viaLabel(source.via)?.let { stringResource(UiStrings.treatment_source_user_via, stringResource(it)) }
                ?: stringResource(UiStrings.treatment_source_user))
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(SOURCE_ICON_SIZE))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = AapsSpacing.small)
        )
    }
}

/** The screen or tool a user action came from, when it has a name of its own */
private fun viaLabel(via: Sources): TextRef? = when (via) {
    Sources.WizardDialog    -> CoreUiStrings.boluswizard
    Sources.InsulinDialog   -> CoreUiStrings.overview_insulin_label
    Sources.TreatmentDialog -> CoreUiStrings.overview_treatment_label
    Sources.CarbDialog      -> InterfacesStrings.carbs
    Sources.Wear            -> CoreUiStrings.wear
    Sources.SMS             -> CoreUiStrings.sms
    else                    -> null
}

private fun userIcon(via: Sources): ImageVector = when (via) {
    Sources.WizardDialog, Sources.QuickWizard -> ElementType.BOLUS_WIZARD.icon()
    Sources.CarbDialog                        -> ElementType.CARBS.icon()
    else                                      -> ElementType.INSULIN.icon()
}
