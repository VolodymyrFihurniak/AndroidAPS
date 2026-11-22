package app.aaps.plugins.aps.learned

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.io.File

/** autoISF build, personal prediction: the plugin's files in the app's own folder, `files/learned_prediction` */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class AndroidLearnedFiles(private val context: Context) : LearnedFiles {

    private val folder: File get() = File(context.filesDir, FOLDER).also { it.mkdirs() }

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
