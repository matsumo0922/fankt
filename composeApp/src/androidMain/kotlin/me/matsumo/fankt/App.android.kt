package me.matsumo.fankt

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import me.matsumo.fankt.theme.FanktTheme

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        Napier.base(DebugAntilog())

        enableEdgeToEdge()
        setContent {
            FanktTheme {
                FanktApp()
            }
        }

        splashScreen.setKeepOnScreenCondition { false }
    }
}
