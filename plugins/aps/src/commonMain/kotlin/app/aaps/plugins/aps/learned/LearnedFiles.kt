package app.aaps.plugins.aps.learned

import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.StringNonPreferenceKey

/**
 * autoISF build, personal prediction: the plugin's own files, for what is too big for a setting: the model (about 130
 * kB), the copy of the AutoISF predictions and the kept forecasts (about 100 kB each). Settings are all read when the
 * app starts and written whole on each change. Each platform keeps them in the app's own folder.
 *
 * The files are small, so the calls block; they are made from the plugin's background work.
 */
interface LearnedFiles {

    /** The text of file [name], null when there is none */
    fun read(name: String): String?

    /** Writes [text] to file [name], whole: a reader never sees half of it */
    fun write(name: String, text: String)

    fun delete(name: String)

    companion object {

        const val MODEL = "model.json"
        const val AUTOISF_RUNS = "autoisf_runs.txt"
        const val FORECASTS = "forecasts.txt"
    }
}

/**
 * The text of file [name]. A version before the files kept it in the setting [oldKey]: then it is moved to the file
 * once and the setting is cleared.
 */
fun LearnedFiles.readMoving(name: String, preferences: Preferences, oldKey: StringNonPreferenceKey): String {
    read(name)?.let { return it }
    val old = preferences.get(oldKey)
    if (old.isNotEmpty()) {
        write(name, old)
        preferences.put(oldKey, "")
    }
    return old
}
