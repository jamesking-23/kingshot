package com.kingshot.camera.media

/** An original capture and its companions, grouped by shared file stem (KS_<date>_<time>_<n>). */
data class CapturePair(val stem: String, val jpeg: String?, val dng: String?) {
    val isComplete: Boolean get() = jpeg != null && dng != null
}

/** Pure naming rules so source files are paired, never renamed over each other. */
object PairNaming {
    private val roles = mapOf("jpg" to "jpeg", "jpeg" to "jpeg", "dng" to "dng")

    fun stem(fileName: String): String = fileName.substringBeforeLast('.')

    fun extension(fileName: String): String = fileName.substringAfterLast('.', "").lowercase()

    /** Groups file names by stem. Names with other extensions are ignored; the first file for a role wins and a duplicate is reported by [duplicates]. */
    fun pairs(names: List<String>): List<CapturePair> = names
        .filter { roles.containsKey(extension(it)) }
        .groupBy { stem(it) }
        .map { (stem, files) ->
            CapturePair(
                stem,
                files.firstOrNull { roles[extension(it)] == "jpeg" },
                files.firstOrNull { roles[extension(it)] == "dng" }
            )
        }
        .sortedBy { it.stem }

    /** Names that share a stem and role with another file (e.g. a MediaStore auto-rename that broke the pair). */
    fun duplicates(names: List<String>): List<String> = names
        .filter { roles.containsKey(extension(it)) }
        .groupBy { stem(it) to roles[extension(it)] }
        .filterValues { it.size > 1 }
        .values.flatten()

    /** MediaStore renames a clashing file to "name (1).ext"; detect that so the pairing can be repaired by the user, not by overwriting. */
    fun looksAutoRenamed(fileName: String): Boolean = Regex(" \\(\\d+\\)$").containsMatchIn(stem(fileName))
}
