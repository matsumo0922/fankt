package me.matsumo.fankt.fanbox

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FanboxViewModel : ViewModel() {

    private val fanbox = Fanbox()

    val uiState = MutableStateFlow(FanboxUiState(fanbox, "", ""))

    init {
        viewModelScope.launch {
            fanbox.updateCsrfToken()
        }

        viewModelScope.launch(Dispatchers.IO) {
            fanbox.cookies.collect { cookies ->
                uiState.update { state ->
                    state.copy(sessionId = cookies.find { it.name == "FANBOXSESSID" }?.value.orEmpty())
                }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            fanbox.csrfToken.collect {
                uiState.update { state ->
                    state.copy(csrfToken = it.orEmpty())
                }
            }
        }
    }

    fun setSessionId(sessionId: String) {
        viewModelScope.launch {
            fanbox.setFanboxSessionId(sessionId)
        }
    }
}

@Stable
data class FanboxUiState(
    val classInstance: Fanbox,
    val sessionId: String,
    val csrfToken: String,
)
