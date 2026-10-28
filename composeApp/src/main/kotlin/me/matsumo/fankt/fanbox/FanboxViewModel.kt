package me.matsumo.fankt.fanbox

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FanboxViewModel(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val fanbox = Fanbox(FanboxLogLevel.ALL)

    val uiState = MutableStateFlow(
        FanboxUiState(
            classInstance = fanbox,
            sessionId = "",
            csrfToken = "",
        ),
    )

    init {
        viewModelScope.launch {
            fanbox.updateCsrfToken()
        }

        viewModelScope.launch(ioDispatcher) {
            fanbox
                .cookies
                .collect { cookies ->
                    uiState.update { state ->
                        state.copy(
                            sessionId = cookies
                                .find { it.name == "FANBOXSESSID" }
                                ?.value
                                .orEmpty(),
                        )
                    }
                }
        }

        viewModelScope.launch(ioDispatcher) {
            fanbox
                .csrfToken
                .collect {
                    uiState.update { state ->
                        state.copy(csrfToken = it.orEmpty())
                    }
                }
        }
    }

    fun setSessionId(sessionId: String) {
        viewModelScope.launch {
            fanbox.setFanboxSessionId(sessionId)
            fanbox.updateCsrfToken()
        }
    }

    override fun onCleared() {
        fanbox.close()
        super.onCleared()
    }
}

/** FANBOX の認証情報と API クライアントを保持する画面状態。 */
@Stable
data class FanboxUiState(
    val classInstance: Fanbox,
    val sessionId: String,
    val csrfToken: String,
)
