package com.calmapps.calmfiles

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calmapps.calmfiles.ui.BrowserScreen
import com.calmapps.calmfiles.ui.StoragePermissionScreen
import com.mudita.mmd.ThemeMMD
import java.io.File

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val picking = intent.action == Intent.ACTION_GET_CONTENT
        val pickFilters = if (picking) {
            intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)?.toList()
                ?: listOf(intent.type ?: "*/*")
        } else null
        setContent {
            ThemeMMD {
                MonoFilesApp(
                    pickFilters = pickFilters,
                    onPick = if (picking) ::finishWithPickedFile else null,
                )
            }
        }
    }

    /** Hands the picked file back to the requesting app as a readable content URI. */
    private fun finishWithPickedFile(file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        setResult(RESULT_OK, Intent().setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        finish()
    }
}

private fun hasStorageAccess(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

@Composable
fun MonoFilesApp(
    pickFilters: List<String>? = null,
    onPick: ((File) -> Unit)? = null,
    viewModel: FilesViewModel = viewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasAccess by remember { mutableStateOf(hasStorageAccess(context)) }

    // The All Files Access grant happens in system settings; recheck when we come back.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = hasStorageAccess(context)
                // Also picks up SD cards mounted or removed while the app was backgrounded.
                if (granted) viewModel.refresh()
                hasAccess = granted
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAccess = granted
        if (granted) viewModel.refresh()
    }

    Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        if (hasAccess) {
            BrowserScreen(viewModel, pickFilters = pickFilters, onPick = onPick)
        } else {
            StoragePermissionScreen(
                onRequestClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:${context.packageName}"),
                        )
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                        }
                    } else {
                        permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    }
                },
            )
        }
    }
}
