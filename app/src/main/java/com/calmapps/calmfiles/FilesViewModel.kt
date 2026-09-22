package com.calmapps.calmfiles

import android.app.Application
import android.os.storage.StorageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class Clipboard(val source: File, val isCut: Boolean)

data class Volume(val name: String, val root: File, val isPrimary: Boolean)

class FilesViewModel(application: Application) : AndroidViewModel(application) {

    var volumes by mutableStateOf(scanVolumes())
        private set

    /** null means the volume list is showing (only reachable when several volumes are mounted). */
    var currentDir by mutableStateOf(volumes.singleOrNull()?.root)
        private set

    val currentVolume: Volume?
        get() = currentDir?.let { volumeFor(it) }

    var entries by mutableStateOf<List<File>>(emptyList())
        private set
    var clipboard by mutableStateOf<Clipboard?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var showHidden by mutableStateOf(false)

    /** One-shot user-facing message; consumed by the screen's snackbar. */
    var message by mutableStateOf<String?>(null)

    init {
        refresh()
    }

    /** One entry per mounted volume; the root is the "Android" dir's parent (works on API 28+). */
    private fun scanVolumes(): List<Volume> {
        val context = getApplication<Application>()
        val storageManager = context.getSystemService(StorageManager::class.java)
        return context.getExternalFilesDirs(null).filterNotNull().mapNotNull { filesDir ->
            val root = generateSequence(filesDir) { it.parentFile }
                .firstOrNull { it.name == "Android" }?.parentFile ?: return@mapNotNull null
            val volume = storageManager.getStorageVolume(filesDir)
            val name = if (volume == null || volume.isPrimary) "Phone storage"
                else volume.getDescription(context) ?: "SD card"
            Volume(name, root, isPrimary = volume == null || volume.isPrimary)
        }
    }

    private fun volumeFor(dir: File): Volume? =
        volumes.firstOrNull { dir.path == it.root.path || dir.path.startsWith(it.root.path + File.separator) }

    fun refresh() {
        volumes = scanVolumes()
        // Falls back when the current volume was unmounted (SD card removed).
        val dir = currentDir?.takeIf { volumeFor(it) != null } ?: volumes.singleOrNull()?.root
        currentDir = dir
        val listed = dir?.listFiles()?.toList().orEmpty()
            .filter { showHidden || !it.name.startsWith(".") }
        entries = listed.sortedWith(
            compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() }
        )
    }

    fun toggleHidden() {
        showHidden = !showHidden
        refresh()
    }

    fun navigateTo(dir: File) {
        if (!dir.isDirectory) return
        currentDir = dir
        refresh()
    }

    fun openVolume(volume: Volume) = navigateTo(volume.root)

    /** Returns false when there is nothing above: the volume list, or the root of the only volume. */
    fun navigateUp(): Boolean {
        val dir = currentDir ?: return false
        if (dir.path == currentVolume?.root?.path) {
            if (volumes.size < 2) return false
            currentDir = null
            refresh()
            return true
        }
        currentDir = dir.parentFile ?: return false
        refresh()
        return true
    }

    fun createFolder(name: String) = runOperation("Could not create folder") {
        val dir = currentDir ?: error("No folder open")
        val target = File(dir, name)
        if (target.exists()) error("A file or folder with that name already exists")
        if (!target.mkdirs()) error("Could not create folder")
    }

    fun createFile(name: String) = runOperation("Could not create file") {
        val dir = currentDir ?: error("No folder open")
        val target = File(dir, name)
        if (target.exists()) error("A file or folder with that name already exists")
        if (!target.createNewFile()) error("Could not create file")
    }

    fun rename(file: File, newName: String) = runOperation("Could not rename") {
        val target = File(file.parentFile, newName)
        if (target.exists()) error("A file or folder with that name already exists")
        if (!file.renameTo(target)) error("Could not rename ${file.name}")
    }

    fun delete(file: File) = runOperation("Could not delete") {
        if (!file.deleteRecursively()) error("Could not delete ${file.name}")
    }

    fun copy(file: File) {
        clipboard = Clipboard(file, isCut = false)
    }

    fun cut(file: File) {
        clipboard = Clipboard(file, isCut = true)
    }

    fun clearClipboard() {
        clipboard = null
    }

    fun paste() {
        val clip = clipboard ?: return
        val destDir = currentDir ?: return
        runOperation("Could not paste") {
            if (clip.source.isDirectory && destDir.canonicalPath.startsWith(clip.source.canonicalPath + File.separator)) {
                error("Cannot paste a folder into itself")
            }
            val target = uniqueTarget(destDir, clip.source.name)
            if (clip.isCut) {
                if (!clip.source.renameTo(target)) {
                    // renameTo fails across mount points; fall back to copy + delete.
                    clip.source.copyRecursively(target, overwrite = false)
                    clip.source.deleteRecursively()
                }
            } else {
                clip.source.copyRecursively(target, overwrite = false)
            }
            clipboard = null
        }
    }

    private fun uniqueTarget(dir: File, name: String): File {
        var target = File(dir, name)
        if (!target.exists()) return target
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "").let { if (it.isEmpty() || base == name) "" else ".$it" }
        var n = 1
        while (target.exists()) {
            target = File(dir, "$base ($n)$ext")
            n++
        }
        return target
    }

    private fun runOperation(fallbackMessage: String, block: () -> Unit) {
        viewModelScope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) { block() }
            } catch (e: Exception) {
                message = e.message ?: fallbackMessage
            } finally {
                busy = false
                refresh()
            }
        }
    }
}
