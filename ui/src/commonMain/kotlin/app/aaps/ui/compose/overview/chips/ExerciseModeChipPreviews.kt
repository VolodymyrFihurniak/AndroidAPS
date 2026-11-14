package app.aaps.ui.compose.overview.chips

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true)
@Composable
internal fun ExerciseModeChipOnPreview() {
    MaterialTheme { ExerciseModeChip(enabled = true, onClick = {}) }
}

@Preview(showBackground = true)
@Composable
internal fun ExerciseModeChipOffPreview() {
    MaterialTheme { ExerciseModeChip(enabled = false, onClick = {}) }
}
