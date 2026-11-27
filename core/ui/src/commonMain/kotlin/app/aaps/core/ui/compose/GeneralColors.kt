package app.aaps.core.ui.compose

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Color scheme for general UI elements across the app.
 * Provides consistent color coding for common elements like IOB, COB, BG ranges, etc.
 *
 * **Usage:**
 * - Treatment screens
 * - Overview screen
 * - BG graphs
 * - General UI elements
 *
 * **Color Assignment:**
 * - activeInsulinText: Blue - Active Insulin On Board (IOB) text color
 * - calculator: Green - Bolus calculator icon and related elements
 * - future: Green - Future/scheduled items color
 * - invalidated: Red - Invalid/deleted items color
 * - inProgress: Amber - Items with temporary modifications (e.g., profile with percentage/timeshift)
 * - onInProgress: Dark text color for use on inProgress background
 * - ttEatingSoon: Orange (carbs color) - Eating Soon temp target badge
 * - ttActivity: Blue (exercise color) - Activity temp target badge
 * - ttHypoglycemia: Red (low color) - Hypoglycemia temp target badge
 * - ttCustom: Purple - Custom temp target badge
 * - bgHigh: Orange - BG above high mark
 * - bgInRange: Green - BG within target range
 * - bgLow: Red - BG below low mark
 *
 * Colors match the existing theme attribute colors for consistency with the rest of the app.
 *
 * @property activeInsulinText Color for active insulin (IOB) text
 * @property calculator Color for calculator icon and elements
 * @property futureRecord Color for future/scheduled items
 * @property invalidatedRecord Color for invalid/deleted items
 * @property inProgress Color for items with temporary modifications (matches ribbonWarningColor)
 * @property onInProgress Text color for use on inProgress background (matches ribbonTextWarningColor)
 * @property ttEatingSoon Color for Eating Soon temp target badge
 * @property ttActivity Color for Activity temp target badge
 * @property ttHypoglycemia Color for Hypoglycemia temp target badge
 * @property ttCustom Color for Custom/Automation/Manual temp target badge
 * @property bgHigh Color for BG readings above high mark (matches highColor attr)
 * @property bgInRange Color for BG readings within target range (matches bgInRange attr)
 * @property bgLow Color for BG readings below low mark (matches lowColor attr)
 * @property originalBgValue Color for regular CGM BG readings (white/outlined dots)
 * @property iobPrediction Color for IOB-based BG predictions (blue)
 * @property cobPrediction Color for COB-based BG predictions (orange)
 * @property aCobPrediction Color for absorbed COB predictions (lighter orange)
 * @property uamPrediction Color for UAM (unannounced meals) predictions (yellow)
 * @property ztPrediction Color for zero-temp predictions (cyan)
 * @property bgParabola Color for the autoISF BG parabola on the BG graph (magenta, as in aisf)
 * @property basalIncrease Fill of the basal graph where the basal given is above the profile (autoISF build)
 * @property basalDecrease Fill of the basal graph where the basal given is below the profile (autoISF build)
 * @property graphGrid Color for the grid lines on the overview graphs, translucent so a band drawn under them shows through
 */
data class GeneralColors(
    val activeInsulinText: Color,
    val calculator: Color,
    val futureRecord: Color,
    val invalidatedRecord: Color,
    val statusNormal: Color,
    val statusWarning: Color,
    val statusCritical: Color,
    val inProgress: Color,
    val onInProgress: Color,
    val ttEatingSoon: Color,
    val ttActivity: Color,
    val ttHypoglycemia: Color,
    val ttCustom: Color,
    val adjusted: Color,
    val onAdjusted: Color,
    val onBadge: Color,
    val bgHigh: Color,
    val bgInRange: Color,
    val bgLow: Color,
    val bgVeryLow: Color,
    val bgVeryHigh: Color,
    val bgTargetRangeArea: Color,
    val originalBgValue: Color,
    val iobPrediction: Color,
    val cobPrediction: Color,
    val aCobPrediction: Color,
    val uamPrediction: Color,
    val ztPrediction: Color,
    val bgParabola: Color,
    /** autoISF build: the curve of the personal prediction (experimental) */
    val learnedPrediction: Color,
    val basalIncrease: Color,
    val basalDecrease: Color,
    val graphGrid: Color,
    // Loop mode colors
    val loopClosed: Color,
    val loopOpened: Color,
    val loopDisabled: Color,
    val loopDisconnected: Color,
    val loopLgs: Color,
    val loopSuperBolus: Color,
    // AAPSClient flavor tint colors (for NSClient status card background)
    val flavorClient1Tint: Color,
    val flavorClient2Tint: Color,
    val flavorClient3Tint: Color,
    // Version overlay colors
    val versionCommitted: Color,
    val versionWarning: Color,
    val versionUncommitted: Color,
    // Chart colors
    val cycleAverage: Color,
    // Notification colors
    val notificationUrgent: Color,
    val notificationNormal: Color,
    val notificationLow: Color,
    val notificationInfo: Color,
    val notificationAnnouncement: Color,
    val onNotification: Color,
    // Toggle colors
    val toggleOn: Color
)

/**
 * Light mode color scheme for general elements.
 * Colors match the light theme values from colors.xml.
 *
 * autoISF build: the colours the user sees most (BG ranges, predictions, warnings, loop) are the "Graphite" palette the
 * user chose: all of the same lightness and a little muted, so they do not fight each other. The comments after the
 * values still name the AAPS colour each one replaced.
 */
val LightGeneralColors = GeneralColors(
    activeInsulinText = Color(0xFF2F66D0),  // iob color
    calculator = Color(0xFF1E8A50),          // colorCalculatorButton
    futureRecord = Color(0xFF66BB6A),        // green for scheduled/future items
    invalidatedRecord = Color(0xFFE53935),   // red for invalid/deleted items
    statusNormal = Color(0xFF1E8A50),        // green for normal status
    statusWarning = Color(0xFFA86B00),       // matches highColor attr (orange)
    statusCritical = Color(0xFFC8283E),      // matches lowColor attr (pure red)
    inProgress = Color(0xFFA86B00),          // ribbonWarning - amber for modified/temporary items
    onInProgress = Color(0xFFFFFFFF),        // ribbonTextWarning - dark text on amber background
    ttEatingSoon = Color(0xFFB8620A),        // orange/carbs color for Eating Soon (matches TempTargetDialog)
    ttActivity = Color(0xFF2F66D0),          // blue/exercise color for Activity (matches TempTargetDialog)
    ttHypoglycemia = Color(0xFFC8283E),      // red/low color for Hypoglycemia (matches TempTargetDialog)
    ttCustom = Color(0xFF7A4FC2),            // purple for Custom/Automation/Manual
    adjusted = Color(0xFF1E8A50),            // green for APS-adjusted target chip
    onAdjusted = Color(0xFFFFFFFF),          // white text on adjusted target
    onBadge = Color(0xFFFFFFFF),             // white text on colored badges
    bgHigh = Color(0xFFA86B00),              // orange for high BG (matches @color/high in values)
    bgInRange = Color(0xFF1E8A50),           // pure green for in-range BG (matches @color/inRange)
    bgLow = Color(0xFFC8283E),               // pure red for low BG (matches @color/low)
    bgVeryLow = Color(0xFF8E1A2B),           // dark red for very low BG (Dexcom TIR 5-range)
    bgVeryHigh = Color(0xFFB8620A),          // deep orange-red for very high BG (Dexcom TIR 5-range)
    bgTargetRangeArea = Color(0x201E8A50),   // green with ~16% alpha for target range area (matches @color/inRangeBackground)
    originalBgValue = Color(0xFFFFFFFF),     // white for regular CGM readings (matches originalBgValueColor attr)
    iobPrediction = Color(0xFF2F66D0),       // blue for IOB predictions (matches iobColor attr)
    cobPrediction = Color(0xFFB8620A),       // orange for COB predictions (matches cobColor attr)
    aCobPrediction = Color(0x80B8620A),      // lighter orange for absorbed COB (50% alpha)
    uamPrediction = Color(0xFF9A8420),       // yellow-ish for UAM predictions (matches uamColor attr)
    ztPrediction = Color(0xFF0E8282),        // cyan for zero-temp predictions (matches ztColor attr)
    bgParabola = Color(0xFFB0409E),          // magenta for the autoISF BG parabola (bgParabolaColor of aisf)
    learnedPrediction = Color(0xFF7E57C2),   // deep purple for the personal prediction
    basalIncrease = Color(0x66B8620A),       // orange at 40%: more basal than the profile
    basalDecrease = Color(0x662F66D0),       // blue at 40%: less basal than the profile
    graphGrid = Color(0x80CAC4D0),           // Material outlineVariant at 50%
    // Loop mode colors
    loopClosed = Color(0xFF1E8A50),          // green for closed loop
    loopOpened = Color(0xFF2F66D0),          // blue for open loop
    loopDisabled = Color(0xFFC8283E),        // red for disabled/paused loop
    loopDisconnected = Color(0xFF8A909C),    // gray for disconnected pump
    loopLgs = Color(0xFF7A4FC2),             // purple for LGS mode
    loopSuperBolus = Color(0xFFB8620A),      // orange for super bolus
    flavorClient1Tint = Color(0x30E8C50C),   // yellow tint (AAPSClient) — alpha ~19%
    flavorClient2Tint = Color(0x300FBBE0),   // blue tint (AAPSClient2)
    flavorClient3Tint = Color(0x304CAF50),   // green tint (AAPSClient3)
    versionCommitted = Color(0xFFB2B2B2),    // gray for official/committed builds (matches omniGrayColor)
    versionWarning = Color(0xFFFF8C00),      // orange for newer version available (matches metadataTextWarningColor)
    versionUncommitted = Color(0xFFFF4444),  // red for uncommitted dev builds (matches urgentColor/alarm)
    cycleAverage = Color(0xFF2E7D32),         // dark green for cycle pattern average line
    // Notification colors (same in both modes — high contrast on any background)
    notificationUrgent = Color(0xFFFF0400),       // red for urgent notifications
    notificationNormal = Color(0xFFFF5E55),       // salmon for normal notifications
    notificationLow = Color(0xFFFF827C),          // pink-red for low priority
    notificationInfo = Color(0xFF009705),         // green for info notifications
    notificationAnnouncement = Color(0xFFFF8C00), // orange for announcements
    onNotification = Color(0xFFFFFFFF),            // white text on notification backgrounds
    toggleOn = Color(0xFF1E8A50)                    // green for active/selected toggles
)

/**
 * Dark mode color scheme for general elements.
 * Colors match the dark theme values from colors.xml (night folder).
 *
 * autoISF build: the colours the user sees most (BG ranges, predictions, warnings, loop) are the "Graphite" palette the
 * user chose: all of the same lightness and a little muted, so they do not fight each other. The comments after the
 * values still name the AAPS colour each one replaced.
 */
val DarkGeneralColors = GeneralColors(
    activeInsulinText = Color(0xFF79A8FF),  // iob color (same in both modes)
    calculator = Color(0xFF5FD08E),          // colorCalculatorButton (night)
    futureRecord = Color(0xFF6AE86D),        // green for scheduled/future items (night)
    invalidatedRecord = Color(0xFFEF5350),   // red for invalid/deleted items (night)
    statusNormal = Color(0xFF5FD08E),        // lighter green for dark mode
    statusWarning = Color(0xFFE9B949),       // matches highColor attr night (yellow)
    statusCritical = Color(0xFFF2667A),      // matches lowColor attr (pure red)
    inProgress = Color(0xFFE9B949),          // ribbonWarning - same in both modes
    onInProgress = Color(0xFF1A1406),        // ribbonTextWarning - same in both modes
    ttEatingSoon = Color(0xFFEFA35E),        // lighter orange/carbs for Eating Soon (dark mode)
    ttActivity = Color(0xFF79A8FF),          // lighter blue/exercise for Activity (dark mode)
    ttHypoglycemia = Color(0xFFF2667A),      // lighter red/low for Hypoglycemia (dark mode)
    ttCustom = Color(0xFFB48CF0),            // lighter purple for Custom/Automation/Manual (dark mode)
    adjusted = Color(0xFF5FD08E),            // lighter green for APS-adjusted target chip (dark mode)
    onAdjusted = Color(0xFF06140C),          // dark text on adjusted target (dark mode)
    onBadge = Color(0xFFFFFFFF),             // white text on colored badges
    bgHigh = Color(0xFFE9B949),              // yellow for high BG (dark mode, matches @color/high in values-night)
    bgInRange = Color(0xFF5FD08E),           // pure green for in-range BG (matches @color/inRange - same in both modes)
    bgLow = Color(0xFFF2667A),               // pure red for low BG (matches @color/low - same in both modes)
    bgVeryLow = Color(0xFFC2445A),           // dark red for very low BG (dark mode, Dexcom TIR 5-range)
    bgVeryHigh = Color(0xFFEFA35E),          // orange for very high BG (dark mode, differentiates from yellow bgHigh)
    bgTargetRangeArea = Color(0x245FD08E),   // green with ~25% alpha for target range area (matches @color/inRangeBackground in values-night)
    originalBgValue = Color(0xFFFFFFFF),     // white for regular CGM readings (same in both modes)
    iobPrediction = Color(0xFF79A8FF),       // lighter blue for IOB predictions (dark mode)
    cobPrediction = Color(0xFFEFA35E),       // lighter orange for COB predictions (dark mode)
    aCobPrediction = Color(0x80EFA35E),      // lighter orange for absorbed COB (50% alpha, dark mode)
    uamPrediction = Color(0xFFD9C77A),       // lighter yellow for UAM predictions (dark mode)
    ztPrediction = Color(0xFF56C6C6),        // lighter cyan for zero-temp predictions (dark mode)
    bgParabola = Color(0xFFE07BE0),          // magenta for the autoISF BG parabola (same in both modes, as in aisf)
    learnedPrediction = Color(0xFFB39DDB),   // lighter purple for the personal prediction (dark mode)
    basalIncrease = Color(0x66EFA35E),       // lighter orange at 40% for dark mode
    basalDecrease = Color(0x6679A8FF),       // lighter blue at 40% for dark mode
    graphGrid = Color(0x40938F99),           // Material outline at 25%: outlineVariant at 50% was barely visible on the dark surface
    // Loop mode colors (same in both modes)
    loopClosed = Color(0xFF5FD08E),          // green for closed loop
    loopOpened = Color(0xFF79A8FF),          // blue for open loop
    loopDisabled = Color(0xFFF2667A),        // red for disabled/paused loop
    loopDisconnected = Color(0xFF8A90A0),    // gray for disconnected pump
    loopLgs = Color(0xFFB48CF0),             // purple for LGS mode
    loopSuperBolus = Color(0xFFEFA35E),      // orange for super bolus
    flavorClient1Tint = Color(0x30E8C50C),   // yellow tint (AAPSClient)
    flavorClient2Tint = Color(0x300FBBE0),   // blue tint (AAPSClient2)
    flavorClient3Tint = Color(0x304CAF50),   // green tint (AAPSClient3)
    versionCommitted = Color(0xFFB2B2B2),    // gray for official/committed builds
    versionWarning = Color(0xFFFF8C00),      // orange for newer version available
    versionUncommitted = Color(0xFFFF4444),  // red for uncommitted dev builds
    cycleAverage = Color(0xFF66BB6A),         // lighter green for cycle pattern average line (dark mode)
    // Notification colors (same values — high contrast on dark background)
    notificationUrgent = Color(0xFFFF0400),
    notificationNormal = Color(0xFFFF5E55),
    notificationLow = Color(0xFFFF827C),
    notificationInfo = Color(0xFF009705),
    notificationAnnouncement = Color(0xFFFF8C00),
    onNotification = Color(0xFFFFFFFF),
    toggleOn = Color(0xFF5FD08E)                    // lighter green for active/selected toggles (dark mode)
)

/**
 * CompositionLocal providing general colors based on current theme (light/dark).
 * Accessed via AapsTheme.generalColors in composables.
 */
internal val LocalGeneralColors = compositionLocalOf { LightGeneralColors }
