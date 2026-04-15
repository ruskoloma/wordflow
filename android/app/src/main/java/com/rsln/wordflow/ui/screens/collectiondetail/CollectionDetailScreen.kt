package com.rsln.wordflow.ui.screens.collectiondetail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rsln.wordflow.WordFlowApp
import com.rsln.wordflow.ui.components.*
import com.rsln.wordflow.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionDetailScreen(
    app: WordFlowApp,
    collectionId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToWord: (Long) -> Unit,
    viewModel: CollectionDetailViewModel = viewModel(
        factory = CollectionDetailViewModel.Factory(collectionId, app.container)
    )
) {
    val collectionWithWords by viewModel.collectionWithWords.collectAsState()
    val showDeleteDialog by viewModel.showDeleteDialog.collectAsState()
    val showAddWordDialog by viewModel.showAddWordDialog.collectAsState()
    val deleted by viewModel.deleted.collectAsState()
    val allWords by viewModel.allWords.collectAsState()

    LaunchedEffect(deleted) {
        if (deleted) onNavigateBack()
    }

    val cw = collectionWithWords

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text(cw?.collection?.name ?: "Collection") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::showAddWord) {
                        Icon(Icons.Outlined.PlaylistAdd, contentDescription = "Add word")
                    }
                    IconButton(onClick = viewModel::showDelete) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background)
            )
        }
    ) { padding ->
        if (cw == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Primary)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = cw.collection.name,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Active", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                                Spacer(modifier = Modifier.width(8.dp))
                                Switch(
                                    checked = cw.collection.isActive,
                                    onCheckedChange = viewModel::toggleActive,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = OnPrimary,
                                        checkedTrackColor = Primary
                                    )
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        val learned = cw.words.count { it.isLearned }
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(
                                text = "${cw.words.size} words",
                                style = MaterialTheme.typography.bodyMedium,
                                color = OnSurfaceVariant
                            )
                            Text(
                                text = "$learned learned",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Success
                            )
                        }
                        if (cw.words.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = if (cw.words.isNotEmpty()) learned.toFloat() / cw.words.size else 0f,
                                modifier = Modifier.fillMaxWidth(),
                                color = Primary,
                                trackColor = PrimaryContainer,
                            )
                        }
                    }
                }
            }

            if (cw.words.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.MenuBook,
                        title = "No words in this collection",
                        subtitle = "Add words using the + button above"
                    )
                }
            }

            items(cw.words, key = { it.id }) { word ->
                WordCard(
                    word = word,
                    onClick = { onNavigateToWord(word.id) },
                    trailing = {
                        IconButton(
                            onClick = { viewModel.removeWordFromCollection(word.id) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Outlined.RemoveCircleOutline,
                                contentDescription = "Remove",
                                tint = OnSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                )
            }
        }
    }

    if (showDeleteDialog) {
        ConfirmDialog(
            title = "Delete Collection",
            message = "Delete \"${cw?.collection?.name}\"? Orphaned words will also be removed.",
            onConfirm = viewModel::deleteCollection,
            onDismiss = viewModel::hideDelete
        )
    }

    if (showAddWordDialog) {
        val currentWordIds = cw?.words?.map { it.id }?.toSet() ?: emptySet()
        val availableWords = allWords.filter { it.id !in currentWordIds }

        AlertDialog(
            onDismissRequest = viewModel::hideAddWord,
            title = { Text("Add Word to Collection") },
            text = {
                if (availableWords.isEmpty()) {
                    Text("No more words available to add", color = OnSurfaceVariant)
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(availableWords, key = { it.id }) { word ->
                            Card(
                                onClick = {
                                    viewModel.addWordToCollection(word.id)
                                    viewModel.hideAddWord()
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = SurfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = word.originalWord,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = word.translation,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = OnSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = viewModel::hideAddWord) { Text("Close") }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
}
