package app.aaps.ui.compose.overview.chips

import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.plugin.ActivePlugin

/** autoISF: the algorithms that read the exercise mode setting ("high temp target raises sensitivity") */
private val EXERCISE_MODE_ALGORITHMS = setOf(APSResult.Algorithm.AUTO_ISF, APSResult.Algorithm.SMB)

/**
 * autoISF: whether the exercise mode means anything on this phone. Only the master runs an APS, and
 * of the algorithms only AutoISF and SMB read the setting. Used by the overview button and the widget,
 * so both show it in the same cases.
 */
fun exerciseModeApplies(config: Config, activePlugin: ActivePlugin): Boolean =
    config.APS && activePlugin.activeAPS?.algorithm?.let { it in EXERCISE_MODE_ALGORITHMS } == true
