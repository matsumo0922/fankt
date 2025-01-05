package me.matsumo.fankt.fanbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import me.matsumo.fankt.fanbox.components.FanboxRequestsContent
import me.matsumo.fankt.fanbox.components.setFanboxSessionId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FanboxScreen(
    terminate: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FanboxViewModel = viewModel(FanboxViewModel::class),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    val state = rememberTopAppBarState()
    val behavior = TopAppBarDefaults.pinnedScrollBehavior(state)

    var sessionId by remember { mutableStateOf(uiState.sessionId) }

    LaunchedEffect(uiState.sessionId) {
        sessionId = uiState.sessionId
    }

    Scaffold(
        modifier = modifier.nestedScroll(behavior.nestedScrollConnection),
        topBar = {
            FanboxTopAppBar(
                modifier = Modifier.fillMaxWidth(),
                scrollBehavior = behavior,
                onBackClicked = terminate,
            )
        },
        bottomBar = {
            FanboxBottomBar(
                modifier = Modifier.fillMaxWidth(),
                sessionId = sessionId,
                onSessionIdChanged = {
                    scope.launch {
                        sessionId = it
                        setFanboxSessionId(sessionId)

                        if (sessionId.isNotBlank()) {
                            viewModel.setSessionId(sessionId)
                        }
                    }
                },
            )
        },
    ) {
        FanboxRequestsContent(
            modifier = Modifier
                .padding(it)
                .fillMaxWidth(),
            classInstance = uiState.classInstance,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FanboxTopAppBar(
    onBackClicked: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
) {
    CenterAlignedTopAppBar(
        modifier = modifier,
        title = {
            Text("FANBOX")
        },
        navigationIcon = {
            IconButton(onBackClicked) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                )
            }
        },
        scrollBehavior = scrollBehavior,
    )
}

@Composable
private fun FanboxBottomBar(
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
                .padding(bottom = 8.dp)
                .fillMaxWidth(),
            value = sessionId,
            onValueChange = onSessionIdChanged,
            singleLine = true,
            placeholder = { Text("FANBOXSESSID") },
        )
    }
}
