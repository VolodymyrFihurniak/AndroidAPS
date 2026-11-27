package app.aaps.core.data.model

/**
 * Sealed class representing individual actions that can be part of a Scene.
 * Each action maps to a specific system operation (TT, profile switch, etc.).
 */
sealed class SceneAction {

    /**
     * Set a temporary target.
     * @param reason TT reason category
     * @param targetMgdl Target value in mg/dL (converted at display time)
     */
    data class TempTarget(
        val reason: TT.Reason,
        val targetMgdl: Double
    ) : SceneAction()

    /**
     * Switch to a named profile with optional percentage and time shift.
     * @param profileName Name of the profile to switch to
     * @param percentage Basal percentage (100 = no change)
     * @param timeShiftHours Time shift in hours (0 = no shift)
     */
    data class ProfileSwitch(
        val profileName: String,
        val percentage: Int = 100,
        val timeShiftHours: Int = 0
    ) : SceneAction()

    /**
     * Toggle SMB on or off.
     * @param enabled Whether SMB should be enabled
     */
    data class SmbToggle(
        val enabled: Boolean
    ) : SceneAction()

    /**
     * Change the loop running mode.
     * @param mode Target running mode
     */
    data class LoopModeChange(
        val mode: RM.Mode
    ) : SceneAction()

    /**
     * Create a CarePortal therapy event entry.
     * @param type Therapy event type (Exercise, Sickness, etc.)
     * @param note Optional note text
     */
    data class CarePortalEvent(
        val type: TE.Type,
        val note: String = ""
    ) : SceneAction()

    /**
     * autoISF build: loop settings while the scene runs, like the overrides of iAPS
     * (github.com/Artificial-Pancreas/iAPS, MIT licence). Only the AutoISF loop reads them, they end with the scene.
     * A null or 100 % value means "no change". The end conditions end the scene early, like its duration does.
     *
     * @param isfPercent ISF in % of what the loop would use (120 = ISF 20 % higher, so less insulin)
     * @param crPercent carb ratio in % (120 = less insulin for carbs)
     * @param basalPercent the basal the loop works from, in % (only while the loop runs closed)
     * @param maxIob max IOB in U instead of the setting, still limited by the hard limit
     * @param smbMinutes max minutes of basal to limit SMB
     * @param uamMinutes max minutes of basal to limit SMB for UAM
     * @param autoIsfWeightsOff run without the AutoISF weights (the ISF only follows autosens and temp target)
     * @param iobThPercent iobTH in % of max IOB
     * @param endOnCarbs end the scene when carbs are entered after it started
     * @param endAboveMgdl end the scene when BG goes above this value
     * @param endBelowMgdl end the scene when BG goes below this value
     */
    data class LoopSettings(
        val isfPercent: Int = 100,
        val crPercent: Int = 100,
        val basalPercent: Int = 100,
        val maxIob: Double? = null,
        val smbMinutes: Int? = null,
        val uamMinutes: Int? = null,
        val autoIsfWeightsOff: Boolean = false,
        val iobThPercent: Int? = null,
        val endOnCarbs: Boolean = false,
        val endAboveMgdl: Double? = null,
        val endBelowMgdl: Double? = null
    ) : SceneAction() {

        /** true when this scene should end now */
        fun shouldEnd(bgMgdl: Double?, carbsSinceStart: Double): Boolean =
            (endOnCarbs && carbsSinceStart > 0.0) ||
                (bgMgdl != null && endAboveMgdl != null && bgMgdl > endAboveMgdl) ||
                (bgMgdl != null && endBelowMgdl != null && bgMgdl < endBelowMgdl)

        val hasEndCondition: Boolean get() = endOnCarbs || endAboveMgdl != null || endBelowMgdl != null

        companion object {

            const val MIN_PERCENT = 50
            const val MAX_PERCENT = 200
        }
    }
}
