package me.matsumo.fankt.fanbox

import android.app.Application
import androidx.compose.runtime.Stable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class FanboxViewModel @JvmOverloads constructor(
    application: Application,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AndroidViewModel(application) {

    // The dev channel serves the guest built from the latest main.
    private val fanbox = Fanbox(
        guestManifestUrl = "https://matsumo0922.github.io/fankt/zipline/v1-dev/manifest.zipline.json",
        guestTrustedKeys = mapOf(
            "fanboxGuest" to "85e4838d32a4e01a5209c7d4267bbe9349608f1227080c2460246f7ea83a0836".hexToByteArray(),
            "fanboxGuestRecovery" to "5279962d35d36dcbbeea47a25612aa5708ebfe0967cbcc18314ef919d55dcb5e".hexToByteArray(),
        ),
        guestCache = FanboxGuestCache(application, File(application.cacheDir, "fanbox-guest")),
        logLevel = FanboxLogLevel.ALL,
    )

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
            fanbox.cookies.collect { cookies ->
                uiState.update { state ->
                    state.copy(
                        sessionId = cookies.find { it.name == "FANBOXSESSID" }?.value.orEmpty(),
                    )
                }
            }
        }

        viewModelScope.launch(ioDispatcher) {
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
