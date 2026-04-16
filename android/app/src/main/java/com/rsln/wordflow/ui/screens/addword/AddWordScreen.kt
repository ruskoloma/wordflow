package com.rsln.wordflow.ui.screens.addword

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rsln.wordflow.WordFlowApp
import com.rsln.wordflow.data.remote.AiModel
import com.rsln.wordflow.ui.components.*
import com.rsln.wordflow.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWordScreen(
    app: WordFlowApp,
    viewModel: AddWordViewModel = viewModel(factory = AddWordViewModel.Factory(app.container))
) {
    val inputText by viewModel.inputText.collectAsState()
    val isTranslating by viewModel.isTranslating.collectAsState()
    val message by viewModel.message.collectAsState()
    val allCollections by viewModel.allCollections.collectAsState()
    val preAppliedCollection by viewModel.preAppliedCollection.collectAsState()
    val selectedAiModel by viewModel.selectedAiModel.collectAsState()
    var showModelPicker by remember { mutableStateOf(false) }

    // Check for pending generated words from Collections -> Generate flow
    LaunchedEffect(Unit) {
        val pending = app.container.pendingGeneratedWords
        val collName = app.container.pendingCollectionName
        if (pending != null && collName != null) {
            viewModel.loadGeneratedDrafts(collName, pending)
            app.container.pendingGeneratedWords = null
            app.container.pendingCollectionName = null
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Background
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
                    text = "Add Words",
                    style = MaterialTheme.typography.headlineLarge,
                    color = OnSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Enter words or phrases, one per line",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            item {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = viewModel::updateInput,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp),
                    placeholder = {
                        Text(
                            "hello\ngoodbye\nthank you",
                            color = Outline.copy(alpha = 0.6f)
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = OutlineVariant,
                        focusedContainerColor = CardSurface,
                        unfocusedContainerColor = CardSurface
                    )
                )
            }

            item {
                OutlinedButton(
                    onClick = { showModelPicker = true },
                    enabled = !isTranslating,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(vertical = 12.dp, horizontal = 14.dp)
                ) {
                    Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Model: ${AiModel.displayName(selectedAiModel)}")
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = viewModel::translate,
                        enabled = inputText.isNotBlank() && !isTranslating,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Primary,
                            contentColor = OnPrimary
                        ),
                        contentPadding = PaddingValues(vertical = 14.dp)
                    ) {
                        if (isTranslating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = OnPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Translating...")
                        } else {
                            Icon(Icons.Outlined.Translate, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Translate")
                        }
                    }

                    if (viewModel.drafts.isNotEmpty()) {
                        OutlinedButton(
                            onClick = viewModel::clearDrafts,
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(vertical = 14.dp, horizontal = 16.dp)
                        ) {
                            Icon(Icons.Outlined.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            if (viewModel.drafts.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Results",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = OnSurface
                        )
                        val saveable = viewModel.drafts.count {
                            it.status == DraftStatus.READY || it.status == DraftStatus.EXISTING || it.status == DraftStatus.SAVED
                        }
                        if (saveable > 0) {
                            Button(
                                onClick = viewModel::saveAll,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Success,
                                    contentColor = OnPrimary
                                ),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save All ($saveable)")
                            }
                        }
                    }
                }

                if (preAppliedCollection != null) {
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
                                    Icons.Outlined.FolderCopy,
                                    contentDescription = null,
                                    tint = Primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "All words will be added to \"$preAppliedCollection\"",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnPrimaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                itemsIndexed(viewModel.drafts) { index, draft ->
                    DraftCard(
                        draft = draft,
                        index = index,
                        allCollections = allCollections,
                        onUpdateDraft = viewModel::updateDraft,
                        onToggleCollection = viewModel::toggleCollectionForDraft,
                        onAddNewCollection = viewModel::addNewCollectionToDraft,
                        onRemoveNewCollection = viewModel::removeNewCollectionFromDraft,
                        onRemoveDraft = viewModel::removeDraft
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(4.dp)) }
        }
    }

    if (showModelPicker) {
        AiModelPickerDialog(
            selectedModel = selectedAiModel,
            title = "Translation model",
            onSelect = viewModel::selectAiModel,
            onDismiss = { showModelPicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DraftCard(
    draft: WordDraft,
    index: Int,
    allCollections: List<com.rsln.wordflow.data.local.entity.CollectionEntity>,
    onUpdateDraft: (Int, WordDraft) -> Unit,
    onToggleCollection: (Int, Long) -> Unit,
    onAddNewCollection: (Int, String) -> Unit,
    onRemoveNewCollection: (Int, String) -> Unit,
    onRemoveDraft: (Int) -> Unit
) {
    val statusColor = when (draft.status) {
        DraftStatus.LOADING -> Outline
        DraftStatus.READY -> Primary
        DraftStatus.EXISTING -> Secondary
        DraftStatus.SAVED -> Success
        DraftStatus.ERROR -> Error
        DraftStatus.IDLE -> Outline
    }

    var showCollectionInput by remember { mutableStateOf(false) }
    var newCollectionName by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Header row: word name + status badge + close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = draft.input,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = OnSurface,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = when (draft.status) {
                            DraftStatus.LOADING -> "Loading..."
                            DraftStatus.READY -> "Ready"
                            DraftStatus.EXISTING -> "Existing"
                            DraftStatus.SAVED -> "Saved"
                            DraftStatus.ERROR -> "Error"
                            DraftStatus.IDLE -> "Idle"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = { onRemoveDraft(index) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Remove",
                        tint = OnSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (draft.status == DraftStatus.LOADING) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = Primary,
                    trackColor = PrimaryContainer
                )
            }

            if (draft.status == DraftStatus.ERROR) {
                Text(
                    text = draft.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = Error
                )
            }

            if (draft.status == DraftStatus.READY || draft.status == DraftStatus.EXISTING || draft.status == DraftStatus.SAVED) {
                if (draft.suggestion.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = TertiaryContainer
                    ) {
                        Text(
                            text = "Did you mean: ${draft.suggestion} (${draft.suggestionTranslation})?",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = draft.translation,
                    onValueChange = { onUpdateDraft(index, draft.copy(translation = it)) },
                    label = { Text("Translation") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = draft.status != DraftStatus.SAVED,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = OutlineVariant
                    ),
                    textStyle = MaterialTheme.typography.bodyMedium
                )

                OutlinedTextField(
                    value = draft.examples,
                    onValueChange = { onUpdateDraft(index, draft.copy(examples = it)) },
                    label = { Text("Examples") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 2,
                    enabled = draft.status != DraftStatus.SAVED,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = OutlineVariant
                    ),
                    textStyle = MaterialTheme.typography.bodySmall
                )

                if (draft.pronunciation.isNotBlank()) {
                    Text(
                        text = "Pronunciation: ${draft.pronunciation}",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Difficulty:", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                    DifficultyIndicator(difficulty = draft.difficulty)
                }

                // Collections section
                Text(
                    text = "Collections",
                    style = MaterialTheme.typography.labelMedium,
                    color = OnSurfaceVariant
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(allCollections, key = { it.id }) { collection ->
                        val selected = collection.id in draft.selectedCollectionIds
                        Surface(
                            modifier = Modifier.clickable { onToggleCollection(index, collection.id) },
                            shape = RoundedCornerShape(20.dp),
                            color = if (selected) PrimaryContainer else SurfaceVariant,
                            contentColor = if (selected) OnPrimaryContainer else OnSurfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (selected) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                                Text(collection.name, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    items(draft.newCollectionNames) { name ->
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = PrimaryContainer,
                            contentColor = OnPrimaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(name, style = MaterialTheme.typography.labelSmall)
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Remove",
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable { onRemoveNewCollection(index, name) }
                                )
                            }
                        }
                    }
                    items(draft.suggestedCollections.filter { it !in draft.newCollectionNames }) { suggested ->
                        Surface(
                            modifier = Modifier.clickable { onAddNewCollection(index, suggested) },
                            shape = RoundedCornerShape(20.dp),
                            color = SurfaceVariant,
                            contentColor = OnSurfaceVariant
                        ) {
                            Text(
                                text = "+ $suggested",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                    }
                    item {
                        Surface(
                            modifier = Modifier.clickable { showCollectionInput = true },
                            shape = RoundedCornerShape(20.dp),
                            color = SurfaceVariant,
                            contentColor = OnSurfaceVariant
                        ) {
                            Text(
                                text = "+ New",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = showCollectionInput) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newCollectionName,
                            onValueChange = { newCollectionName = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Collection name") },
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                if (newCollectionName.isNotBlank()) {
                                    onAddNewCollection(index, newCollectionName.trim())
                                    newCollectionName = ""
                                    showCollectionInput = false
                                }
                            }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Primary,
                                unfocusedBorderColor = OutlineVariant
                            )
                        )
                        IconButton(onClick = {
                            if (newCollectionName.isNotBlank()) {
                                onAddNewCollection(index, newCollectionName.trim())
                                newCollectionName = ""
                            }
                            showCollectionInput = false
                        }) {
                            Icon(Icons.Filled.Check, contentDescription = "Add", tint = Primary)
                        }
                    }
                }
            }
        }
    }
}
