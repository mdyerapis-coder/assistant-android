package com.mdyerapis.sable.feature.chat

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * Rationale shown before requesting SEND_SMS / READ_SMS. Used by the
 * cloud FCM relay (phase 10) and by on-device P2 in-process SMS.
 */
@Composable
fun SmsPermissionRationaleDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onGranted: () -> Unit,
) {
    if (!visible) return

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.SEND_SMS] == true ||
            result[Manifest.permission.READ_SMS] == true
        onDismiss()
        if (granted) onGranted()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Allow Assistant to send and read SMS?") },
        text = {
            Text(
                "On-device chat sends and reads texts with this phone’s SMS " +
                    "radio — no cloud hop. Cloud Assistant still uses the same " +
                    "permission for its FCM relay. SMS is only touched when you ask; " +
                    "nothing is uploaded or synced in the background.",
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
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
