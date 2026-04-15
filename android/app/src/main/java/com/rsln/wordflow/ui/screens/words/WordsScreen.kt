package com.rsln.wordflow.ui.screens.words

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
fun WordsScreen(
    app: WordFlowApp,
    onNavigateToWord: (Long) -> Unit,
    viewModel: WordsViewModel = viewModel(factory = WordsViewModel.Factory(app.container))
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val words by viewModel.words.collectAsState()
    val totalCount by viewModel.totalCount.collectAsState()
    val learnedCount by viewModel.learnedCount.collectAsState()
    val deleteTarget by viewModel.deleteTarget.collectAsState()

    Scaffold(containerColor = Background) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    text = "Words",
                    style = MaterialTheme.typography.headlineLarge,
                    color = OnSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$totalCount total, $learnedCount learned",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = viewModel::updateSearch,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search words or translations...") },
                    leadingIcon = {
                        Icon(Icons.Outlined.Search, contentDescription = null, tint = OnSurfaceVariant)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearch("") }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = OutlineVariant,
                        focusedContainerColor = CardSurface,
                        unfocusedContainerColor = CardSurface
                    )
                )
            }

            if (words.isEmpty()) {
                item {
                    EmptyState(
                        icon = if (searchQuery.isNotBlank()) Icons.Outlined.SearchOff else Icons.Outlined.MenuBook,
                        title = if (searchQuery.isNotBlank()) "No matches" else "No words yet",
                        subtitle = if (searchQuery.isNotBlank()) "Try a different search" else "Add words to build your vocabulary"
                    )
                }
            }

            items(words, key = { it.id }) { word ->
                WordCard(
                    word = word,
                    onClick = { onNavigateToWord(word.id) },
                    trailing = {
                        IconButton(
                            onClick = { viewModel.confirmDelete(word) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "Delete",
                                tint = OnSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                )
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }

    deleteTarget?.let { target ->
        ConfirmDialog(
            title = "Delete Word",
            message = "Delete \"${target.originalWord}\"? This will remove it from all collections.",
            onConfirm = viewModel::deleteWord,
            onDismiss = viewModel::cancelDelete
        )
    }
}
