package com.rsln.wordflow.ui.screens.learning

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rsln.wordflow.WordFlowApp
import com.rsln.wordflow.ui.components.*
import com.rsln.wordflow.ui.theme.*

@Composable
fun LearningScreen(
    app: WordFlowApp,
    onNavigateToWord: (Long) -> Unit,
    onNavigateToCollection: (Long) -> Unit,
    viewModel: LearningViewModel = viewModel(factory = LearningViewModel.Factory(app.container))
) {
    val totalCount by viewModel.totalCount.collectAsState()
    val learnedCount by viewModel.learnedCount.collectAsState()
    val weekCount by viewModel.weekCount.collectAsState()
    val activeCollectionCount by viewModel.activeCollectionCount.collectAsState()
    val recentWords by viewModel.recentWords.collectAsState()
    val activeCollections by viewModel.activeCollections.collectAsState()
    val practiceAll by viewModel.practiceAll.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "WordFlow",
                style = MaterialTheme.typography.headlineLarge,
                color = OnSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Your vocabulary journey",
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    label = "Total",
                    value = "$totalCount",
                    icon = Icons.Outlined.Translate,
                    color = Primary,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "Learned",
                    value = "$learnedCount",
                    icon = Icons.Outlined.CheckCircle,
                    color = Success,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    label = "This week",
                    value = "$weekCount",
                    icon = Icons.Outlined.CalendarMonth,
                    color = Secondary,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "Active sets",
                    value = "$activeCollectionCount",
                    icon = Icons.Outlined.FolderCopy,
                    color = Tertiary,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Practice all toggle + Active Collections
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (practiceAll) "Practicing all words" else "Active Collections",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = OnSurface
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "All words",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (practiceAll) Primary else OnSurfaceVariant
                    )
                    Switch(
                        checked = practiceAll,
                        onCheckedChange = viewModel::togglePracticeAll,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = OnPrimary,
                            checkedTrackColor = Primary,
                            uncheckedThumbColor = Outline,
                            uncheckedTrackColor = SurfaceVariant
                        ),
                        modifier = Modifier.height(28.dp)
                    )
                }
            }
        }

        if (!practiceAll && activeCollections.isNotEmpty()) {
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(activeCollections, key = { it.id }) { collection ->
                        CollectionChip(
                            name = collection.name,
                            isActive = true,
                            onClick = { onNavigateToCollection(collection.id) }
                        )
                    }
                }
            }
        }

        if (!practiceAll && activeCollections.isEmpty()) {
            item {
                Text(
                    text = "No active collections — toggle \"All words\" or activate a collection",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }

        if (practiceAll) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = PrimaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Outlined.AllInclusive,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Widget and notifications will use all $totalCount words",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnPrimaryContainer
                        )
                    }
                }
            }
        }

        if (totalCount > 0) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                SectionHeader(title = "Recent Words")
            }
        }

        if (recentWords.isEmpty() && totalCount == 0) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                EmptyState(
                    icon = Icons.Outlined.Translate,
                    title = "No words yet",
                    subtitle = "Start building your vocabulary by adding words"
                )
            }
        } else {
            items(recentWords, key = { it.id }) { word ->
                WordCard(
                    word = word,
                    onClick = { onNavigateToWord(word.id) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(4.dp)) }
    }
}
