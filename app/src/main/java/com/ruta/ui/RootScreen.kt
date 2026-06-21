package com.ruta.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ruta.ui.home.BrowserViewModel
import com.ruta.ui.home.HomeScreen
import com.ruta.ui.settings.AboutScreen
import com.ruta.ui.settings.SettingsScreen
import com.ruta.ui.theme.RutaTheme
import com.ruta.ui.theme.accentForProfile

@Composable
fun RootScreen(
    initialUrl: String?,
    onExit: () -> Unit,
) {
    val browserViewModel: BrowserViewModel = hiltViewModel()
    val settings by browserViewModel.settings.collectAsStateWithLifecycle()
    val activeTab by browserViewModel.activeTab.collectAsStateWithLifecycle()

    LaunchedEffect(initialUrl) {
        if (!initialUrl.isNullOrBlank()) browserViewModel.openInput(initialUrl)
    }

    RutaTheme(
        themeMode = settings.themeMode,
        dynamicColor = settings.dynamicColor,
        accent = accentForProfile(activeTab?.profileId ?: ""),
    ) {
        val navController = rememberNavController()
        NavHost(
            navController = navController,
            startDestination = "home",
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(220)) },
            exitTransition = { fadeOut(tween(140)) },
            popEnterTransition = { fadeIn(tween(140)) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(220)) },
        ) {
            composable("home") {
                HomeScreen(
                    viewModel = browserViewModel,
                    onOpenSettings = { navController.navigate("settings") },
                    onExit = onExit,
                )
            }
            composable("settings") {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenAbout = { navController.navigate("about") },
                )
            }
            composable("about") {
                AboutScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
