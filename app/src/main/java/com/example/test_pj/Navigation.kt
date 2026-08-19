package com.example.test_pj

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.test_pj.ui.ask.AskScreen
import com.example.test_pj.ui.core.CoreMemoryScreen
import com.example.test_pj.ui.home.HomeScreen
import com.example.test_pj.ui.memories.MemoryListScreen

@Composable
fun MainNavigation() {
    val backStack = rememberNavBackStack(Home)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<Home> {
                HomeScreen(
                    onNavigateToMemories = { backStack.add(MemoryList) },
                    onNavigateToAsk = { backStack.add(Ask) },
                    onNavigateToCoreMemory = { backStack.add(CoreMemory) },
                    modifier = Modifier.safeDrawingPadding(),
                )
            }
            entry<MemoryList> {
                MemoryListScreen(
                    onBack = { backStack.removeLastOrNull() },
                    modifier = Modifier.safeDrawingPadding(),
                )
            }
            entry<Ask> {
                AskScreen(
                    onBack = { backStack.removeLastOrNull() },
                    modifier = Modifier.safeDrawingPadding(),
                )
            }
            entry<CoreMemory> {
                CoreMemoryScreen(
                    onBack = { backStack.removeLastOrNull() },
                    modifier = Modifier.safeDrawingPadding(),
                )
            }
        },
    )
}
