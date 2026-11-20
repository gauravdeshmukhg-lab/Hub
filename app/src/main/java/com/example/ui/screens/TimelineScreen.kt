package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.example.data.model.DailyDigestState
import com.example.data.model.FeedTypeFilter
import com.example.data.model.NotificationCategory
import com.example.data.model.ProfileType
import com.example.data.model.UnifiedTimelineItem
import com.example.ui.components.DailyDigestDialog
import com.example.ui.components.HeaderBanner
import com.example.ui.components.NotificationPermissionBanner
import com.example.ui.components.QuickReplyDialog
import com.example.ui.components.TimelineCard
import com.example.ui.components.TonePickerDialog
import com.example.ui.components.openNotificationListenerSettings
import com.example.ui.viewmodel.OmniUiState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    uiState: OmniUiState,
    timelineItems: List<UnifiedTimelineItem>,
    onCategorySelected: (NotificationCategory) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onMarkAllRead: () -> Unit,
    onMarkRead: (UnifiedTimelineItem) -> Unit,
    onToggleStar: (UnifiedTimelineItem.NotificationItem) -> Unit,
    onDelete: (UnifiedTimelineItem) -> Unit,
    onOpenApp: (String) -> Unit,
    onCallClick: (String) -> Unit,
    onWhatsAppClick: (String) -> Unit,
    onSendQuickReply: (UnifiedTimelineItem.NotificationItem, String) -> Unit,
    onOpenPermissionSettings: () -> Unit,
    onResetDemoData: () -> Unit,
    onQuickReplyClick: (UnifiedTimelineItem.NotificationItem) -> Unit,
    onQuickReplyDismiss: () -> Unit,
    onTruecallerClick: (String) -> Unit,
    onProfileSelected: (com.example.data.model.ProfileType) -> Unit,
    onSetNotificationTone: (UnifiedTimelineItem.NotificationItem, String?, String) -> Unit = { _, _, _ -> },
    onToggleFeedType: (FeedTypeFilter) -> Unit = {},
    onResetFeedFilters: () -> Unit = {},
    onDisableFocusMode: () -> Unit = {},
    onGenerateDigest: () -> Unit = {},
    onTogglePin: (UnifiedTimelineItem.NotificationItem) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var activeReplyItem by remember { mutableStateOf<UnifiedTimelineItem.NotificationItem?>(null) }
    var activeToneItem by remember { mutableStateOf<UnifiedTimelineItem.NotificationItem?>(null) }
    var showDigestDialog by remember { mutableStateOf(false) }
    val unreadCount = timelineItems.count { !it.isRead }
    val missedCallCount = timelineItems.count {
        it is UnifiedTimelineItem.CallLogItem && it.callType == com.example.data.model.CallType.MISSED
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Hero Banner Header
        HeaderBanner(
            unreadCount = unreadCount,
            missedCallCount = missedCallCount,
            isListenerActive = uiState.isNotificationListenerGranted,
            onPermissionClick = onOpenPermissionSettings
        )

        // Notification Listener Permission Banner (shown if permission is not granted)
        NotificationPermissionBanner(
            isGranted = uiState.isNotificationListenerGranted,
            onGrantClick = { openNotificationListenerSettings(context) }
        )

        // Profile Selector (Personal / Work)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                ProfileType.entries.forEachIndexed { index, profile ->
                    SegmentedButton(
                        selected = uiState.selectedProfile == profile,
                        onClick = { onProfileSelected(profile) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = ProfileType.entries.size),
                        label = { Text(profile.label, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }
        }

        // Search Bar & Mark All Read
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Search notifications, messages, calls...") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = "Search")
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("timeline_search_bar")
            )

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(
                onClick = {
                    showDigestDialog = true
                    if (uiState.dailyDigestState is DailyDigestState.Idle || uiState.dailyDigestState is DailyDigestState.Error) {
                        onGenerateDigest()
                    }
                },
                modifier = Modifier.testTag("open_daily_digest_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.WbSunny,
                    contentDescription = "Daily Digest Morning Briefing",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            IconButton(
                onClick = onMarkAllRead,
                modifier = Modifier.testTag("mark_all_read_button")
            ) {
                Icon(
                    imageVector = Icons.Default.DoneAll,
                    contentDescription = "Mark all read",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Feed Visibility Toggles (Calls, Messages, Emails, System) to reduce clutter
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Show:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 2.dp)
            )

            FeedTypeFilter.entries.forEach { feedType ->
                val isVisible = feedType in uiState.visibleFeedTypes
                val typeIcon = when (feedType) {
                    FeedTypeFilter.CALLS -> Icons.Default.Call
                    FeedTypeFilter.MESSAGES -> Icons.AutoMirrored.Filled.Chat
                    FeedTypeFilter.EMAILS -> Icons.Default.Email
                    FeedTypeFilter.SYSTEM -> Icons.Default.SettingsApplications
                }

                FilterChip(
                    selected = isVisible,
                    onClick = { onToggleFeedType(feedType) },
                    label = {
                        Text(
                            text = feedType.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isVisible) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (isVisible) Icons.Default.Check else typeIcon,
                            contentDescription = if (isVisible) "${feedType.label} visible" else "${feedType.label} hidden",
                            modifier = Modifier.height(16.dp)
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier.testTag("visibility_toggle_${feedType.name}")
                )
            }

            if (uiState.visibleFeedTypes.size < FeedTypeFilter.entries.size) {
                TextButton(
                    onClick = onResetFeedFilters,
                    modifier = Modifier.testTag("show_all_feed_types_btn")
                ) {
                    Text("Show All", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // Horizontal Category Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NotificationCategory.entries.forEach { category ->
                val selected = uiState.selectedCategory == category
                val (icon, label) = when (category) {
                    NotificationCategory.ALL -> Icons.Default.FilterList to "All Feed"
                    NotificationCategory.WHATSAPP -> Icons.AutoMirrored.Filled.Chat to "WhatsApp"
                    NotificationCategory.CALLS -> Icons.Default.Call to "Calls"
                    NotificationCategory.EMAIL -> Icons.Default.Email to "Email"
                    NotificationCategory.MESSAGES -> Icons.AutoMirrored.Filled.Chat to "Messages"
                    NotificationCategory.SOCIAL -> Icons.Default.Notifications to "Social"
                    NotificationCategory.URGENT -> Icons.Default.Notifications to "Urgent"
                    NotificationCategory.PROMOTIONS -> Icons.Default.Notifications to "Promotions"
                    NotificationCategory.SYSTEM -> Icons.Default.Notifications to "Apps & System"
                }

                FilterChip(
                    selected = selected,
                    onClick = { onCategorySelected(category) },
                    label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                    leadingIcon = {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.height(16.dp)
                        )
                    },
                    shape = RoundedCornerShape(20.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("category_chip_${category.name}")
                )
            }
        }

        // Focus Mode Indicator Banner (shown when Focus Mode is active)
        AnimatedVisibility(visible = uiState.isFocusModeEnabled) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .testTag("focus_mode_active_banner")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CenterFocusStrong,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.height(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Focus Mode Active — Showing Urgent Only",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = onDisableFocusMode,
                        modifier = Modifier.testTag("disable_focus_mode_banner_btn")
                    ) {
                        Text("Turn Off", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // Timeline Feed List
        if (timelineItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (uiState.isFocusModeEnabled) Icons.Default.CenterFocusStrong else Icons.Default.Inbox,
                        contentDescription = null,
                        modifier = Modifier.height(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (uiState.isFocusModeEnabled) "No Urgent Alerts Right Now" else "No updates in this filter",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (uiState.isFocusModeEnabled) {
                            "Focus Mode is suppressing all non-urgent notifications."
                        } else {
                            "Notifications & call logs will appear automatically as they arrive."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    if (uiState.isFocusModeEnabled) {
                        TextButton(
                            onClick = onDisableFocusMode,
                            modifier = Modifier.testTag("disable_focus_mode_empty_btn")
                        ) {
                            Icon(Icons.Default.CenterFocusStrong, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Disable Focus Mode")
                        }
                    }
                    if (uiState.visibleFeedTypes.size < FeedTypeFilter.entries.size || uiState.selectedCategory != NotificationCategory.ALL) {
                        TextButton(
                            onClick = onResetFeedFilters,
                            modifier = Modifier.testTag("reset_feed_filters_btn")
                        ) {
                            Icon(Icons.Default.FilterList, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Show All Feed Types")
                        }
                    }
                    TextButton(onClick = onResetDemoData) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reload Sample Feed")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(
                    items = timelineItems,
                    key = { it.id }
                ) { item ->
                    TimelineCard(
                        item = item,
                        onOpenApp = onOpenApp,
                        onCallClick = onCallClick,
                        onWhatsAppClick = onWhatsAppClick,
                        onQuickReply = { 
                            activeReplyItem = it
                            onQuickReplyClick(it)
                        },
                        onToggleStar = onToggleStar,
                        onDelete = onDelete,
                        onMarkRead = onMarkRead,
                        onTruecallerClick = onTruecallerClick,
                        onSetTone = { activeToneItem = it },
                        onTogglePin = onTogglePin
                    )
                }
            }
        }
    }

    // Quick Reply Modal
    activeReplyItem?.let { item ->
        QuickReplyDialog(
            item = item,
            aiSuggestions = uiState.aiSuggestions,
            isAiLoading = uiState.isAiLoading,
            aiError = uiState.aiError,
            onDismiss = { 
                activeReplyItem = null
                onQuickReplyDismiss()
            },
            onSendReply = { text ->
                onSendQuickReply(item, text)
                activeReplyItem = null
                onQuickReplyDismiss()
            }
        )
    }

    // Per-Notification Tone Picker Modal
    activeToneItem?.let { item ->
        TonePickerDialog(
            targetTitle = "Set Notification Tone",
            targetSubtitle = "${item.appName} • ${item.title}",
            initialToneUri = item.customToneUri,
            initialToneName = item.customToneName ?: "System Default",
            onDismiss = { activeToneItem = null },
            onToneSelected = { uri, name ->
                onSetNotificationTone(item, uri, name)
                activeToneItem = null
            }
        )
    }

    // Daily Digest Morning Briefing Modal
    if (showDigestDialog) {
        DailyDigestDialog(
            digestState = uiState.dailyDigestState,
            onGenerateDigest = onGenerateDigest,
            onDismiss = { showDigestDialog = false }
        )
    }
}
