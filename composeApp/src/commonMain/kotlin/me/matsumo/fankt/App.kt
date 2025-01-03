package me.matsumo.fankt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.matsumo.fankt.fanbox.FanboxRequestsContent
import me.matsumo.fankt.fanbox.getFanboxSessionId
import me.matsumo.fankt.fanbox.setFanboxSessionId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FanktApp(
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val state = rememberTopAppBarState()
    val behavior = TopAppBarDefaults.pinnedScrollBehavior(state)

    val classInstance = remember { Fanbox() }
    var sessionId by remember { mutableStateOf("") }

    LaunchedEffect(true) {
        sessionId = getFanboxSessionId().also {
            if (it.isNotBlank()) {
                classInstance.setFanboxSessionId(it)
            }
        }
    }

    Scaffold(
        modifier = modifier.nestedScroll(behavior.nestedScrollConnection),
        topBar = {
            AppTopAppBar(
                modifier = Modifier.fillMaxWidth(),
                scrollBehavior = behavior,
            )
        },
        bottomBar = {
            AppBottomBar(
                modifier = Modifier.fillMaxWidth(),
                sessionId = sessionId,
                onSessionIdChanged = {
                    scope.launch {
                        sessionId = it
                        setFanboxSessionId(sessionId)

                        if (sessionId.isNotBlank()) {
                            classInstance.setFanboxSessionId(sessionId)
                        }
                    }
                },
            )
        }
    ) {
        FanboxRequestsContent(
            modifier = Modifier
                .padding(it)
                .fillMaxWidth(),
            classInstance = classInstance,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopAppBar(
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
) {
    CenterAlignedTopAppBar(
        modifier = modifier,
        title = {
            Text("Fankt")
        },
        scrollBehavior = scrollBehavior,
    )
}

@Composable
private fun AppBottomBar(
    sessionId: String,
    onSessionIdChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .navigationBarsPadding()
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HorizontalDivider()

        OutlinedTextField(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            value = sessionId,
            onValueChange = onSessionIdChanged,
            singleLine = true,
            placeholder = { Text("FANBOXSESSID") },
        )

        Button(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp)
                .fillMaxWidth(),
            onClick = { onSessionIdChanged(sessionId) },
        ) {
            Text("Save")
        }
    }
}