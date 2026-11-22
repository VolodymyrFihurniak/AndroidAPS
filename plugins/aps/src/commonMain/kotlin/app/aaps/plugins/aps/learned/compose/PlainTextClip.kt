package app.aaps.plugins.aps.learned.compose

import androidx.compose.ui.platform.ClipEntry

/** autoISF build: a clipboard entry with plain [text]; each platform makes it its own way */
internal expect fun plainTextClip(text: String): ClipEntry
