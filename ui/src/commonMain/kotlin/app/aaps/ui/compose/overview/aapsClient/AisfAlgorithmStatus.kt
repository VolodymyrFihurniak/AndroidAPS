package app.aaps.ui.compose.overview.aapsClient

import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.aps.RT

/**
 * autoISF: the name of the algorithm of a loop result of the master, for the algorithm status of
 * AAPSClient. As in aisf, SMB with dynamic ISF is shown as "DynISF". It is read from the result
 * itself, not from the settings of the client as aisf did.
 */
internal fun aisfAlgorithmName(result: RT): String = when {
    result.algorithm == APSResult.Algorithm.AUTO_ISF                        -> "AutoISF"
    result.algorithm == APSResult.Algorithm.SMB && result.runningDynamicIsf -> "DynISF"
    else                                                                    -> result.algorithm.name
}
