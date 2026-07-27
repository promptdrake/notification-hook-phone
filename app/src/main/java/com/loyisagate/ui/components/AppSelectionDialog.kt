package com.loyisagate.ui.components

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap

data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable
)

@Composable
fun AppSelectionDialog(
    pm: PackageManager,
    selectedPackages: Set<String>,
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val allApps = remember {
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(intent, 0)
        resolveInfos.map { info ->
            AppInfo(
                packageName = info.activityInfo.packageName,
                appName = info.loadLabel(pm).toString(),
                icon = info.loadIcon(pm)
            )
        }.sortedBy { it.appName }
    }

    val checkedPackages = remember {
        mutableStateListOf<String>().apply { addAll(selectedPackages) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Select Applications to Monitor",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    "Notifications from selected apps will be forwarded to your webhook.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                ) {
                    items(allApps, key = { it.packageName }) { app ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        ) {
                            Checkbox(
                                checked = app.packageName in checkedPackages,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        checkedPackages.add(app.packageName)
                                    } else {
                                        checkedPackages.remove(app.packageName)
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Image(
                                painter = remember(app.icon) {
                                    BitmapPainter(app.icon.toBitmap().asImageBitmap())
                                },
                                contentDescription = null,
                                modifier = Modifier
                                    .width(32.dp)
                                    .height(32.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = app.appName,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(checkedPackages.toSet()) }) {
                Text("Save (${checkedPackages.size} selected)")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
