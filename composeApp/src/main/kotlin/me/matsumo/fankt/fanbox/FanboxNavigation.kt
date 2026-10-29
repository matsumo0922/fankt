package me.matsumo.fankt.fanbox

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/** FANBOX の動作確認画面の navigation route。 */
const val FANBOX_ROUTE = "fanbox"

fun NavController.navigateToFanbox() {
    this.navigate(FANBOX_ROUTE)
}

fun NavGraphBuilder.fanboxScreen(terminate: () -> Unit) {
    composable(FANBOX_ROUTE) {
        FanboxScreen(
            modifier = Modifier.fillMaxSize(),
            terminate = terminate,
        )
    }
}
