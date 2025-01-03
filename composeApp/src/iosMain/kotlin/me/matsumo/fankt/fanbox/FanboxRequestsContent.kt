package me.matsumo.fankt.fanbox

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.matsumo.fankt.Fanbox

@Composable
actual fun FanboxRequestsContent(
    classInstance: Fanbox,
    modifier: Modifier,
) {
    Box(
        modifier = modifier.padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Operation confirmed on iOS has not yet been implemented.",
        )
    }
}