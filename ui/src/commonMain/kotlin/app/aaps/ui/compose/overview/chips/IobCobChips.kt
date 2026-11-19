package app.aaps.ui.compose.overview.chips

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import app.aaps.core.ui.compose.AapsSpacing

/**
 * IOB, COB and (autoISF build) the basal card in one row.
 *
 * @see IobCobChipsRowPreview
 * @see IobCobChipsRowCarbsReqPreview
 */
@Composable
fun IobCobChipsRow(
    iobUiState: IobUiState,
    cobUiState: CobUiState,
    onIobChipClick: () -> Unit,
    modifier: Modifier = Modifier,
    basalUiState: BasalUiState = BasalUiState(),
    onBasalChipClick: () -> Unit = {}
) {
    val spacingDp = AapsSpacing.small
    val showBasal = basalUiState.visible
    SubcomposeLayout(
        modifier = modifier.fillMaxWidth()
    ) { constraints ->
        val spacingPx = spacingDp.roundToPx()
        val chipCount = if (showBasal) 3 else 2
        val isWidthBounded = constraints.hasBoundedWidth
        val availableWidth = if (isWidthBounded) (constraints.maxWidth - (chipCount - 1) * spacingPx).coerceAtLeast(0) else 0

        @Composable
        fun chips(icons: Boolean) {
            IobChip(state = iobUiState, onClick = onIobChipClick, showIcon = icons)
            CobChip(state = cobUiState, showIcon = icons)
            if (showBasal) BasalChip(state = basalUiState, onClick = onBasalChipClick, showIcon = icons)
        }

        // First pass: measure intrinsic widths with icons
        val withIcons = subcompose("withIcons") { chips(icons = true) }
        val intrinsicsWithIcons = withIcons.map { it.maxIntrinsicWidth(constraints.maxHeight) }
        // IOB and COB decide, as before the basal card: its status line scrolls and its rate is short
        val totalWithIcons = intrinsicsWithIcons.take(2).sum()

        // If chips with icons don't fit, hide icons to free up space. Not while carbs are required:
        // the blinking COB icon is the cue, and the "16req" text is what made the row too narrow in
        // the first place, so the cue would vanish exactly when it is needed. The COB text scrolls
        // instead (see CobChip).
        val showIcons = cobUiState.carbsReq > 0 || !isWidthBounded || totalWithIcons <= availableWidth

        val measurables = if (showIcons) withIcons else subcompose("withoutIcons") { chips(icons = false) }

        // Max intrinsic width = the width each chip needs to render on a single line.
        // (min intrinsic only guarantees the longest word fits, which wraps IOB.)
        val naturalWidths = measurables.map { it.maxIntrinsicWidth(constraints.maxHeight) }

        val widths = if (isWidthBounded) {
            // IOB hugs its content but never grabs more than half the row when space is tight.
            val iobWidth = naturalWidths[0].coerceAtMost(availableWidth / 2)
            // The basal card hugs its content too, at most half of what IOB left; its status line scrolls.
            val basalWidth = if (showBasal) naturalWidths[2].coerceAtMost((availableWidth - iobWidth) / 2) else 0
            // COB fills the rest of the row up to the edge so the chips span the full width;
            // its text marquee-scrolls when it can't fit (see CobChip).
            val cobWidth = (availableWidth - iobWidth - basalWidth).coerceAtLeast(0)
            listOfNotNull(iobWidth, cobWidth, basalWidth.takeIf { showBasal })
        } else naturalWidths

        // Every chip as high as the highest one: the basal card has two lines, and the row must look like one row
        // (a measurable may be measured only once, so the height comes from the intrinsics)
        val height = measurables.mapIndexed { i, m -> m.maxIntrinsicHeight(widths[i]) }.maxOrNull() ?: 0
        val placeables = measurables.mapIndexed { i, m -> m.measure(constraints.copy(minWidth = widths[i], maxWidth = widths[i], minHeight = height, maxHeight = height)) }

        val layoutWidth = if (isWidthBounded) constraints.maxWidth else placeables.sumOf { it.width } + (if (placeables.size > 1) (placeables.size - 1) * spacingPx else 0)
        layout(layoutWidth, height) {
            var x = 0
            placeables.forEachIndexed { i, placeable ->
                placeable.place(x, 0)
                x += placeable.width + if (i < placeables.lastIndex) spacingPx else 0
            }
        }
    }
}
