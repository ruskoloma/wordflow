package com.rsln.wordflow.ui.screens.settings

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rsln.wordflow.WordFlowApp
import com.rsln.wordflow.data.remote.AiModel
import com.rsln.wordflow.notification.NotificationScheduler
import com.rsln.wordflow.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    app: WordFlowApp,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(app.container))
) {
    val context = LocalContext.current
    val aiModel by viewModel.aiModel.collectAsState()
    val wordsPerWeek by viewModel.wordsPerWeek.collectAsState()
    val notificationsPerDay by viewModel.notificationsPerDay.collectAsState()
    val activeStartHour by viewModel.activeStartHour.collectAsState()
    val activeEndHour by viewModel.activeEndHour.collectAsState()
    val widgetRefreshSeconds by viewModel.widgetRefreshSeconds.collectAsState()
    val testResult by viewModel.testResult.collectAsState()
    val isTesting by viewModel.isTesting.collectAsState()

    // Local state for text fields to avoid cursor issues from async DataStore round-trips
    val apiKeyFlow by viewModel.apiKey.collectAsState()
    val nicknameFlow by viewModel.nickname.collectAsState()
    var apiKeyLocal by remember { mutableStateOf("") }
    var nicknameLocal by remember { mutableStateOf("") }
    var apiKeyInit by remember { mutableStateOf(false) }
    var nicknameInit by remember { mutableStateOf(false) }

    // Initialize local state from DataStore once loaded
    if (!apiKeyInit && apiKeyFlow.isNotEmpty()) { apiKeyLocal = apiKeyFlow; apiKeyInit = true }
    if (apiKeyInit.not() && apiKeyFlow.isEmpty()) { apiKeyInit = true }
    if (!nicknameInit && nicknameFlow.isNotEmpty()) { nicknameLocal = nicknameFlow; nicknameInit = true }
    if (nicknameInit.not() && nicknameFlow.isEmpty()) { nicknameInit = true }

    var showApiKey by remember { mutableStateOf(false) }
    var showModelPicker by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let { viewModel.exportCsv(context, it) }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importCsv(context, it) }
    }

    LaunchedEffect(testResult) {
        testResult?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearTestResult()
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
                    text = "Settings",
                    style = MaterialTheme.typography.headlineLarge,
                    color = OnSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // AI Settings
            item {
                Text(
                    text = "AI Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Primary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            item {
                SettingsCard {
                    OutlinedTextField(
                        value = apiKeyLocal,
                        onValueChange = { apiKeyLocal = it; viewModel.setApiKey(it) },
                        label = { Text("OpenRouter API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showApiKey = !showApiKey }) {
                                Icon(
                                    if (showApiKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = "Toggle visibility"
                                )
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary,
                            unfocusedBorderColor = OutlineVariant
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Model", style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant)
                            val modelName = AiModel.AVAILABLE_MODELS.find { it.id == aiModel }?.name ?: aiModel
                            Text(modelName, style = MaterialTheme.typography.bodyMedium)
                        }
                        OutlinedButton(
                            onClick = { showModelPicker = true },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Change")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = viewModel::testAiConnection,
                        enabled = !isTesting,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Primary,
                            contentColor = OnPrimary
                        )
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = OnPrimary, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Test Connection")
                    }
                }
            }

            // Learning Settings
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Learning",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Primary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            item {
                SettingsCard {
                    SettingsSlider(
                        label = "Words per week",
                        value = wordsPerWeek,
                        range = 5f..50f,
                        onValueChange = { viewModel.setWordsPerWeek(it.toInt()) }
                    )
                    SettingsSlider(
                        label = "Notifications per day",
                        value = notificationsPerDay,
                        range = 0f..10f,
                        onValueChange = { viewModel.setNotificationsPerDay(it.toInt()) }
                    )
                    SettingsSlider(
                        label = "Active start hour",
                        value = activeStartHour,
                        range = 0f..23f,
                        displayValue = "${activeStartHour}:00",
                        onValueChange = { viewModel.setActiveStartHour(it.toInt()) }
                    )
                    SettingsSlider(
                        label = "Active end hour",
                        value = activeEndHour,
                        range = 0f..23f,
                        displayValue = "${activeEndHour}:00",
                        onValueChange = { viewModel.setActiveEndHour(it.toInt()) }
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            NotificationScheduler.schedule(
                                context, notificationsPerDay, activeStartHour, activeEndHour
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Secondary)
                    ) {
                        Text("Apply Notification Schedule")
                    }
                }
            }

            // Widget Settings
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Widget",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Primary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            item {
                SettingsCard {
                    SettingsSlider(
                        label = "Refresh interval",
                        value = widgetRefreshSeconds,
                        range = 30f..45f,
                        displayValue = "${widgetRefreshSeconds}s",
                        onValueChange = { viewModel.setWidgetRefreshSeconds(it.toInt()) }
                    )
                }
            }

            // Cloud Settings
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Cloud Sync",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Primary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            item {
                SettingsCard {
                    OutlinedTextField(
                        value = nicknameLocal,
                        onValueChange = { nicknameLocal = it; viewModel.setNickname(it) },
                        label = { Text("Your nickname") },
                        placeholder = { Text("Enter a username") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary,
                            unfocusedBorderColor = OutlineVariant
                        )
                    )

                    if (nicknameLocal.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Outlined.CloudDone,
                                contentDescription = null,
                                tint = Success,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Auto-syncing as \"$nicknameLocal\"",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceVariant
                            )
                        }
                        Text(
                            text = "Changes sync automatically when online",
                            style = MaterialTheme.typography.bodySmall,
                            color = Outline
                        )
                    }
                }
            }

            // Data Tools
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Data Tools",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Primary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            item {
                SettingsCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { exportLauncher.launch("wordflow_export.csv") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export CSV")
                        }
                        OutlinedButton(
                            onClick = { importLauncher.launch(arrayOf("text/csv", "text/*")) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Outlined.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import CSV")
                        }
                    }
                }
            }

            // About
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "About",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Primary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            item {
                SettingsCard {
                    Text(
                        text = "WordFlow v1.0.0",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Personal English-Russian vocabulary learning app",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    if (showModelPicker) {
        AlertDialog(
            onDismissRequest = { showModelPicker = false },
            title = { Text("Select Model") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AiModel.AVAILABLE_MODELS.forEach { model ->
                        val isSelected = model.id == aiModel
                        Card(
                            onClick = {
                                viewModel.setAiModel(model.id)
                                showModelPicker = false
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) PrimaryContainer else SurfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(selectedColor = Primary)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(model.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text(model.id, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showModelPicker = false }) { Text("Close") }
            },
            dismissButton = {},
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
fun SettingsSlider(
    label: String,
    value: Int,
    range: ClosedFloatingPointRange<Float>,
    displayValue: String = value.toString(),
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = OnSurface)
            Text(displayValue, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = Primary)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = onValueChange,
            valueRange = range,
            steps = (range.endInclusive - range.start).toInt() - 1,
            colors = SliderDefaults.colors(
                thumbColor = Primary,
                activeTrackColor = Primary,
                inactiveTrackColor = PrimaryContainer
            )
        )
    }
}
