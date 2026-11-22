package app.aaps.plugins.aps.learned

/** The plugin's files for the tests, in memory */
class MemoryLearnedFiles(val files: MutableMap<String, String> = mutableMapOf()) : LearnedFiles {

    override fun read(name: String): String? = files[name]

    override fun write(name: String, text: String) {
        files[name] = text
    }

    override fun delete(name: String) {
        files.remove(name)
    }
}
