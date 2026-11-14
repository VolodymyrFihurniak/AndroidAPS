package app.aaps.workflow

import app.aaps.core.interfaces.aps.GlucoseStatusAutoIsf
import app.aaps.core.interfaces.overview.graph.BgParabolaData
import app.aaps.core.interfaces.overview.graph.GraphDataPoint

/** autoISF: how far the parabola is drawn into the future, as in aisf 3.2 */
internal const val BG_PARABOLA_EXTRAPOLATION_MINUTES = 20

/**
 * autoISF: the points of the BG parabola that AutoISF fitted to the last BG values.
 *
 * The fit is `BG = a0 + a1 * t + a2 * t²` in mg/dL, with `t` in steps of 5 minutes from the newest BG
 * ([GlucoseStatusAutoIsf.date]), negative into the past. The fitted part covers the
 * [GlucoseStatusAutoIsf.parabolaMinutes] of the fit, the extrapolation goes on for
 * [BG_PARABOLA_EXTRAPOLATION_MINUTES]. One point per minute, so the curve looks round.
 *
 * aisf 3.2 drew the same curve from "now" with a point every 5 minutes. The newest BG is the real
 * start of the fit, so the curve now sits exactly on the readings it was fitted to.
 *
 * Empty when there is no fit ([GlucoseStatusAutoIsf.corrSqu] is 0).
 */
internal fun bgParabolaData(glucoseStatus: GlucoseStatusAutoIsf, fromMgdlToUnits: (Double) -> Double): BgParabolaData {
    if (glucoseStatus.corrSqu <= 0.0) return BgParabolaData.EMPTY
    fun point(minutes: Int): GraphDataPoint {
        val t = minutes / 5.0
        val mgdl = glucoseStatus.a0 + glucoseStatus.a1 * t + glucoseStatus.a2 * t * t
        return GraphDataPoint(timestamp = glucoseStatus.date + minutes * 60_000L, value = fromMgdlToUnits(mgdl))
    }
    val fittedMinutes = glucoseStatus.parabolaMinutes.toInt()
    return BgParabolaData(
        fitted = (-fittedMinutes..0).map(::point),
        extrapolation = (0..BG_PARABOLA_EXTRAPOLATION_MINUTES).map(::point)
    )
}
