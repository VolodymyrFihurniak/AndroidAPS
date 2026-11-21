package app.aaps.plugins.aps.learned.compose

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.plugins.aps.learned.LearnedPredictionPlugin

/** autoISF build: the screen of the personal prediction: its state, its errors, what it learned and its log */
internal class LearnedPredictionScreen(private val plugin: LearnedPredictionPlugin) : ComposablePluginContent {

    @Composable
    override fun Render(
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)?
    ) {
        val status by plugin.status.collectAsStateWithLifecycle()
        Text(status.state.name)
    }
}
