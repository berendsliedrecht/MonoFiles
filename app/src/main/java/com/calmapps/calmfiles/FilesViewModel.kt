package com.calmapps.calmfiles

import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class Clipboard(val source: File, val isCut: Boolean)

class FilesViewModel : ViewModel() {

    val root: File = Environment.getExternalStorageDirectory()

    var currentDir by mutableStateOf(root)
        private set
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

    fun refresh() {
        val listed = currentDir.listFiles()?.toList().orEmpty()
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

    /** Returns false when already at the storage root. */
    fun navigateUp(): Boolean {
        if (currentDir == root) return false
        currentDir = currentDir.parentFile ?: root
        refresh()
        return true
    }

    fun createFolder(name: String) = runOperation("Could not create folder") {
        val target = File(currentDir, name)
        if (target.exists()) error("A file or folder with that name already exists")
        if (!target.mkdirs()) error("Could not create folder")
    }

    fun createFile(name: String) = runOperation("Could not create file") {
        val target = File(currentDir, name)
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
        val destDir = currentDir
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
