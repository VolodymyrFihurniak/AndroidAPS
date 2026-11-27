package app.aaps.core.ui.compose.navigation

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Color palette for UI elements.
 * Provides consistent color coding for element types, display indicators, and graph overlays.
 * Action-type colors are mapped via [color]; graph-only colors are used directly.
 */
data class ElementColors(
    val insulin: Color,
    val carbs: Color,
    val extendedBolus: Color,
    val tempBasal: Color,
    val tempTarget: Color,
    val profileSwitch: Color,
    val careportal: Color, // Deprecated: use note or question instead. Remove after migration.
    val runningMode: Color,
    val userEntry: Color,
    val loop: Color,
    val pump: Color,
    val aaps: Color,
    val cgmXdrip: Color,
    val cgmDex: Color,
    val calibration: Color,
    val quickWizard: Color,
    val bgCheck: Color,
    val exercise: Color,
    val announcement: Color,
    val cob: Color,
    val sensitivity: Color,
    val automation: Color,
    // New fields for ElementType
    val bolusWizard: Color,
    val note: Color,
    val question: Color,
    val deviceMaintenance: Color,
    val siteRotation: Color,
    val settings: Color,
    // Navigation screens
    val treatments: Color,
    val statistics: Color,
    val scene: Color,               // scenes / situation presets
    val navigation: Color,          // history browser, setup wizard, maintenance, configuration
    // Graph overlay colors (no ElementType)
    val activity: Color,
    // Running mode belt graph background colors (no ElementType)
    val loopClosed: Color,
    val loopOpened: Color,
    val loopLgs: Color,
    val loopDisabled: Color,
    val loopSuperBolus: Color,
    val loopDisconnected: Color,
    val loopSuspended: Color
)

/**
 * Light mode color scheme for basic elements.
 * Colors match the light theme values from colors.xml.
 *
 * autoISF build: the colours the user sees most (BG ranges, predictions, warnings, loop) are the "Graphite" palette the
 * user chose: all of the same lightness and a little muted, so they do not fight each other. The comments after the
 * values still name the AAPS colour each one replaced.
 */
val LightElementColors = ElementColors(
    insulin = Color(0xFF2F66D0),         // insulin - bolus
    carbs = Color(0xFFB8620A),           // colorCarbsButton
    extendedBolus = Color(0xFFCF8BFE),   // extendedBolus
    tempBasal = Color(0xFF0E8282),       // actionBasal
    tempTarget = Color(0xFF1E8A50),      // tempTargetConfirmation
    profileSwitch = Color(0xFF000000),   // profileSwitch (black for light mode)
    careportal = Color(0xFFFEAF05),
    runningMode = Color(0xFFFFB400),     // hardcoded in drawable
    userEntry = Color(0xFF66BB6A),       // userOption
    loop = Color(0xFF1E8A50),            // loop
    pump = Color(0xFF5F6675),            // pump
    aaps = Color(0xFF666666),            // AAPS
    cgmXdrip = Color(0xFFE93057),        // colorCalibrationButton
    cgmDex = Color(0xFF777777),          // byodaGray
    calibration = Color(0xFFE93057),     // colorCalibrationButton
    quickWizard = Color(0xFFB8620A),     // colorQuickWizardButton
    bgCheck = Color(0xFFE93057),         // calibrationButtonColor
    exercise = Color(0xFF2F66D0),        // exercise
    announcement = Color(0xFFCF8BFE),    // announcement
    cob = Color(0xFFB8620A),             // deep orange — distinct from COB line (#FB8C00)
    sensitivity = Color(0xFF0E8282),     // teal — autosens icon color
    automation = Color(0xFF66BB6A),      // green — same as userEntry light
    bolusWizard = Color(0xFF1E8A50),     // moved from generalColors.calculator
    note = Color(0xFFFEAF05),            // was careportal
    question = Color(0xFFFF9800),        // distinct from note — amber/orange
    deviceMaintenance = Color(0xFF78909C), // blue-grey — sensor/battery/cannula
    siteRotation = Color(0xFF5C6BC0),    // indigo
    settings = Color(0xFF546E7A),         // blue-grey 600 — distinct from pump grey
    scene = Color(0xFF7B1FA2),             // purple 700 — distinct from all other element colors
    // Navigation screens
    treatments = Color(0xFF00897B),       // teal 600
    statistics = Color(0xFF5C6BC0),       // indigo 400
    navigation = Color(0xFF607D8B),       // blue-grey 500
    // Graph overlay colors
    activity = Color(0xFFD3F166),         // activity — yellow-green
    // Running mode belt graph background colors
    loopClosed = Color(0xFF1E8A50),       // green — normal operating state
    loopOpened = Color(0xFF2F66D0),       // blue
    loopLgs = Color(0xFF7A4FC2),          // purple
    loopDisabled = Color(0xFFC8283E),     // red
    loopSuperBolus = Color(0xFFB8620A),   // orange
    loopDisconnected = Color(0xFF8A909C), // gray
    loopSuspended = Color(0xFFA86B00),    // yellow
)

/**
 * Dark mode color scheme for basic elements.
 * Colors match the dark theme values from colors.xml (night folder).
 *
 * autoISF build: the colours the user sees most (BG ranges, predictions, warnings, loop) are the "Graphite" palette the
 * user chose: all of the same lightness and a little muted, so they do not fight each other. The comments after the
 * values still name the AAPS colour each one replaced.
 */
val DarkElementColors = ElementColors(
    insulin = Color(0xFF79A8FF),         // insulin - bolus
    carbs = Color(0xFFEFA35E),           // colorCarbsButton (night)
    extendedBolus = Color(0xFFCF8BFE),   // extendedBolus (night)
    tempBasal = Color(0xFF56C6C6),       // actionBasal
    tempTarget = Color(0xFF5FD08E),      // tempTargetConfirmation (night)
    profileSwitch = Color(0xFFFFFFFF),   // profileSwitch (white for dark mode)
    careportal = Color(0xFFFEAF05),
    runningMode = Color(0xFFFFB400),     // hardcoded in drawable
    userEntry = Color(0xFF6AE86D),       // userOption (night)
    loop = Color(0xFF5FD08E),            // loop
    pump = Color(0xFF8A90A0),            // pump
    aaps = Color(0xFFBBBBBB),            // AAPS
    cgmXdrip = Color(0xFFE93057),        // colorCalibrationButton
    cgmDex = Color(0xFF999999),          // byodaGray (night)
    calibration = Color(0xFFE93057),     // colorCalibrationButton
    quickWizard = Color(0xFFEFA35E),     // colorQuickWizardButton (night)
    bgCheck = Color(0xFFE93057),         // calibrationButtonColor
    exercise = Color(0xFF79A8FF),        // exercise
    announcement = Color(0xFFCF8BFE),    // announcement
    cob = Color(0xFFEFA35E),             // soft salmon — distinct from COB line (#FFB74D)
    sensitivity = Color(0xFF56C6C6),     // teal — autosens icon color
    automation = Color(0xFF6AE86D),      // green — same as userEntry dark
    bolusWizard = Color(0xFF5FD08E),     // moved from generalColors.calculator (night)
    note = Color(0xFFFEAF05),            // was careportal
    question = Color(0xFFFFB74D),        // distinct from note — warm amber
    deviceMaintenance = Color(0xFF90A4AE), // blue-grey (night)
    siteRotation = Color(0xFF7986CB),    // indigo (night)
    settings = Color(0xFF78909C),         // blue-grey 400 (night)
    scene = Color(0xFFCE93D8),             // purple 200 (night) — distinct from all other element colors
    // Navigation screens
    treatments = Color(0xFF26A69A),       // teal 400 (night)
    statistics = Color(0xFF7986CB),       // indigo 300 (night)
    navigation = Color(0xFF90A4AE),       // blue-grey 300 (night)
    // Graph overlay colors
    activity = Color(0xFFD3F166),         // activity — yellow-green
    // Running mode belt graph background colors
    loopClosed = Color(0xFF5FD08E),       // green — normal operating state
    loopOpened = Color(0xFF79A8FF),       // blue
    loopLgs = Color(0xFFB48CF0),          // purple
    loopDisabled = Color(0xFFF2667A),     // red
    loopSuperBolus = Color(0xFFEFA35E),   // orange
    loopDisconnected = Color(0xFF8A90A0), // gray
    loopSuspended = Color(0xFFE9B949),    // yellow
)

/**
 * CompositionLocal providing treatment icon colors based on current theme (light/dark).
 * Accessed via AapsTheme.treatmentIconColors in composables.
 */
internal val LocalElementColors = compositionLocalOf { LightElementColors }
