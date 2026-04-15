package com.rsln.wordflow.ui.screens.collections

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rsln.wordflow.WordFlowApp
import com.rsln.wordflow.ui.components.*
import com.rsln.wordflow.ui.theme.*

data class PresetCollection(
    val name: String,
    val icon: String,
    val description: String,
    val wordCount: Int = 25
)

val presetCollections = listOf(
    PresetCollection("Travel Essentials", "✈", "Airport, hotel, directions, restaurants"),
    PresetCollection("Business English", "💼", "Meetings, emails, presentations, negotiations"),
    PresetCollection("Food & Cooking", "🍳", "Ingredients, kitchen tools, recipes, dining"),
    PresetCollection("Technology", "💻", "Software, hardware, internet, AI terms"),
    PresetCollection("Emotions & Feelings", "💭", "Expressing mood, reactions, mental states"),
    PresetCollection("Nature & Weather", "🌿", "Seasons, animals, landscapes, climate"),
    PresetCollection("Health & Body", "🏥", "Medical terms, symptoms, fitness, wellness"),
    PresetCollection("Finance & Money", "💰", "Banking, investing, taxes, budgeting"),
    PresetCollection("Academic Writing", "📝", "Essays, research, argumentation, citations"),
    PresetCollection("Idioms & Slang", "🗣", "Common expressions, phrasal verbs, colloquial"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionsScreen(
    app: WordFlowApp,
    onNavigateToDetail: (Long) -> Unit,
    onNavigateToGenerate: (String, String, Int, Int) -> Unit = { _, _, _, _ -> },
    viewModel: CollectionsViewModel = viewModel(factory = CollectionsViewModel.Factory(app.container))
) {
    val collectionsWithWords by viewModel.collectionsWithWords.collectAsState()
    val showCreateDialog by viewModel.showCreateDialog.collectAsState()
    val deleteTarget by viewModel.deleteTarget.collectAsState()

    var newCollectionName by remember { mutableStateOf("") }
    var showExplore by remember { mutableStateOf(false) }
    var showGenerateDialog by remember { mutableStateOf(false) }

    // Generate dialog state
    var genName by remember { mutableStateOf("") }
    var genDescription by remember { mutableStateOf("") }
    var genWordCount by remember { mutableStateOf(25f) }
    var genDifficulty by remember { mutableStateOf(5f) }

    Scaffold(
        containerColor = Background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = viewModel::showCreate,
                containerColor = Primary,
                contentColor = OnPrimary,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "New collection")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = "Collections",
                    style = MaterialTheme.typography.headlineLarge,
                    color = OnSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${collectionsWithWords.size} collection${if (collectionsWithWords.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceVariant
                )
            }

            // Explore & Generate row
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showExplore = !showExplore },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Primary
                        )
                    ) {
                        Icon(Icons.Outlined.Explore, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (showExplore) "Hide Topics" else "Explore Topics")
                    }
                    Button(
                        onClick = { showGenerateDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("AI Generate")
                    }
                }
            }

            // Explore section
            if (showExplore) {
                item {
                    Text(
                        text = "Popular Topics",
                        style = MaterialTheme.typography.titleSmall,
                        color = OnSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                items(presetCollections.chunked(2)) { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { preset ->
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = CardSurface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                                onClick = {
                                    genName = preset.name
                                    genDescription = preset.description
                                    genWordCount = preset.wordCount.toFloat()
                                    showGenerateDialog = true
                                }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = preset.icon,
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = preset.name,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = OnSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = preset.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = OnSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        // Fill remaining space if odd number
                        if (row.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            // My Collections
            if (collectionsWithWords.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "My Collections",
                        style = MaterialTheme.typography.titleSmall,
                        color = OnSurfaceVariant
                    )
                }
            }

            if (collectionsWithWords.isEmpty() && !showExplore) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.FolderCopy,
                        title = "No collections",
                        subtitle = "Create one or explore topics above"
                    )
                }
            }

            items(collectionsWithWords, key = { it.collection.id }) { cw ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                    onClick = { onNavigateToDetail(cw.collection.id) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = cw.collection.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = OnSurface
                            )
                            val learned = cw.words.count { it.isLearned }
                            Text(
                                text = "${cw.words.size} words, $learned learned",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceVariant
                            )
                        }

                        Switch(
                            checked = cw.collection.isActive,
                            onCheckedChange = { viewModel.toggleActive(cw.collection.id, it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = OnPrimary,
                                checkedTrackColor = Primary,
                                uncheckedThumbColor = Outline,
                                uncheckedTrackColor = SurfaceVariant
                            )
                        )

                        IconButton(onClick = { viewModel.confirmDelete(cw) }) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "Delete",
                                tint = OnSurfaceVariant
                            )
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(72.dp)) }
        }
    }

    // Create collection dialog
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = {
                viewModel.hideCreate()
                newCollectionName = ""
            },
            title = { Text("New Collection") },
            text = {
                OutlinedTextField(
                    value = newCollectionName,
                    onValueChange = { newCollectionName = it },
                    placeholder = { Text("Collection name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = OutlineVariant
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.createCollection(newCollectionName)
                        newCollectionName = ""
                    },
                    enabled = newCollectionName.isNotBlank()
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.hideCreate()
                    newCollectionName = ""
                }) { Text("Cancel") }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Delete confirmation
    deleteTarget?.let { target ->
        val orphanCount = target.words.count { word ->
            collectionsWithWords.count { cw -> word in cw.words } <= 1
        }
        ConfirmDialog(
            title = "Delete Collection",
            message = "Delete \"${target.collection.name}\"?" +
                    if (orphanCount > 0) "\n$orphanCount word${if (orphanCount > 1) "s" else ""} will also be deleted."
                    else "",
            onConfirm = viewModel::deleteCollection,
            onDismiss = viewModel::cancelDelete
        )
    }

    // AI Generate dialog
    if (showGenerateDialog) {
        AlertDialog(
            onDismissRequest = {
                showGenerateDialog = false
                genName = ""
                genDescription = ""
                genWordCount = 25f
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = Primary, modifier = Modifier.size(22.dp))
                    Text("Generate Collection")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = genName,
                        onValueChange = { genName = it },
                        label = { Text("Collection name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary,
                            unfocusedBorderColor = OutlineVariant
                        )
                    )
                    OutlinedTextField(
                        value = genDescription,
                        onValueChange = { genDescription = it },
                        label = { Text("Description (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        minLines = 2,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary,
                            unfocusedBorderColor = OutlineVariant
                        )
                    )
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Words to generate", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${genWordCount.toInt()}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Primary
                            )
                        }
                        Slider(
                            value = genWordCount,
                            onValueChange = { genWordCount = it },
                            valueRange = 10f..100f,
                            steps = 8,
                            colors = SliderDefaults.colors(
                                thumbColor = Primary,
                                activeTrackColor = Primary,
                                inactiveTrackColor = PrimaryContainer
                            )
                        )
                    }
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Difficulty level", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = when (genDifficulty.toInt()) {
                                    in 1..3 -> "${genDifficulty.toInt()} · Easy"
                                    in 4..6 -> "${genDifficulty.toInt()} · Medium"
                                    in 7..8 -> "${genDifficulty.toInt()} · Hard"
                                    else -> "${genDifficulty.toInt()} · Advanced"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Primary
                            )
                        }
                        Slider(
                            value = genDifficulty,
                            onValueChange = { genDifficulty = it },
                            valueRange = 1f..10f,
                            steps = 8,
                            colors = SliderDefaults.colors(
                                thumbColor = Primary,
                                activeTrackColor = Primary,
                                inactiveTrackColor = PrimaryContainer
                            )
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showGenerateDialog = false
                        onNavigateToGenerate(genName, genDescription, genWordCount.toInt(), genDifficulty.toInt())
                        genName = ""
                        genDescription = ""
                        genWordCount = 25f
                        genDifficulty = 5f
                    },
                    enabled = genName.isNotBlank(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Generate")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showGenerateDialog = false
                    genName = ""
                    genDescription = ""
                    genWordCount = 25f
                    genDifficulty = 5f
                }) { Text("Cancel") }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
}
