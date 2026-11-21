package app.aaps.ui.compose.testing

import app.aaps.core.interfaces.aps.LearnedForecast
import app.aaps.core.interfaces.aps.LearnedPrediction
import kotlinx.coroutines.flow.MutableStateFlow

/** autoISF build: the personal prediction for tests, off unless a test switches it on */
class FakeLearnedPrediction(
    override val enabled: MutableStateFlow<Boolean> = MutableStateFlow(false),
    override val forecast: MutableStateFlow<LearnedForecast?> = MutableStateFlow(null)
) : LearnedPrediction {

    override suspend fun forecastNow(): LearnedForecast? = forecast.value?.takeIf { enabled.value }
}
