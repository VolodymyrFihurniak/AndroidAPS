package app.aaps.ui.compose.overview.chips

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true)
@Composable
internal fun BasalChipProfilePreview() {
    MaterialTheme { BasalChip(BasalUiState(visible = true, rateText = "0.50 U/h", statusText = "100% of profile · By profile"), onClick = {}) }
}

@Preview(showBackground = true)
@Composable
internal fun BasalChipTbrPreview() {
    MaterialTheme {
        BasalChip(BasalUiState(visible = true, rateText = "0.75 U/h", statusText = "150% of profile · TBR · 12 min left", direction = BasalDirection.HIGHER), onClick = {})
    }
}

@Preview(showBackground = true)
@Composable
internal fun BasalChipNoDataPreview() {
    MaterialTheme { BasalChip(BasalUiState(visible = true, rateText = "—", statusText = "No recent pump data", isWarning = true), onClick = {}) }
}
