package me.matsumo.fankt

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/** 画面一覧の navigation route。 */
const val NAV_HOST_ROUTE = "navHost"

fun NavGraphBuilder.navHostScreen(navigateToFanbox: () -> Unit, navigateToFantia: () -> Unit) {
    composable(NAV_HOST_ROUTE) {
        NavHostScreen(
            modifier = Modifier.fillMaxSize(),
            navigateToFanbox = navigateToFanbox,
            navigateToFantia = navigateToFantia,
        )
    }
}
