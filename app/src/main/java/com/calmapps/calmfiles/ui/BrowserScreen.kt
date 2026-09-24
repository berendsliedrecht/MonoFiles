@file:OptIn(ExperimentalMaterial3Api::class)

package com.calmapps.calmfiles.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.SdCard
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.calmapps.calmfiles.FilesViewModel
import com.calmapps.calmfiles.Volume
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.menus.DropdownMenuItemMMD
import com.mudita.mmd.components.menus.DropdownMenuMMD
import com.mudita.mmd.components.snackbar.SnackbarHostMMD
import com.mudita.mmd.components.snackbar.SnackbarHostStateMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed class Sheet {
    data class Actions(val file: File) : Sheet()
    data class Rename(val file: File) : Sheet()
    data class ConfirmDelete(val file: File) : Sheet()
    data object NewFolder : Sheet()
    data object NewFile : Sheet()
}

@Composable
fun BrowserScreen(
    viewModel: FilesViewModel,
    pickFilters: List<String>? = null,
    onPick: ((File) -> Unit)? = null,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostStateMMD() }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }

    val currentDir = viewModel.currentDir
    val volume = viewModel.currentVolume
    val atVolumeList = currentDir == null
    val canGoUp = currentDir != null &&
        (currentDir.path != volume?.root?.path || viewModel.volumes.size > 1)

    BackHandler(enabled = canGoUp) { viewModel.navigateUp() }

    LaunchedEffect(viewModel.message) {
        viewModel.message?.let {
            snackbarHostState.showSnackbar(message = it)
            viewModel.message = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHostMMD(hostState = snackbarHostState) },
        topBar = {
            TopAppBarMMD(
                title = {
                    TextMMD(
                        text = when {
                            currentDir == null -> "Storage"
                            volume != null && currentDir.path == volume.root.path -> volume.name
                            else -> currentDir.name
                        },
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (canGoUp) {
                        IconButton(onClick = { viewModel.navigateUp() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Up")
                        }
                    }
                },
                actions = {
                    if (!atVolumeList) Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Outlined.MoreVert, "Menu")
                        }
                        DropdownMenuMMD(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItemMMD(
                                text = { TextMMD("New folder") },
                                onClick = {
                                    menuExpanded = false
                                    sheet = Sheet.NewFolder
                                },
                            )
                            DropdownMenuItemMMD(
                                text = { TextMMD("New file") },
                                onClick = {
                                    menuExpanded = false
                                    sheet = Sheet.NewFile
                                },
                            )
                            DropdownMenuItemMMD(
                                text = { TextMMD(if (viewModel.showHidden) "Hide hidden files" else "Show hidden files") },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.toggleHidden()
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            TextMMD(
                text = displayPath(viewModel),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            HorizontalDividerMMD()

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (atVolumeList) {
                    LazyColumnMMD(modifier = Modifier.fillMaxSize()) {
                        items(viewModel.volumes.size) { index ->
                            val vol = viewModel.volumes[index]
                            VolumeRow(volume = vol, onClick = { viewModel.openVolume(vol) })
                            if (index < viewModel.volumes.lastIndex) HorizontalDividerMMD(thickness = 0.5.dp)
                        }
                    }
                } else {
                    val entries = if (pickFilters == null) viewModel.entries
                        else viewModel.entries.filter { it.isDirectory || matchesMime(it, pickFilters) }
                    if (entries.isEmpty()) {
                        TextMMD(
                            text = "Empty folder",
                            fontSize = 16.sp,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    } else {
                        LazyColumnMMD(modifier = Modifier.fillMaxSize()) {
                            items(entries.size) { index ->
                                val entry = entries[index]
                                EntryRow(
                                    entry = entry,
                                    onClick = {
                                        if (entry.isDirectory) viewModel.navigateTo(entry)
                                        else if (onPick != null) onPick(entry)
                                        else openFile(context, entry, chooser = false) { viewModel.message = it }
                                    },
                                    onMoreClick = { sheet = Sheet.Actions(entry) },
                                )
                                if (index < entries.lastIndex) HorizontalDividerMMD(thickness = 0.5.dp)
                            }
                        }
                    }
                }
            }

            if (!atVolumeList) viewModel.clipboard?.let { clip ->
                HorizontalDividerMMD(thickness = 3.dp)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    TextMMD(
                        text = "${if (clip.isCut) "Move" else "Copy"} \"${clip.source.name}\"",
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButtonMMD(
                        onClick = { viewModel.paste() },
                        enabled = !viewModel.busy,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        TextMMD("Paste here", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButtonMMD(
                        onClick = { viewModel.clearClipboard() },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        TextMMD("Cancel", fontSize = 14.sp)
                    }
                }
            }
        }
    }

    when (val current = sheet) {
        is Sheet.Actions -> ActionsSheet(
            file = current.file,
            onDismiss = { sheet = null },
            onOpenWith = {
                sheet = null
                openFile(context, current.file, chooser = true) { viewModel.message = it }
            },
            onCopy = {
                sheet = null
                viewModel.copy(current.file)
            },
            onCut = {
                sheet = null
                viewModel.cut(current.file)
            },
            onRename = { sheet = Sheet.Rename(current.file) },
            onDelete = { sheet = Sheet.ConfirmDelete(current.file) },
        )

        is Sheet.Rename -> NameInputSheet(
            title = "Rename \"${current.file.name}\"",
            confirmLabel = "Rename",
            initialValue = current.file.name,
            onDismiss = { sheet = null },
            onConfirm = { name ->
                sheet = null
                viewModel.rename(current.file, name)
            },
        )

        is Sheet.ConfirmDelete -> ConfirmDeleteSheet(
            file = current.file,
            onDismiss = { sheet = null },
            onConfirm = {
                sheet = null
                viewModel.delete(current.file)
            },
        )

        Sheet.NewFolder -> NameInputSheet(
            title = "New folder",
            confirmLabel = "Create",
            onDismiss = { sheet = null },
            onConfirm = { name ->
                sheet = null
                viewModel.createFolder(name)
            },
        )

        Sheet.NewFile -> NameInputSheet(
            title = "New file",
            confirmLabel = "Create",
            onDismiss = { sheet = null },
            onConfirm = { name ->
                sheet = null
                viewModel.createFile(name)
            },
        )

        null -> Unit
    }
}

@Composable
private fun VolumeRow(volume: Volume, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Icon(
            imageVector = if (volume.isPrimary) Icons.Outlined.Smartphone else Icons.Outlined.SdCard,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            TextMMD(
                text = volume.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TextMMD(
                text = "${formatSize(volume.root.freeSpace)} free of ${formatSize(volume.root.totalSpace)}",
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun EntryRow(
    entry: File,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Icon(
            imageVector = if (entry.isDirectory) Icons.Outlined.Folder else Icons.AutoMirrored.Outlined.InsertDriveFile,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            TextMMD(
                text = entry.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TextMMD(
                text = entrySubtitle(entry),
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
        IconButton(onClick = onMoreClick) {
            Icon(Icons.Outlined.MoreVert, "Actions for ${entry.name}")
        }
    }
}

@Composable
private fun ActionsSheet(
    file: File,
    onDismiss: () -> Unit,
    onOpenWith: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    ActionSheetScaffold(title = file.name, onDismiss = onDismiss) {
        if (file.isFile) SheetButton("Open with…", onOpenWith)
        SheetButton("Copy", onCopy)
        SheetButton("Move", onCut)
        SheetButton("Rename", onRename)
        SheetButton("Delete", onDelete)
    }
}

@Composable
private fun ConfirmDeleteSheet(
    file: File,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val what = if (file.isDirectory) "folder and everything in it" else "file"
    ActionSheetScaffold(title = "Delete \"${file.name}\"?", onDismiss = onDismiss) {
        TextMMD(
            text = "The $what will be permanently deleted.",
            fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth(),
        )
        SheetButton("Delete", onConfirm)
        SheetButton("Cancel", onDismiss)
    }
}

@Composable
private fun NameInputSheet(
    title: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    initialValue: String = "",
) {
    var name by rememberSaveable { mutableStateOf(initialValue) }
    val valid = name.isNotBlank() && !name.contains('/')

    ActionSheetScaffold(title = title, onDismiss = onDismiss) {
        com.mudita.mmd.components.text_field.TextFieldMMD(
            value = name,
            onValueChange = { name = it },
            label = { TextMMD("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButtonMMD(
            onClick = { onConfirm(name.trim()) },
            enabled = valid,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
        ) {
            TextMMD(confirmLabel, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ActionSheetScaffold(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    com.mudita.mmd.components.bottom_sheet.ModalBottomSheetMMD(
        onDismissRequest = onDismiss,
        sheetState = com.mudita.mmd.components.bottom_sheet.rememberModalBottomSheetMMDState(
            skipPartiallyExpanded = true,
        ),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            TextMMD(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            )
            content()
        }
    }
}

@Composable
private fun SheetButton(label: String, onClick: () -> Unit) {
    OutlinedButtonMMD(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(12.dp),
    ) {
        TextMMD(label, fontSize = 16.sp)
    }
}

private fun displayPath(viewModel: FilesViewModel): String {
    val dir = viewModel.currentDir ?: return "Storage"
    val volume = viewModel.currentVolume ?: return dir.path
    return volume.name + dir.path.removePrefix(volume.root.path)
}

private fun entrySubtitle(entry: File): String {
    val date = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(entry.lastModified()))
    return if (entry.isDirectory) {
        val count = entry.listFiles()?.size ?: 0
        "$count item${if (count == 1) "" else "s"} · $date"
    } else {
        "${formatSize(entry.length())} · $date"
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "%.1f KB".format(bytes / 1_000.0)
    else -> "$bytes B"
}

// True when the file's MIME type (from its extension) matches any picker filter, wildcards included.
private fun matchesMime(file: File, filters: List<String>): Boolean {
    val mime = MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"
    return filters.any { filter ->
        filter == "*/*" || filter == mime ||
            (filter.endsWith("/*") && mime.startsWith(filter.dropLast(1)))
    }
}

/** Opens a file in the app that handles its type, via a FileProvider content URI. */
private fun openFile(context: Context, file: File, chooser: Boolean, onError: (String) -> Unit) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val extension = file.extension.lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(if (chooser) Intent.createChooser(intent, "Open with") else intent)
    } catch (e: ActivityNotFoundException) {
        onError("No app can open this file")
    } catch (e: Exception) {
        onError(e.message ?: "Could not open file")
    }
}
