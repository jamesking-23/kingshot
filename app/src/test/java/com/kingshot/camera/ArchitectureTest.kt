package com.kingshot.camera

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Enforces the package dependency direction in docs/ARCHITECTURE.md and the pure-Kotlin core. */
class ArchitectureTest {
    private val root: File = listOf("src/main/java/com/kingshot/camera", "app/src/main/java/com/kingshot/camera")
        .map { File(it) }.first { it.isDirectory }

    private val allowed = mapOf(
        "capabilities" to emptySet(),
        "profiles" to setOf("capabilities"),
        "media" to emptySet(),
        "raw" to emptySet(),
        "diagnostics" to setOf("capabilities", "media"),
        "camera" to setOf("capabilities", "diagnostics", "raw"),
        "capture" to setOf("camera", "capabilities", "diagnostics", "media", "raw", "profiles")
    )

    private val importRegex = Regex("^import com\\.kingshot\\.camera\\.([a-z]+)\\.", RegexOption.MULTILINE)

    private fun filesOf(pkg: String): List<File> =
        File(root, pkg).takeIf { it.isDirectory }?.walkTopDown()?.filter { it.extension == "kt" }?.toList().orEmpty()

    @Test
    fun packagesOnlyDependOnAllowedPackages() {
        val violations = mutableListOf<String>()
        for ((pkg, ok) in allowed) {
            for (f in filesOf(pkg)) {
                for (m in importRegex.findAll(f.readText())) {
                    val dep = m.groupValues[1]
                    if (dep != pkg && dep !in ok) violations += "${f.name}: $pkg -> $dep"
                }
            }
        }
        assertTrue("Forbidden dependencies: $violations", violations.isEmpty())
    }

    @Test
    fun pureCoreHasNoAndroidImports() {
        val pure = listOf(
            "capabilities", "profiles"
        ).flatMap { filesOf(it) } + listOf(
            "capture/StillRequestPlanner.kt", "capture/CaptureIdGenerator.kt", "capture/ReceiptAssembler.kt",
            "raw/TimestampMatcher.kt", "diagnostics/Json.kt", "diagnostics/ProcessingReceipt.kt",
            "diagnostics/CapabilityReport.kt", "diagnostics/CaptureLog.kt"
        ).map { File(root, it) }
        val offenders = pure.filter { f -> f.readLines().any { it.startsWith("import android.") } }.map { it.name }
        assertTrue("Android imports in pure core: $offenders", offenders.isEmpty())
    }

    @Test
    fun noInternetOrNetworkApiInSources() {
        val offenders = root.walkTopDown().filter { it.extension == "kt" }
            .filter { f -> f.readText().let { it.contains("java.net.") || it.contains("okhttp") || it.contains("HttpURLConnection") } }
            .map { it.name }.toList()
        assertTrue("Network APIs found: $offenders", offenders.isEmpty())
    }
}
