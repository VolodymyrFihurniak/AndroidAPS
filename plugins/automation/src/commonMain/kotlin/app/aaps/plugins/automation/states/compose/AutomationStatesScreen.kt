package app.aaps.plugins.automation.states.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.stringResource
import app.aaps.plugins.automation.AutomationStrings
import app.aaps.plugins.automation.states.AutomationStateService

/** autoISF: the screen of the Automation States plugin. */
internal class AutomationStatesScreen(private val service: AutomationStateService) : ComposablePluginContent {

    @Composable
    override fun Render(
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)?
    ) {
        var editState by remember { mutableStateOf<String?>(null) }
        var addState by remember { mutableStateOf(false) }
        val title = stringResource(AutomationStrings.automation_states)
        val backDesc = stringResource(CoreUiStrings.back)
        val addDesc = stringResource(AutomationStrings.add_automation_state)

        LaunchedEffect(Unit) {
            setToolbarConfig(
                ToolbarConfig(
                    title = title,
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = backDesc)
                        }
                    },
                    actions = {
                        IconButton(onClick = { addState = true }) {
                            Icon(Icons.Filled.Add, contentDescription = addDesc)
                        }
                    }
                )
            )
        }

        // Read again on every change of the service
        val changes by service.changes.collectAsState()
        val states = remember(changes) { service.getAllStates().map { (name, current) -> StateRow(name, current, service.getStateValues(name)) } }

        AutomationStatesContent(
            states = states,
            onSelectValue = { name, value -> runCatching { service.setState(name, value) } },
            onEdit = { editState = it }
        )

        if (addState) {
            StateEditDialog(
                initialName = "",
                initialValues = emptyList(),
                nameEditable = true,
                onDismiss = { addState = false },
                onSave = { name, values ->
                    service.setStateValues(name, values)
                    addState = false
                },
                onDelete = null
            )
        }
        editState?.let { name ->
            StateEditDialog(
                initialName = name,
                initialValues = service.getStateValues(name),
                nameEditable = false,
                onDismiss = { editState = null },
                onSave = { _, values ->
                    service.setStateValues(name, values)
                    editState = null
                },
                onDelete = {
                    service.deleteState(name)
                    editState = null
                }
            )
        }
    }
}

internal data class StateRow(val name: String, val current: String, val values: List<String>)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AutomationStatesContent(
    states: List<StateRow>,
    onSelectValue: (String, String) -> Unit,
    onEdit: (String) -> Unit
) {
    if (states.isEmpty()) {
        Text(
            text = stringResource(AutomationStrings.no_automation_states_plugin),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(AapsSpacing.large)
        )
        return
    }
    val editDesc = stringResource(AutomationStrings.edit_automation_state)
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = AapsSpacing.large),
        verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
    ) {
        items(states, key = { it.name }) { state ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(AapsSpacing.large)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = state.name,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { onEdit(state.name) }) {
                            Icon(Icons.Filled.Edit, contentDescription = editDesc)
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(AapsSpacing.small)) {
                        state.values.forEach { value ->
                            FilterChip(
                                selected = value == state.current,
                                onClick = { onSelectValue(state.name, value) },
                                label = { Text(value) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Add a new state, or change the values of a state. The values are written one per line. */
@Composable
internal fun StateEditDialog(
    initialName: String,
    initialValues: List<String>,
    nameEditable: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, List<String>) -> Unit,
    onDelete: (() -> Unit)?
) {
    var name by remember { mutableStateOf(initialName) }
    var values by remember { mutableStateOf(initialValues.joinToString("\n")) }
    var confirmDelete by remember { mutableStateOf(false) }
    val valueList = values.lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    val valid = name.isNotBlank() && valueList.isNotEmpty()

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(AutomationStrings.delete_state)) },
            text = { Text(stringResource(AutomationStrings.delete_state_confirmation)) },
            confirmButton = { TextButton(onClick = onDelete) { Text(stringResource(CoreUiStrings.ok)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(CoreUiStrings.cancel)) } }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (nameEditable) AutomationStrings.add_automation_state else AutomationStrings.edit_automation_state)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    enabled = nameEditable,
                    singleLine = true,
                    label = { Text(stringResource(AutomationStrings.state_name_label)) }
                )
                OutlinedTextField(
                    value = values,
                    onValueChange = { values = it },
                    minLines = 3,
                    label = { Text(stringResource(AutomationStrings.state_values_one_per_line)) }
                )
                if (onDelete != null) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                        Text(stringResource(AutomationStrings.delete_state))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim(), valueList) }, enabled = valid) { Text(stringResource(CoreUiStrings.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreUiStrings.cancel)) } }
    )
}
