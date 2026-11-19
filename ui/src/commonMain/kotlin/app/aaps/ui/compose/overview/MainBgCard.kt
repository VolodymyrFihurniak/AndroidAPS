package app.aaps.ui.compose.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.overview.graph.BgInfoData
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsCard
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalProfileUtil
import app.aaps.core.ui.compose.icons.IcHistory
import app.aaps.core.ui.compose.stringResource
import app.aaps.core.ui.extensions.directionToIcon
import app.aaps.ui.UiStrings
import app.aaps.ui.compose.components.bgSpokenDescription

/**
 * autoISF build: the main card of the overview on a phone, from the concept of the user. Left: the BG value in the
 * colour of its range with its unit, the trend in words, how old the reading is, and the deltas of 5, 15 and 40
 * minutes. Right: IOB and COB. A bar in the colour of the range runs along the left edge.
 *
 * It replaces the BG circle on a phone; tablets keep the circle. The values are the ones the circle shows
 * ([BgInfoData]), nothing is worked out here.
 */
@Composable
fun MainBgCard(
    bgInfo: BgInfoData?,
    timeAgoText: String,
    iobText: String,
    cobText: String,
    onIobClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rangeColor = bgInfo?.bgRange?.toColor() ?: MaterialTheme.colorScheme.outline
    val unit = stringResource(if (LocalProfileUtil.current.units == GlucoseUnit.MGDL) CoreUiStrings.mgdl else CoreUiStrings.mmol)
    AapsCard(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(AapsSpacing.small)
                    .fillMaxHeight()
                    .background(rangeColor)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(AapsSpacing.large)
            ) {
                BgLine(bgInfo, timeAgoText, unit)
                HorizontalDivider(modifier = Modifier.padding(vertical = AapsSpacing.medium))
                Row(modifier = Modifier.fillMaxWidth()) {
                    DeltaCell(5, bgInfo?.deltaText, Modifier.weight(1f))
                    DeltaCell(15, bgInfo?.shortAvgDeltaText, Modifier.weight(1f))
                    DeltaCell(40, bgInfo?.longAvgDeltaText, Modifier.weight(1f))
                }
            }
            VerticalDivider(modifier = Modifier.padding(vertical = AapsSpacing.large))
            Column(
                verticalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .fillMaxHeight()
                    .padding(horizontal = AapsSpacing.large, vertical = AapsSpacing.medium)
            ) {
                SideValue(stringResource(CoreUiStrings.iob), iobText, Modifier.clickable(onClick = onIobClick))
                HorizontalDivider()
                SideValue(stringResource(CoreUiStrings.cob), cobText)
            }
        }
    }
}

@Composable
private fun BgLine(bgInfo: BgInfoData?, timeAgoText: String, unit: String) {
    if (bgInfo == null) {
        Text(text = "---", style = AapsTheme.typography.bgValue, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val color = bgInfo.bgRange.toColor()
    // One stop for a screen reader, as the BG circle: value, range, trend, delta and age in one sentence
    val description = bgSpokenDescription(
        bgText = bgInfo.bgText,
        range = bgInfo.bgRange,
        trend = bgInfo.trendDescription,
        deltaText = bgInfo.deltaText,
        timeAgo = timeAgoText,
        isOutdated = bgInfo.isOutdated
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = description }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = bgInfo.bgText,
                style = AapsTheme.typography.bgValue,
                color = color,
                textDecoration = if (bgInfo.isOutdated) TextDecoration.LineThrough else TextDecoration.None
            )
            Text(text = unit, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.width(AapsSpacing.large))
        Column(verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                bgInfo.trendArrow?.let {
                    Icon(imageVector = it.directionToIcon(), contentDescription = null, tint = color, modifier = Modifier.size(AapsSpacing.chipIconSize))
                }
                Text(
                    text = bgInfo.trendDescription,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = color,
                    maxLines = 1,
                    modifier = Modifier
                        .padding(start = AapsSpacing.small)
                        .basicMarquee()
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = IcHistory,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(AapsSpacing.chipIconSize)
                )
                Text(
                    text = timeAgoText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = AapsSpacing.small)
                )
            }
        }
    }
}

@Composable
private fun DeltaCell(minutes: Int, value: String?, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(
            text = stringResource(UiStrings.delta_minutes, minutes),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value ?: stringResource(UiStrings.basal_unknown),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

@Composable
private fun SideValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(vertical = AapsSpacing.small)) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}
