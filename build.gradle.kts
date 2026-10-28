plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

// Cloud-synced folders (OneDrive, Dropbox…) lock files while Gradle writes them and waste space syncing
// build output. When the project lives in one, build output goes to %LOCALAPPDATA%\TKD-PRO-build instead.
val syncedFolder = listOf("OneDrive", "Dropbox", "Google Drive", "iCloud").any { rootDir.absolutePath.contains(it) }
if (syncedFolder) {
    val base = File(System.getenv("LOCALAPPDATA") ?: "${System.getProperty("user.home")}/.cache", "TKD-PRO-build")
    allprojects {
        layout.buildDirectory.set(File(base, path.trim(':').replace(':', '/').ifEmpty { "root" }))
    }
}
