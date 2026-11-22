package app.aaps.plugins.aps.learned.compose

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry

internal actual fun plainTextClip(text: String): ClipEntry = ClipEntry(ClipData.newPlainText("Personal prediction", text))
