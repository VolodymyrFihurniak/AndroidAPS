package app.aaps.ui.food

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max

/**
 * The full size camera photo goes to a file in the cache (shared through the app's FileProvider, authority
 * `<applicationId>.fileprovider`), is made smaller and is deleted again.
 */
@Composable
actual fun rememberMealPhotoCapture(onPhoto: (ByteArray?) -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    val file = remember { File(File(context.cacheDir, "meal"), "meal.jpg") }
    val uri = remember { photoUri(context, file) } ?: return null
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val bytes = if (saved) runCatching { toJpeg(file) }.getOrNull() else null
        file.delete()
        onPhoto(bytes)
    }
    // The app holds the CAMERA permission (for QR codes), so Android lets the camera app take a photo for us only
    // after the user allowed it.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launcher.launch(uri) else onPhoto(null)
    }
    return {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launcher.launch(uri)
        else permission.launch(Manifest.permission.CAMERA)
    }
}

private fun photoUri(context: Context, file: File): Uri? =
    runCatching {
        file.parentFile?.mkdirs()
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull()

/** Smaller photos are faster to send and cost less; 1024 px is enough to see the food */
private fun toJpeg(file: File): ByteArray? {
    val bitmap = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = 2 }) ?: return null
    val longest = max(bitmap.width, bitmap.height)
    val scaled = if (longest > MAX_SIDE) {
        val factor = MAX_SIDE.toFloat() / longest
        Bitmap.createScaledBitmap(bitmap, (bitmap.width * factor).toInt(), (bitmap.height * factor).toInt(), true)
    } else bitmap
    return ByteArrayOutputStream().use { out ->
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
        out.toByteArray()
    }
}

private const val MAX_SIDE = 1024
