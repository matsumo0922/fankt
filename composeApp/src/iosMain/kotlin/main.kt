import androidx.compose.ui.window.ComposeUIViewController
import me.matsumo.fankt.FanktApp
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController = ComposeUIViewController {
    FanktApp()
}
