package app.aaps.ui.food

import androidx.compose.runtime.Composable

/** No camera here yet: the photo button is not shown */
@Composable
actual fun rememberMealPhotoCapture(onPhoto: (ByteArray?) -> Unit): (() -> Unit)? = null
