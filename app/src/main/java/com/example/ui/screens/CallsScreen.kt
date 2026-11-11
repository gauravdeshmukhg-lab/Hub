package com.example.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.CallType
import com.example.data.model.UnifiedTimelineItem
import com.example.ui.components.DialerDialog
import com.example.ui.components.TimelineCard

@Composable
fun CallsScreen(
    timelineItems: List<UnifiedTimelineItem>,
    onCallClick: (String) -> Unit,
    onWhatsAppClick: (String) -> Unit,
    onDelete: (UnifiedTimelineItem) -> Unit,
    onMarkRead: (UnifiedTimelineItem) -> Unit,
    onTruecallerClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf<CallType?>(null) }
    var showDialer by remember { mutableStateOf(false) }

    val callLogs = remember(timelineItems, selectedFilter, searchQuery) {
        timelineItems.filterIsInstance<UnifiedTimelineItem.CallLogItem>()
            .filter { item ->
                if (selectedFilter == null) true else item.callType == selectedFilter
            }
            .filter { item ->
                if (searchQuery.isBlank()) true else {
                    val q = searchQuery.lowercase()
                    (item.contactName?.lowercase()?.contains(q) == true) || item.phoneNumber.contains(q)
                }
            }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showDialer = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("open_dialer_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Dialpad,
                    contentDescription = "Open Dialer"
                )
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Screen Title
            Text(
                text = "Call History & Dialer",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search contact name or phone number...") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = "Search")
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .testTag("calls_search_bar")
            )

            // Filter Chips (All, Missed, Incoming, Outgoing)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == null,
                    onClick = { selectedFilter = null },
                    label = { Text("All") },
                    leadingIcon = { Icon(Icons.Default.FilterList, contentDescription = null) },
                    modifier = Modifier.testTag("call_filter_all")
                )

                FilterChip(
                    selected = selectedFilter == CallType.MISSED,
                    onClick = { selectedFilter = if (selectedFilter == CallType.MISSED) null else CallType.MISSED },
                    label = { Text("Missed") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.CallMissed,
                            contentDescription = null,
                            tint = Color(0xFFE53935)
                        )
                    },
                    modifier = Modifier.testTag("call_filter_missed")
                )

                FilterChip(
                    selected = selectedFilter == CallType.INCOMING,
                    onClick = { selectedFilter = if (selectedFilter == CallType.INCOMING) null else CallType.INCOMING },
                    label = { Text("Incoming") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.CallReceived,
                            contentDescription = null,
                            tint = Color(0xFF43A047)
                        )
                    },
                    modifier = Modifier.testTag("call_filter_incoming")
                )

                FilterChip(
                    selected = selectedFilter == CallType.OUTGOING,
                    onClick = { selectedFilter = if (selectedFilter == CallType.OUTGOING) null else CallType.OUTGOING },
                    label = { Text("Outgoing") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.CallMade,
                            contentDescription = null,
                            tint = Color(0xFF1E88E5)
                        )
                    },
                    modifier = Modifier.testTag("call_filter_outgoing")
                )
            }

            // Calls List
            if (callLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.PhoneInTalk,
                            contentDescription = null,
                            modifier = Modifier.height(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No call records found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tap the dialer button below to start a call or launch WhatsApp.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(callLogs, key = { it.id }) { item ->
                        TimelineCard(
                            item = item,
                            onOpenApp = {},
                            onCallClick = onCallClick,
                            onWhatsAppClick = onWhatsAppClick,
                            onQuickReply = {},
                            onToggleStar = {},
                            onDelete = onDelete,
                            onMarkRead = onMarkRead,
                            onTruecallerClick = onTruecallerClick
                        )
                    }
                }
            }
        }
    }

    if (showDialer) {
        DialerDialog(
            onDismiss = { showDialer = false },
            onCallClick = onCallClick,
            onWhatsAppClick = onWhatsAppClick,
            onTruecallerClick = onTruecallerClick
        )
    }
}
