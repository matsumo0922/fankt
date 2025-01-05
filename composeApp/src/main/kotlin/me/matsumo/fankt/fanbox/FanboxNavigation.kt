package me.matsumo.fankt.fanbox

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

const val FanboxRoute = "fanbox"

fun NavController.navigateToFanbox() {
    this.navigate(FanboxRoute)
}

fun NavGraphBuilder.fanboxScreen(
    terminate: () -> Unit,
) {
    composable(FanboxRoute) {
        FanboxScreen(
            modifier = Modifier.fillMaxSize(),
            terminate = terminate,
        )
    }
}
