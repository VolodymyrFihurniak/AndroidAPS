package app.aaps.plugins.aps.openAPSAutoISF

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AutoIsfExtrasTest {

    private val keto = AutoIsfExtras.KetoSettings(enabled = true, onlyWithNegativeIob = true, percent = 20, absoluteRate = 0.0)

    private fun ketoRate(
        settings: AutoIsfExtras.KetoSettings = keto,
        iob: Double = -1.5,
        basal: Double = 1.0,
        wanted: Double = 0.0,
        smb: Double = 0.0,
        bg: Double = 140.0,
        minBg: Double = 100.0,
        maxBasal: Double = 3.0
    ) = AutoIsfExtras.ketoRate(settings, iob, basal, wanted, smb, bg, minBg, maxBasal)

    @Test fun ketoGivesPercentOfBasalInsteadOfZeroTemp() = assertEquals(0.2, ketoRate())

    @Test fun ketoIsOffByDefault() = assertNull(ketoRate(settings = keto.copy(enabled = false)))

    @Test fun ketoNeedsIobBelowMinusOneHourOfBasal() {
        assertNull(ketoRate(iob = -0.9))
        assertNull(ketoRate(iob = -1.0))
        assertNotNull(ketoRate(iob = -1.01))
    }

    @Test fun ketoAlwaysModeIgnoresIob() = assertEquals(0.2, ketoRate(settings = keto.copy(onlyWithNegativeIob = false), iob = 2.0))

    @Test fun ketoDoesNothingWhenLoopGivesInsulin() {
        assertNull(ketoRate(wanted = 0.05))
        assertNull(ketoRate(smb = 0.1))
    }

    @Test fun ketoNeverActsBelowTarget() = assertNull(ketoRate(bg = 99.0))

    @Test fun ketoLimitsPercentAndAbsolute() {
        assertEquals(0.5, ketoRate(settings = keto.copy(percent = 90)))
        assertEquals(0.05, ketoRate(settings = keto.copy(percent = 1)))
        assertEquals(2.0, ketoRate(settings = keto.copy(absoluteRate = 5.0)))
        assertEquals(0.3, ketoRate(settings = keto.copy(absoluteRate = 0.3)))
    }

    @Test fun ketoNeverAboveMaxBasal() = assertEquals(0.1, ketoRate(settings = keto.copy(absoluteRate = 1.0), maxBasal = 0.1))

    private val b30 = AutoIsfExtras.B30Settings(
        enabled = true, minBolus = 1.5, targetBelowMgdl = 100.0, upperBgMgdl = 130.0, upperDeltaMgdl = 8.0, factor = 5.0, durationMinutes = 30
    )

    private fun b30(
        tt: Double? = 80.0,
        bolus: Double = 4.0,
        minutes: Double? = 10.0,
        basal: Double = 0.8,
        wanted: Double = 1.2,
        bg: Double = 150.0,
        delta: Double = 5.0,
        maxBasal: Double = 5.0
    ) = AutoIsfExtras.b30(b30, tt, bolus, minutes, basal, wanted, bg, delta, maxBasal)

    @Test fun b30RunsAfterMealBolusWithLowTt() {
        val r = b30()
        assertNotNull(r)
        assertEquals(4.0, r.rate)
        assertEquals(20, r.minutesLeft)
        assertEquals(false, r.stopSmb)
    }

    @Test fun b30NeedsLowTempTarget() {
        assertNull(b30(tt = null))
        assertNull(b30(tt = 100.0))
    }

    @Test fun b30NeedsBigEnoughRecentBolus() {
        assertNull(b30(bolus = 1.0))
        assertNull(b30(minutes = 31.0))
        assertNull(b30(minutes = null))
    }

    @Test fun b30NeverLowersOrOverridesHoldingBack() {
        // loop holds back insulin (rate below basal): B30 must not run
        assertNull(b30(wanted = 0.0))
        assertNull(b30(wanted = 0.5))
        // loop already wants more than B30
        assertNull(b30(wanted = 4.5))
    }

    @Test fun b30LimitedByMaxBasal() = assertEquals(2.0, b30(maxBasal = 2.0)?.rate)

    @Test fun b30StopsSmbWhileBgLowAndFlat() {
        assertTrue(b30(bg = 120.0, delta = 3.0)!!.stopSmb)
        assertEquals(false, b30(bg = 120.0, delta = 10.0)!!.stopSmb)
    }

    @Test fun autoCrFollowsIsfFactor() {
        // ISF 50 -> 40 (AutoISF factor 1.25) makes CR 10 -> 8
        assertEquals(8.0, AutoIsfExtras.autoCr(profileCr = 10.0, profileIsf = 50.0, autoIsf = 40.0))
        assertEquals(10.0, AutoIsfExtras.autoCr(profileCr = 10.0, profileIsf = 50.0, autoIsf = 50.0))
        assertEquals(10.0, AutoIsfExtras.autoCr(profileCr = 10.0, profileIsf = 0.0, autoIsf = 40.0))
        assertEquals(10.0, AutoIsfExtras.autoCr(profileCr = 10.0, profileIsf = 50.0, autoIsf = Double.NaN))
    }
}
