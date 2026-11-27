package app.aaps.ui.compose.carbsDialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.aaps.core.data.format.NumberFormat
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.NumberInputRow
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings
import app.aaps.ui.food.FoodItem
import app.aaps.ui.food.MealPart
import app.aaps.ui.food.MealRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.round
import kotlin.math.roundToInt

/**
 * autoISF build: the meal calculator in the carbs dialog. Foods with their weights give the carbs, and with
 * the fat and protein setting on, also the fat and protein that become extended carbs.
 */
@Composable
internal fun MealCard(
    uiState: CarbsDialogUiState,
    onAddFood: () -> Unit,
    onPhoto: (() -> Unit)?,
    onRemovePart: (Int) -> Unit,
    onFatChange: (Double) -> Unit,
    onProteinChange: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(UiStrings.meal_title), style = MaterialTheme.typography.titleSmall)
        uiState.mealParts.forEachIndexed { index, part ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(part.food.name, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(
                            UiStrings.meal_part_line,
                            stringResource(UiStrings.meal_amount, formatAmount(part.grams), part.food.unit),
                            stringResource(InterfacesStrings.format_carbs, part.nutrients.carbs.roundToInt())
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { onRemovePart(index) }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(UiStrings.meal_remove))
                }
            }
        }
        if (uiState.mealParts.isNotEmpty()) {
            val total = uiState.mealTotal
            Text(
                stringResource(
                    UiStrings.meal_total,
                    stringResource(InterfacesStrings.format_carbs, total.carbs.roundToInt()),
                    stringResource(InterfacesStrings.format_carbs, total.fat.roundToInt()),
                    stringResource(InterfacesStrings.format_carbs, total.protein.roundToInt())
                ),
                style = MaterialTheme.typography.bodySmall
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onAddFood) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(UiStrings.meal_add_food))
            }
            if (onPhoto != null) {
                OutlinedButton(onClick = onPhoto, enabled = !uiState.photoBusy) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(UiStrings.meal_photo))
                }
            }
            if (uiState.photoBusy) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        if (uiState.photoBusy) Text(stringResource(UiStrings.meal_photo_busy), style = MaterialTheme.typography.bodySmall)
        if (uiState.mealParts.any { it.food.source == FoodItem.Source.AI })
            Text(stringResource(UiStrings.meal_photo_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        if (uiState.fpuEnabled) {
            NumberInputRow(
                labelRef = UiStrings.meal_fat,
                value = uiState.fatGrams,
                onValueChange = onFatChange,
                valueRange = 0.0..300.0,
                step = 1.0,
                valueFormat = NumberFormat.INTEGER,
                unitLabel = CoreUiStrings.shortgramm
            )
            NumberInputRow(
                labelRef = UiStrings.meal_protein,
                value = uiState.proteinGrams,
                onValueChange = onProteinChange,
                valueRange = 0.0..300.0,
                step = 1.0,
                valueFormat = NumberFormat.INTEGER,
                unitLabel = CoreUiStrings.shortgramm
            )
            uiState.fpu?.let {
                Text(stringResource(UiStrings.meal_fpu, it.carbs, it.durationHours, it.delayMinutes + maxOf(0, uiState.timeOffsetMinutes)), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/**
 * autoISF build: pick a food and its amount. Typing searches My foods and the common foods at once; Open Food
 * Facts (by name, or by barcode when only digits are typed) only on request, because it needs the network.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FoodPickerSheet(
    repository: MealRepository,
    onPick: (FoodItem, Double) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var local by remember { mutableStateOf<List<FoodItem>>(emptyList()) }
    var online by remember { mutableStateOf<List<FoodItem>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<FoodItem?>(null) }
    var amount by remember { mutableStateOf(0.0) }
    val isBarcode = query.length >= 8 && query.all { it.isDigit() }

    LaunchedEffect(query) {
        delay(SEARCH_DELAY_MS)
        local = repository.localSearch(query).take(MAX_RESULTS)
        online = null
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val food = selected
            if (food == null) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(UiStrings.food_search_hint)) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(
                    enabled = !searching && (isBarcode || query.trim().length >= 3),
                    onClick = {
                        searching = true
                        scope.launch {
                            online = if (isBarcode) listOfNotNull(repository.byBarcode(query)) else repository.onlineSearch(query)
                            searching = false
                            // A barcode has one answer: go straight to the amount.
                            if (isBarcode) online?.singleOrNull()?.let { selected = it; amount = it.defaultGrams }
                        }
                    }
                ) { Text(stringResource(if (isBarcode) UiStrings.food_search_barcode else UiStrings.food_search_online)) }
                when {
                    searching                            -> Text(stringResource(UiStrings.food_searching), style = MaterialTheme.typography.bodySmall)
                    online != null && !repository.isConnected -> Text(stringResource(UiStrings.food_search_offline), style = MaterialTheme.typography.bodySmall)
                    online?.isEmpty() == true            -> Text(stringResource(UiStrings.food_search_none), style = MaterialTheme.typography.bodySmall)
                }
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    items(online.orEmpty() + local) { item ->
                        FoodRow(item) { selected = item; amount = item.defaultGrams }
                        HorizontalDivider()
                    }
                }
            } else {
                Text(food.name, style = MaterialTheme.typography.titleMedium)
                Text(per100Text(food), style = MaterialTheme.typography.bodySmall)
                NumberInputRow(
                    labelRef = UiStrings.food_amount,
                    value = amount,
                    onValueChange = { amount = it },
                    valueRange = 0.0..MAX_AMOUNT,
                    step = if (food.unit == "g" || food.unit == "ml") 5.0 else 0.5,
                    decimalPlaces = if (food.unit == "g" || food.unit == "ml") 0 else 1,
                    unitLabel = TextRef.Literal(food.unit)
                )
                Text(
                    stringResource(InterfacesStrings.format_carbs, food.nutrients(amount).carbs.roundToInt()),
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { selected = null }) { Text(stringResource(CoreUiStrings.cancel)) }
                    Button(enabled = amount > 0.0, onClick = { onPick(food, amount) }) { Text(stringResource(UiStrings.food_add)) }
                }
            }
        }
    }
}

@Composable
private fun FoodRow(item: FoodItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        Text(item.name, style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(UiStrings.food_row_detail, per100Text(item), stringResource(sourceLabel(item.source))),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun per100Text(item: FoodItem): String =
    stringResource(UiStrings.food_per_100, stringResource(InterfacesStrings.format_carbs, item.carbsPer100.roundToInt()), item.unit)

private fun sourceLabel(source: FoodItem.Source): TextRef = when (source) {
    FoodItem.Source.MY_FOODS        -> UiStrings.food_source_my
    FoodItem.Source.COMMON          -> UiStrings.food_source_common
    FoodItem.Source.OPEN_FOOD_FACTS -> UiStrings.food_source_off
    FoodItem.Source.AI              -> UiStrings.food_source_ai
}

/** Whole numbers without decimals, others with one */
private fun formatAmount(value: Double): String {
    val r = round(value * 10.0) / 10.0
    return if (r == round(r)) r.toInt().toString() else r.toString()
}

private const val SEARCH_DELAY_MS = 250L
private const val MAX_RESULTS = 60
private const val MAX_AMOUNT = 2000.0
