package app.aaps.plugins.aps.learned

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

/** autoISF build, personal prediction: the plugin's files in the app's Application Support folder */
@OptIn(ExperimentalForeignApi::class)
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class IosLearnedFiles : LearnedFiles {

    private fun folder(): String? {
        val support = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).firstOrNull() as? String
            ?: return null
        val path = "$support/$FOLDER"
        NSFileManager.defaultManager.createDirectoryAtPath(path, withIntermediateDirectories = true, attributes = null, error = null)
        return path
    }

    override fun read(name: String): String? {
        val path = folder()?.let { "$it/$name" } ?: return null
        if (!NSFileManager.defaultManager.fileExistsAtPath(path)) return null
        return NSString.stringWithContentsOfFile(path, encoding = NSUTF8StringEncoding, error = null)
    }

    override fun write(name: String, text: String) {
        val path = folder()?.let { "$it/$name" } ?: return
        // atomically: written next to it and then moved, so a crash leaves the old file
        NSString.create(string = text).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
    }

    override fun delete(name: String) {
        val path = folder()?.let { "$it/$name" } ?: return
        NSFileManager.defaultManager.removeItemAtPath(path, error = null)
    }

    private companion object {

        const val FOLDER = "learned_prediction"
    }
}
