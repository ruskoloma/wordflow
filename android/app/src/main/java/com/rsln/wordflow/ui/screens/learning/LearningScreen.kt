package com.rsln.wordflow.ui.screens.learning

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rsln.wordflow.WordFlowApp
import com.rsln.wordflow.data.local.entity.WordEntity
import com.rsln.wordflow.ui.components.*
import com.rsln.wordflow.ui.theme.*
import kotlin.math.abs

private enum class StudyMode(val title: String) {
    Flashcards("Flashcards"),
    TypeAnswer("Type Answer"),
    MultipleChoice("Multiple Choice")
}

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
    val studyWords by viewModel.studyWords.collectAsState()
    var activeMode by remember { mutableStateOf<StudyMode?>(null) }

    activeMode?.let { mode ->
        StudySessionScreen(
            mode = mode,
            words = studyWords,
            onClose = { activeMode = null },
            onRecordAnswer = viewModel::recordAnswer
        )
        return
    }

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

        item {
            Spacer(modifier = Modifier.height(4.dp))
            SectionHeader(title = "Study Modes")
            StudyModesGrid(
                wordCount = studyWords.size,
                onStart = { activeMode = it }
            )
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

@Composable
private fun StudyModesGrid(
    wordCount: Int,
    onStart: (StudyMode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StudyModeCard(
            title = "Flashcards",
            subtitle = "Reveal the answer, then mark remembered or forgot.",
            icon = Icons.Outlined.Style,
            enabled = wordCount > 0,
            onClick = { onStart(StudyMode.Flashcards) }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StudyModeCard(
                title = "Type",
                subtitle = "Type the translation from memory.",
                icon = Icons.Outlined.Keyboard,
                enabled = wordCount > 0,
                modifier = Modifier.weight(1f),
                onClick = { onStart(StudyMode.TypeAnswer) }
            )
            StudyModeCard(
                title = "Choice",
                subtitle = "Pick from similar saved translations.",
                icon = Icons.Outlined.Checklist,
                enabled = wordCount >= 4,
                modifier = Modifier.weight(1f),
                onClick = { onStart(StudyMode.MultipleChoice) }
            )
        }
        if (wordCount in 1..3) {
            Text(
                text = "Add at least 4 words to unlock multiple choice.",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun StudyModeCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(22.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = OnSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariant
            )
        }
    }
}

@Composable
private fun StudySessionScreen(
    mode: StudyMode,
    words: List<WordEntity>,
    onClose: () -> Unit,
    onRecordAnswer: (WordEntity, Boolean) -> Unit
) {
    val sessionWords = remember(mode, words) {
        words.shuffled().take(10)
    }
    var index by remember(mode, sessionWords) { mutableStateOf(0) }
    var correctCount by remember(mode, sessionWords) { mutableStateOf(0) }
    var finished by remember(mode, sessionWords) { mutableStateOf(sessionWords.isEmpty()) }

    val currentWord = sessionWords.getOrNull(index)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(mode.title, style = MaterialTheme.typography.titleLarge, color = OnSurface)
                    Text(
                        text = if (finished) "Session complete" else "${index + 1} of ${sessionWords.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant
                    )
                }
            }
        }

        if (finished) {
            item {
                StudyFinishedCard(
                    correctCount = correctCount,
                    totalCount = sessionWords.size,
                    onRestart = {
                        index = 0
                        correctCount = 0
                        finished = sessionWords.isEmpty()
                    },
                    onClose = onClose
                )
            }
        } else if (currentWord != null) {
            item {
                LinearProgressIndicator(
                    progress = { (index + 1).toFloat() / sessionWords.size.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                    color = Primary,
                    trackColor = SurfaceVariant
                )
            }
            item {
                when (mode) {
                    StudyMode.Flashcards -> FlashcardPrompt(
                        word = currentWord,
                        onAnswer = { remembered ->
                            if (remembered) correctCount += 1
                            onRecordAnswer(currentWord, remembered)
                            if (index == sessionWords.lastIndex) finished = true else index += 1
                        }
                    )
                    StudyMode.TypeAnswer -> TypeAnswerPrompt(
                        word = currentWord,
                        onAnswer = { correct ->
                            if (correct) correctCount += 1
                            onRecordAnswer(currentWord, correct)
                            if (index == sessionWords.lastIndex) finished = true else index += 1
                        }
                    )
                    StudyMode.MultipleChoice -> MultipleChoicePrompt(
                        word = currentWord,
                        allWords = words,
                        onAnswer = { correct ->
                            if (correct) correctCount += 1
                            onRecordAnswer(currentWord, correct)
                            if (index == sessionWords.lastIndex) finished = true else index += 1
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FlashcardPrompt(
    word: WordEntity,
    onAnswer: (Boolean) -> Unit
) {
    var revealed by remember(word.id) { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = word.originalWord,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = OnSurface,
                textAlign = TextAlign.Center
            )
            if (revealed) {
                Text(
                    text = word.translation,
                    style = MaterialTheme.typography.titleMedium,
                    color = OnSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { onAnswer(false) }, shape = RoundedCornerShape(12.dp)) {
                        Text("Forgot")
                    }
                    Button(onClick = { onAnswer(true) }, shape = RoundedCornerShape(12.dp)) {
                        Text("Remembered")
                    }
                }
            } else {
                Button(onClick = { revealed = true }, shape = RoundedCornerShape(12.dp)) {
                    Text("Show answer")
                }
            }
        }
    }
}

@Composable
private fun TypeAnswerPrompt(
    word: WordEntity,
    onAnswer: (Boolean) -> Unit
) {
    var answer by remember(word.id) { mutableStateOf("") }
    var checked by remember(word.id) { mutableStateOf<Boolean?>(null) }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Translate", style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant)
            Text(
                text = word.originalWord,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = OnSurface
            )
            OutlinedTextField(
                value = answer,
                onValueChange = {
                    answer = it
                    checked = null
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Type translation") },
                singleLine = true,
                readOnly = checked != null,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = OutlineVariant,
                    focusedContainerColor = CardSurface,
                    unfocusedContainerColor = CardSurface
                )
            )
            checked?.let { correct ->
                Text(
                    text = if (correct) "Correct" else "Answer: ${word.translation}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (correct) Success else Error
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { onAnswer(false) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Skip")
                }
                Button(
                    onClick = {
                        val correct = isAcceptedAnswer(answer, word.translation)
                        if (checked == null) {
                            checked = correct
                        } else {
                            onAnswer(checked == true)
                        }
                    },
                    enabled = answer.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (checked == null) "Check" else "Next")
                }
            }
        }
    }
}

@Composable
private fun MultipleChoicePrompt(
    word: WordEntity,
    allWords: List<WordEntity>,
    onAnswer: (Boolean) -> Unit
) {
    val choices = remember(word.id, allWords) { buildChoices(word, allWords) }
    var selected by remember(word.id) { mutableStateOf<String?>(null) }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Choose the translation", style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant)
            Text(
                text = word.originalWord,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = OnSurface
            )
            choices.forEach { choice ->
                val isSelected = selected == choice
                val isCorrect = normalized(choice) == normalized(word.translation)
                OutlinedButton(
                    onClick = { selected = choice },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = when {
                            selected == null -> CardSurface
                            isCorrect -> SuccessContainer
                            isSelected -> ErrorContainer
                            else -> CardSurface
                        },
                        contentColor = OnSurface
                    )
                ) {
                    Text(choice, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                }
            }
            Button(
                onClick = { onAnswer(normalized(selected.orEmpty()) == normalized(word.translation)) },
                enabled = selected != null,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Next")
            }
        }
    }
}

@Composable
private fun StudyFinishedCard(
    correctCount: Int,
    totalCount: Int,
    onRestart: () -> Unit,
    onClose: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = Success, modifier = Modifier.size(42.dp))
            Text("Done", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
            Text(
                text = "$correctCount / $totalCount remembered",
                style = MaterialTheme.typography.titleMedium,
                color = OnSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onClose, shape = RoundedCornerShape(12.dp)) {
                    Text("Home")
                }
                Button(onClick = onRestart, shape = RoundedCornerShape(12.dp), enabled = totalCount > 0) {
                    Text("Restart")
                }
            }
        }
    }
}

private fun buildChoices(target: WordEntity, allWords: List<WordEntity>): List<String> {
    val targetAnswer = target.translation.trim()
    val distractors = allWords
        .filter { it.id != target.id && it.translation.isNotBlank() }
        .filter { normalized(it.translation) != normalized(targetAnswer) }
        .shuffled()
        .sortedWith(
            compareBy<WordEntity> { abs(it.difficulty - target.difficulty) }
                .thenBy { abs(it.translation.length - targetAnswer.length) }
        )
        .map { it.translation.trim() }
        .distinctBy { normalized(it) }
        .take(3)

    return (distractors + targetAnswer).shuffled()
}

private fun isAcceptedAnswer(input: String, expected: String): Boolean {
    val normalizedInput = normalized(input)
    val accepted = expected
        .split(",", ";", "/", "\n")
        .map { normalized(it) }
        .filter { it.isNotBlank() } + normalized(expected)

    return normalizedInput in accepted.distinct()
}

private fun normalized(value: String): String =
    value.trim().lowercase().replace(Regex("\\s+"), " ")
