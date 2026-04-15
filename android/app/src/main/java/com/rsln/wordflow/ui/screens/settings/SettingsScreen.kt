package com.rsln.wordflow.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rsln.wordflow.WordFlowApp
import com.rsln.wordflow.notification.NotificationScheduler
import com.rsln.wordflow.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    app: WordFlowApp,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(app.container))
) {
    val context = LocalContext.current
    val wordsPerWeek by viewModel.wordsPerWeek.collectAsState()
    val notificationsPerDay by viewModel.notificationsPerDay.collectAsState()
    val activeStartHour by viewModel.activeStartHour.collectAsState()
    val activeEndHour by viewModel.activeEndHour.collectAsState()
    val widgetRefreshSeconds by viewModel.widgetRefreshSeconds.collectAsState()
    val syncMessage by viewModel.syncMessage.collectAsState()
    val currentEmail by viewModel.currentEmail.collectAsState()

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

    LaunchedEffect(syncMessage) {
        syncMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSyncMessage()
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

            // Account — signed in state + sync + sign out
            item {
                Text(
                    text = "Account",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Primary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            item {
                SettingsCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Outlined.CloudDone,
                            contentDescription = null,
                            tint = Success,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Signed in as",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceVariant
                            )
                            Text(
                                text = currentEmail ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        OutlinedButton(
                            onClick = viewModel::signOut,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Sign out")
                        }
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

            // About & Updates
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
                val updateState by viewModel.updateState.collectAsState()

                SettingsCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "WordFlow v${viewModel.getCurrentVersion(context)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "English-Russian vocabulary app",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    when (val state = updateState) {
                        is SettingsViewModel.UpdateState.Idle -> {
                            OutlinedButton(
                                onClick = { viewModel.checkForUpdate(context) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Outlined.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Check for updates")
                            }
                        }
                        is SettingsViewModel.UpdateState.Checking -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Checking...", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                            }
                        }
                        is SettingsViewModel.UpdateState.UpToDate -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = Success, modifier = Modifier.size(18.dp))
                                Text("You're up to date", style = MaterialTheme.typography.bodySmall, color = Success)
                            }
                        }
                        is SettingsViewModel.UpdateState.Available -> {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = PrimaryContainer)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Update available: v${state.release.versionName}",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = OnPrimaryContainer
                                    )
                                    if (state.release.body.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = state.release.body.take(200),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = OnPrimaryContainer.copy(alpha = 0.8f),
                                            maxLines = 3
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { viewModel.downloadUpdate(context, state.release) },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Primary),
                                            enabled = state.release.apkUrl != null
                                        ) {
                                            Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Download & Install")
                                        }
                                        TextButton(onClick = { viewModel.dismissUpdate() }) {
                                            Text("Later")
                                        }
                                    }
                                }
                            }
                        }
                        is SettingsViewModel.UpdateState.Downloading -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Primary)
                                Text("Downloading update...", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                            }
                        }
                        is SettingsViewModel.UpdateState.Error -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = Error, modifier = Modifier.size(18.dp))
                                Text(state.message, style = MaterialTheme.typography.bodySmall, color = Error, modifier = Modifier.weight(1f))
                                TextButton(onClick = { viewModel.checkForUpdate(context) }) { Text("Retry") }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
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
