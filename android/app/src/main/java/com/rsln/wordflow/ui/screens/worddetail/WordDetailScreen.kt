package com.rsln.wordflow.ui.screens.worddetail

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rsln.wordflow.WordFlowApp
import com.rsln.wordflow.ui.components.*
import com.rsln.wordflow.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordDetailScreen(
    app: WordFlowApp,
    wordId: Long,
    onNavigateBack: () -> Unit,
    viewModel: WordDetailViewModel = viewModel(
        factory = WordDetailViewModel.Factory(wordId, app.container)
    )
) {
    val wordWithCollections by viewModel.word.collectAsState()
    val isEditing by viewModel.isEditing.collectAsState()
    val editTranslation by viewModel.editTranslation.collectAsState()
    val editExamples by viewModel.editExamples.collectAsState()
    val showDeleteDialog by viewModel.showDeleteDialog.collectAsState()
    val showCollectionDialog by viewModel.showCollectionDialog.collectAsState()
    val deleted by viewModel.deleted.collectAsState()
    val allCollections by viewModel.allCollections.collectAsState()

    LaunchedEffect(deleted) {
        if (deleted) onNavigateBack()
    }

    val wc = wordWithCollections

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text("Word Detail") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isEditing) {
                        IconButton(onClick = viewModel::saveEdit) {
                            Icon(Icons.Filled.Check, contentDescription = "Save", tint = Primary)
                        }
                        IconButton(onClick = viewModel::cancelEdit) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancel")
                        }
                    } else {
                        IconButton(onClick = viewModel::startEdit) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = viewModel::showDelete) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background)
            )
        }
    ) { padding ->
        if (wc == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Primary)
            }
            return@Scaffold
        }

        val word = wc.word

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = word.originalWord,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = OnSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        if (isEditing) {
                            OutlinedTextField(
                                value = editTranslation,
                                onValueChange = viewModel::updateTranslation,
                                label = { Text("Translation") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Primary,
                                    unfocusedBorderColor = OutlineVariant
                                )
                            )
                        } else {
                            Text(
                                text = word.translation,
                                style = MaterialTheme.typography.titleLarge,
                                color = Primary,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        if (word.pronunciation.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = word.pronunciation,
                                style = MaterialTheme.typography.bodyMedium,
                                color = OnSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (word.isLearned) SuccessContainer else SurfaceVariant
                        ),
                        modifier = Modifier.weight(1f),
                        onClick = viewModel::toggleLearned
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (word.isLearned) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (word.isLearned) Success else OnSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = if (word.isLearned) "Learned" else "Not learned",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Visibility,
                                contentDescription = null,
                                tint = OnSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "${word.showCount} views",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Difficulty:", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
                    DifficultyIndicator(difficulty = word.difficulty)
                }
            }

            if (isEditing || word.exampleUsage.isNotBlank()) {
                item {
                    SectionHeader(title = "Usage Examples")
                    if (isEditing) {
                        OutlinedTextField(
                            value = editExamples,
                            onValueChange = viewModel::updateExamples,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            minLines = 3,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Primary,
                                unfocusedBorderColor = OutlineVariant
                            )
                        )
                    } else {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceVariant)
                        ) {
                            Text(
                                text = word.exampleUsage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = OnSurface,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }
            }

            if (word.explanation.isNotBlank()) {
                item {
                    SectionHeader(title = "Explanation")
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceVariant)
                    ) {
                        Text(
                            text = word.explanation,
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurface,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }

            item {
                SectionHeader(
                    title = "Collections",
                    action = {
                        TextButton(onClick = viewModel::showCollections) {
                            Text("Manage", color = Primary)
                        }
                    }
                )
                if (wc.collections.isEmpty()) {
                    Text(
                        text = "Not in any collection",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant
                    )
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(wc.collections, key = { it.id }) { collection ->
                            CollectionChip(
                                name = collection.name,
                                isActive = collection.isActive,
                                onRemove = { viewModel.removeFromCollection(collection.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        ConfirmDialog(
            title = "Delete Word",
            message = "Delete \"${wc?.word?.originalWord}\"? This cannot be undone.",
            onConfirm = viewModel::deleteWord,
            onDismiss = viewModel::hideDelete
        )
    }

    if (showCollectionDialog) {
        val wordCollectionIds = wc?.collections?.map { it.id }?.toSet() ?: emptySet()

        AlertDialog(
            onDismissRequest = viewModel::hideCollections,
            title = { Text("Manage Collections") },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(allCollections, key = { it.id }) { collection ->
                        val isIn = collection.id in wordCollectionIds
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isIn,
                                onCheckedChange = { checked ->
                                    if (checked) viewModel.addToCollection(collection.id)
                                    else viewModel.removeFromCollection(collection.id)
                                },
                                colors = CheckboxDefaults.colors(checkedColor = Primary)
                            )
                            Text(
                                text = collection.name,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::hideCollections) { Text("Done") }
            },
            dismissButton = {},
            shape = RoundedCornerShape(20.dp)
        )
    }
}
