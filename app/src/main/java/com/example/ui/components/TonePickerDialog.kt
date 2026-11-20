package com.example.ui.components

import android.app.Activity
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.util.NotificationTonePlayer

@Composable
fun TonePickerDialog(
    targetTitle: String,
    targetSubtitle: String,
    initialToneUri: String?,
    initialToneName: String,
    onDismiss: () -> Unit,
    onToneSelected: (toneUri: String?, toneName: String) -> Unit
) {
    val context = LocalContext.current
    var selectedUri by remember { mutableStateOf(initialToneUri) }
    var selectedName by remember { mutableStateOf(initialToneName.ifBlank { "System Default" }) }

    DisposableEffect(Unit) {
        onDispose {
            NotificationTonePlayer.stopPreview()
        }
    }

    val systemPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val pickedUri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            if (pickedUri == null) {
                selectedUri = NotificationTonePlayer.URI_SILENT
                selectedName = "Silent (No Sound)"
            } else {
                selectedUri = pickedUri.toString()
                selectedName = NotificationTonePlayer.resolveToneTitle(context, pickedUri)
                NotificationTonePlayer.playTone(context, selectedUri)
            }
        }
    }

    val isCustomDeviceTone = selectedUri != null &&
        NotificationTonePlayer.presets.none { it.uri == selectedUri }

    AlertDialog(
        onDismissRequest = {
            NotificationTonePlayer.stopPreview()
            onDismiss()
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = targetTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = targetSubtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (isCustomDeviceTone) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = true,
                                onClick = { NotificationTonePlayer.playTone(context, selectedUri) }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = selectedName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Device sound selected",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { NotificationTonePlayer.playTone(context, selectedUri) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = "Preview tone",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                NotificationTonePlayer.presets.forEach { preset ->
                    val isSelected = selectedUri == preset.uri && !isCustomDeviceTone
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedUri = preset.uri
                                selectedName = preset.name
                                NotificationTonePlayer.playTone(context, preset.uri)
                            }
                            .padding(vertical = 2.dp)
                            .testTag("tone_preset_${preset.id}")
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                selectedUri = preset.uri
                                selectedName = preset.name
                                NotificationTonePlayer.playTone(context, preset.uri)
                            }
                        )
                        Text(
                            text = preset.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                        if (preset.uri != NotificationTonePlayer.URI_SILENT) {
                            IconButton(
                                onClick = { NotificationTonePlayer.playTone(context, preset.uri) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = "Preview ${preset.name}",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        try {
                            systemPickerLauncher.launch(
                                NotificationTonePlayer.createRingtonePickerIntent(selectedUri)
                            )
                        } catch (_: Exception) {
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("choose_device_tone_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.LibraryMusic,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Choose from Device Sounds...")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    NotificationTonePlayer.stopPreview()
                    onToneSelected(selectedUri, selectedName)
                },
                modifier = Modifier.testTag("save_tone_btn")
            ) {
                Text("Save Tone")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    NotificationTonePlayer.stopPreview()
                    onDismiss()
                }
            ) {
                Text("Cancel")
            }
        }
    )
}
