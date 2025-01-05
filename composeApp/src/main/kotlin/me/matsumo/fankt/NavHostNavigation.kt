package me.matsumo.fankt

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

const val NavHostRoute = "navHost"

fun NavGraphBuilder.navHostScreen(
    navigateToFanbox: () -> Unit,
    navigateToFantia: () -> Unit
) {
    composable(NavHostRoute) {
        NavHostScreen(
            modifier = Modifier.fillMaxSize(),
            navigateToFanbox = navigateToFanbox,
            navigateToFantia = navigateToFantia,
        )
    }
}
