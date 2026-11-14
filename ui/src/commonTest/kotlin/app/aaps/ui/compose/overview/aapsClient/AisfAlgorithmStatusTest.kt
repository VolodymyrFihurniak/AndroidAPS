package app.aaps.ui.compose.overview.aapsClient

import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.aps.RT
import kotlin.test.Test
import kotlin.test.assertEquals

class AisfAlgorithmStatusTest {

    @Test
    fun autoIsfIsNamedAutoIsf() {
        // AutoISF also sets runningDynamicIsf, the algorithm wins
        assertEquals("AutoISF", aisfAlgorithmName(RT(algorithm = APSResult.Algorithm.AUTO_ISF, runningDynamicIsf = true, runningAutoIsf = true)))
    }

    @Test
    fun smbWithDynamicIsfIsNamedDynIsf() {
        assertEquals("DynISF", aisfAlgorithmName(RT(algorithm = APSResult.Algorithm.SMB, runningDynamicIsf = true)))
    }

    @Test
    fun otherAlgorithmsKeepTheirName() {
        assertEquals("SMB", aisfAlgorithmName(RT(algorithm = APSResult.Algorithm.SMB, runningDynamicIsf = false)))
        assertEquals("AMA", aisfAlgorithmName(RT(algorithm = APSResult.Algorithm.AMA, runningDynamicIsf = false)))
    }
}
