package app.aaps.desktop.shell.platform

import app.aaps.implementation.maintenance.DesktopFolders
import app.aaps.plugins.aps.learned.LearnedFiles
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.io.File

/**
 * autoISF build, personal prediction: the plugin's files in the app's own folder, `learned_prediction`
 * inside [DesktopFolders.data], as Android keeps them in its private `files` folder.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class DesktopLearnedFiles() : LearnedFiles {

    private val folder: File get() = File(DesktopFolders.data, FOLDER).also { it.mkdirs() }

    override fun read(name: String): String? = File(folder, name).takeIf { it.isFile }?.readText()

    override fun write(name: String, text: String) {
        // Written next to it and then renamed, so a crash leaves the old file, not half of the new one
        val target = File(folder, name)
        val temporary = File(folder, "$name.new")
        temporary.writeText(text)
        if (!temporary.renameTo(target)) {
            target.delete()
            temporary.renameTo(target)
        }
    }

    override fun delete(name: String) {
        File(folder, name).delete()
    }

    private companion object {

        const val FOLDER = "learned_prediction"
    }
}
