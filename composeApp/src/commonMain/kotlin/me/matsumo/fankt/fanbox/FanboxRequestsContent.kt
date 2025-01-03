package me.matsumo.fankt.fanbox

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.matsumo.fankt.Fanbox

@Composable
expect fun FanboxRequestsContent(
    classInstance: Fanbox,
    modifier: Modifier = Modifier,
)