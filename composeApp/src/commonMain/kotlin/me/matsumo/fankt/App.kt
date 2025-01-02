package me.matsumo.fankt

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.aakira.napier.Napier

@Composable
internal fun FanktApp() {
    val fanbox = Fanbox()

    LaunchedEffect(true) {
        Napier.d { "start" }
        runCatching {
            fanbox.getNewsLetters()
        }.fold(
            onSuccess = { "success" },
            onFailure = { it.toString() },
        ).also {
            Napier.d { it }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "fankt",
        )
    }
}
