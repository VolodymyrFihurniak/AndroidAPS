package app.aaps.ui.food

import androidx.compose.runtime.Composable

/**
 * autoISF build: a function that opens the camera for a photo of a meal and hands over a JPEG, or null on a cancel.
 * Returns null where this platform has no camera support, then the photo button is not shown.
 */
@Composable
expect fun rememberMealPhotoCapture(onPhoto: (ByteArray?) -> Unit): (() -> Unit)?
