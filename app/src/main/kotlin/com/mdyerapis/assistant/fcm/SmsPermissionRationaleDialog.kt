package com.mdyerapis.assistant.fcm

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext

/**
 * Rationale dialog shown before requesting SMS permissions (phase 10).
 * Explains why the app needs SEND_SMS/READ_SMS — the user should not be
 * surprised by the system permission sheet. On grant, invokes onGranted
 * so a pending relay action is retried.
 */
@Composable
fun SmsPermissionRationaleDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onGranted: () -> Unit,
) {
    if (!visible) return
    val context = LocalContext.current
    var showSystemDialog by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val allGranted = result[Manifest.permission.SEND_SMS] == true ||
            result[Manifest.permission.READ_SMS] == true
        onDismiss()
        if (allGranted) onGranted()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Allow Assistant to send and read SMS?") },
        text = {
            Text(
                "The assistant relays texts through this phone: sending a " +
                    "message you dictate and reading recent messages when you " +
                    "ask. SMS is only touched when you ask — nothing is " +
                    "uploaded or synced in the background."
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    showSystemDialog = true
                    launcher.launch(
                        arrayOf(
                            Manifest.permission.SEND_SMS,
                            Manifest.permission.READ_SMS,
                        )
                    )
                }
            ) {
                Text("Allow")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Not now") }
        },
    )
}
