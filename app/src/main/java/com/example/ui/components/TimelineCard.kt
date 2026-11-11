package com.example.ui.components

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CallType
import com.example.data.model.NotificationCategory
import com.example.data.model.ProfileType
import com.example.data.model.UnifiedTimelineItem

@Composable
fun TimelineCard(
    item: UnifiedTimelineItem,
    onOpenApp: (String) -> Unit,
    onCallClick: (String) -> Unit,
    onWhatsAppClick: (String) -> Unit,
    onQuickReply: (UnifiedTimelineItem.NotificationItem) -> Unit,
    onToggleStar: (UnifiedTimelineItem.NotificationItem) -> Unit,
    onDelete: (UnifiedTimelineItem) -> Unit,
    onMarkRead: (UnifiedTimelineItem) -> Unit,
    onTruecallerClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedMenu by remember { mutableStateOf(false) }
    val relativeTime = DateUtils.getRelativeTimeSpanString(
        item.timestamp,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS
    ).toString()

    val cardColor = if (!item.isRead) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 12.dp)
            .clickable { onMarkRead(item) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            when (item) {
                is UnifiedTimelineItem.NotificationItem -> {
                    NotificationHeaderRow(
                        item = item,
                        relativeTime = relativeTime,
                        onMenuClick = { expandedMenu = true }
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (item.text.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (!item.subText.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = item.subText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (item.category == NotificationCategory.WHATSAPP) {
                            AssistChip(
                                onClick = { onQuickReply(item) },
                                label = { Text("Quick Reply", fontSize = 12.sp) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Reply,
                                        contentDescription = "Reply",
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = Color(0xFF25D366).copy(alpha = 0.15f),
                                    labelColor = Color(0xFF128C7E)
                                ),
                                modifier = Modifier.testTag("quick_reply_chip_${item.dbId}")
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        AssistChip(
                            onClick = { onOpenApp(item.packageName) },
                            label = { Text("Open App", fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Launch,
                                    contentDescription = "Open App",
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier.testTag("open_app_chip_${item.dbId}")
                        )

                        IconButton(
                            onClick = { onToggleStar(item) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (item.isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Star",
                                tint = if (item.isStarred) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Box {
                            IconButton(
                                onClick = { expandedMenu = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More"
                                )
                            }
                            DropdownMenu(
                                expanded = expandedMenu,
                                onDismissRequest = { expandedMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    onClick = {
                                        expandedMenu = false
                                        onDelete(item)
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Delete, contentDescription = null)
                                    }
                                )
                            }
                        }
                    }
                }

                is UnifiedTimelineItem.CallLogItem -> {
                    CallLogHeaderRow(
                        item = item,
                        relativeTime = relativeTime,
                        onMenuClick = { expandedMenu = true }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AssistChip(
                            onClick = { onTruecallerClick(item.phoneNumber) },
                            label = { Text("Search Truecaller", fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = Color(0xFF0087FF).copy(alpha = 0.15f),
                                labelColor = Color(0xFF0087FF)
                            ),
                            modifier = Modifier.testTag("call_truecaller_btn_${item.dbId}")
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        AssistChip(
                            onClick = { onWhatsAppClick(item.phoneNumber) },
                            label = { Text("WhatsApp", fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Chat,
                                    contentDescription = "WhatsApp",
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = Color(0xFF25D366).copy(alpha = 0.2f),
                                labelColor = Color(0xFF075E54)
                            ),
                            modifier = Modifier.testTag("call_whatsapp_btn_${item.dbId}")
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        AssistChip(
                            onClick = { onCallClick(item.phoneNumber) },
                            label = { Text("Call Back", fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = "Call",
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.testTag("call_back_btn_${item.dbId}")
                        )

                        Box {
                            IconButton(
                                onClick = { expandedMenu = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More"
                                )
                            }
                            DropdownMenu(
                                expanded = expandedMenu,
                                onDismissRequest = { expandedMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Delete Call Log") },
                                    onClick = {
                                        expandedMenu = false
                                        onDelete(item)
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Delete, contentDescription = null)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationHeaderRow(
    item: UnifiedTimelineItem.NotificationItem,
    relativeTime: String,
    onMenuClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        // App Icon Badge
        val (badgeColor, iconVector) = when (item.category) {
            NotificationCategory.WHATSAPP -> Color(0xFF25D366) to Icons.AutoMirrored.Filled.Chat
            NotificationCategory.CALLS -> MaterialTheme.colorScheme.error to Icons.Default.Call
            NotificationCategory.EMAIL -> Color(0xFFEA4335) to Icons.Default.Email
            NotificationCategory.MESSAGES -> Color(0xFF1A73E8) to Icons.AutoMirrored.Filled.Chat
            NotificationCategory.SOCIAL -> Color(0xFFE1306C) to Icons.Default.Notifications
            NotificationCategory.URGENT -> Color(0xFFD32F2F) to Icons.Default.PriorityHigh
            NotificationCategory.PROMOTIONS -> Color(0xFFFBC02D) to Icons.Default.LocalOffer
            else -> MaterialTheme.colorScheme.primary to Icons.Default.Notifications
        }

        Surface(
            shape = CircleShape,
            color = badgeColor,
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = iconVector,
                    contentDescription = item.appName,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.appName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.width(6.dp))
                
                // Profile Badge
                val (profileColor, profileBg) = if (item.profileType == com.example.data.model.ProfileType.WORK) {
                    MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                }
                
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = profileBg,
                    modifier = Modifier.padding(horizontal = 2.dp)
                ) {
                    Text(
                        text = item.profileType.name,
                        style = androidx.compose.ui.text.TextStyle(
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = profileColor
                        ),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }

        Text(
            text = relativeTime,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CallLogHeaderRow(
    item: UnifiedTimelineItem.CallLogItem,
    relativeTime: String,
    onMenuClick: () -> Unit
) {
    val (typeColor, typeIcon, typeLabel) = when (item.callType) {
        CallType.MISSED -> Triple(Color(0xFFE53935), Icons.Default.CallMissed, "Missed Call")
        CallType.INCOMING -> Triple(Color(0xFF43A047), Icons.Default.CallReceived, "Incoming Call")
        CallType.OUTGOING -> Triple(Color(0xFF1E88E5), Icons.Default.CallMade, "Outgoing Call")
        CallType.REJECTED -> Triple(Color(0xFF8E24AA), Icons.Default.CallEnd, "Rejected Call")
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            shape = CircleShape,
            color = typeColor.copy(alpha = 0.15f),
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = typeIcon,
                    contentDescription = typeLabel,
                    tint = typeColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = typeColor,
                    fontWeight = FontWeight.Medium
                )
                if (item.durationSeconds > 0) {
                    Text(
                        text = " • ${item.durationSeconds / 60}m ${item.durationSeconds % 60}s",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Text(
            text = relativeTime,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
