package com.rsln.wordflow.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rsln.wordflow.WordFlowApp
import com.rsln.wordflow.data.remote.AiModel
import com.rsln.wordflow.ui.screens.addword.AddWordScreen
import com.rsln.wordflow.ui.screens.addword.GeneratedWord
import com.rsln.wordflow.ui.screens.collectiondetail.CollectionDetailScreen
import com.rsln.wordflow.ui.screens.collections.CollectionsScreen
import com.rsln.wordflow.ui.screens.learning.LearningScreen
import com.rsln.wordflow.ui.screens.settings.SettingsScreen
import com.rsln.wordflow.ui.screens.worddetail.WordDetailScreen
import com.rsln.wordflow.ui.screens.words.WordsScreen
import com.rsln.wordflow.ui.theme.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

sealed class Screen(val route: String, val title: String) {
    data object Learning : Screen("learning", "Home")
    data object AddWord : Screen("add_word", "Add Words")
    data object Collections : Screen("collections", "Collections")
    data object Words : Screen("words", "Words")
    data object Settings : Screen("settings", "Settings")
    data object WordDetail : Screen("word_detail/{wordId}", "Word") {
        fun createRoute(wordId: Long) = "word_detail/$wordId"
    }
    data object CollectionDetail : Screen("collection_detail/{collectionId}", "Collection") {
        fun createRoute(collectionId: Long) = "collection_detail/$collectionId"
    }
    data object GenerateCollection : Screen("generate_collection/{name}/{description}/{count}/{difficulty}", "Generate") {
        fun createRoute(name: String, description: String, count: Int, difficulty: Int): String {
            val encodedName = java.net.URLEncoder.encode(name, "UTF-8")
            val encodedDesc = java.net.URLEncoder.encode(description.ifBlank { " " }, "UTF-8")
            return "generate_collection/$encodedName/$encodedDesc/$count/$difficulty"
        }
    }
}

data class BottomNavItem(
    val screen: Screen,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val label: String
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Learning, Icons.Filled.Home, Icons.Outlined.Home, "Home"),
    BottomNavItem(Screen.AddWord, Icons.Filled.Add, Icons.Outlined.Add, "Add"),
    BottomNavItem(Screen.Collections, Icons.Filled.FolderCopy, Icons.Outlined.FolderCopy, "Collections"),
    BottomNavItem(Screen.Words, Icons.AutoMirrored.Filled.MenuBook, Icons.AutoMirrored.Outlined.MenuBook, "Words"),
    BottomNavItem(Screen.Settings, Icons.Filled.Settings, Icons.Outlined.Settings, "Settings"),
)

@Composable
fun WordFlowNavHost(app: WordFlowApp, startRoute: String? = null) {
    val navController = rememberNavController()

    // Navigate to requested route on first composition (e.g. from widget)
    LaunchedEffect(startRoute) {
        if (startRoute != null) {
            navController.navigate(startRoute) {
                popUpTo(Screen.Learning.route) { saveState = true }
                launchSingleTop = true
            }
        }
    }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in bottomNavItems.map { it.screen.route }

    Scaffold(
        containerColor = Background,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = CardSurface,
                    tonalElevation = 0.dp
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = currentRoute == item.screen.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (currentRoute != item.screen.route) {
                                    navController.navigate(item.screen.route) {
                                        popUpTo(Screen.Learning.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label
                                )
                            },
                            label = {
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Primary,
                                selectedTextColor = Primary,
                                unselectedIconColor = OnSurfaceVariant,
                                unselectedTextColor = OnSurfaceVariant,
                                indicatorColor = PrimaryContainer
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Learning.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Learning.route) {
                LearningScreen(
                    app = app,
                    onNavigateToWord = { navController.navigate(Screen.WordDetail.createRoute(it)) },
                    onNavigateToCollection = { navController.navigate(Screen.CollectionDetail.createRoute(it)) }
                )
            }
            composable(Screen.AddWord.route) {
                AddWordScreen(app = app)
            }
            composable(Screen.Collections.route) {
                CollectionsScreen(
                    app = app,
                    onNavigateToDetail = { navController.navigate(Screen.CollectionDetail.createRoute(it)) },
                    onNavigateToGenerate = { name, desc, count, difficulty ->
                        navController.navigate(Screen.GenerateCollection.createRoute(name, desc, count, difficulty))
                    }
                )
            }
            composable(Screen.Words.route) {
                WordsScreen(
                    app = app,
                    onNavigateToWord = { navController.navigate(Screen.WordDetail.createRoute(it)) }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(app = app)
            }
            composable(
                route = Screen.GenerateCollection.route,
                arguments = listOf(
                    navArgument("name") { type = NavType.StringType },
                    navArgument("description") { type = NavType.StringType },
                    navArgument("count") { type = NavType.IntType },
                    navArgument("difficulty") { type = NavType.IntType }
                )
            ) { backStackEntry ->
                val name = java.net.URLDecoder.decode(
                    backStackEntry.arguments?.getString("name") ?: "", "UTF-8"
                )
                val description = java.net.URLDecoder.decode(
                    backStackEntry.arguments?.getString("description") ?: "", "UTF-8"
                ).trim()
                val count = backStackEntry.arguments?.getInt("count") ?: 25
                val difficulty = backStackEntry.arguments?.getInt("difficulty") ?: 5

                var error by remember { mutableStateOf<String?>(null) }
                val scope = rememberCoroutineScope()

                LaunchedEffect(Unit) {
                    scope.launch {
                        val model = AiModel.normalize(app.container.settingsDataStore.defaultAiModel.first())
                        val result = app.container.openRouterService.generateCollectionWords(
                            name, description, count, difficulty, model
                        )
                        result.fold(
                            onSuccess = { aiResults ->
                                val words = aiResults.map { ai ->
                                    GeneratedWord(
                                        word = ai.suggestion, // word stored in suggestion field
                                        translation = ai.translation,
                                        examples = ai.examples.joinToString("\n"),
                                        pronunciation = ai.pronunciation,
                                        reason = ai.reason,
                                        difficulty = ai.difficulty
                                    )
                                }
                                app.container.pendingCollectionName = name
                                app.container.pendingGeneratedWords = words
                                navController.navigate(Screen.AddWord.route) {
                                    popUpTo(Screen.Collections.route) { inclusive = false }
                                    launchSingleTop = true
                                }
                            },
                            onFailure = {
                                error = it.message ?: "Generation failed"
                            }
                        )
                    }
                }

                // Loading screen
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (error != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                tint = Error,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Generation Failed",
                                style = MaterialTheme.typography.titleMedium,
                                color = OnSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = error ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedButton(
                                onClick = { navController.popBackStack() },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Go Back")
                            }
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Primary)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Generating \"$name\"",
                                style = MaterialTheme.typography.titleMedium,
                                color = OnSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Creating $count words with AI...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = OnSurfaceVariant
                            )
                        }
                    }
                }
            }
            composable(
                route = Screen.WordDetail.route,
                arguments = listOf(navArgument("wordId") { type = NavType.LongType })
            ) { backStackEntry ->
                val wordId = backStackEntry.arguments?.getLong("wordId") ?: return@composable
                WordDetailScreen(
                    app = app,
                    wordId = wordId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.CollectionDetail.route,
                arguments = listOf(navArgument("collectionId") { type = NavType.LongType })
            ) { backStackEntry ->
                val collectionId = backStackEntry.arguments?.getLong("collectionId") ?: return@composable
                CollectionDetailScreen(
                    app = app,
                    collectionId = collectionId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToWord = { navController.navigate(Screen.WordDetail.createRoute(it)) }
                )
            }
        }
    }
}
