package me.matsumo.fankt

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideOut
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.IntOffset
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import me.matsumo.fankt.fanbox.fanboxScreen
import me.matsumo.fankt.fanbox.navigateToFanbox
import me.matsumo.fankt.theme.FanktTheme

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            FanktTheme {
                FanktApp()
            }
        }

        splashScreen.setKeepOnScreenCondition { false }
    }
}

@Composable
internal fun FanktApp(
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()

    NavHost(
        modifier = modifier.background(MaterialTheme.colorScheme.surface),
        navController = navController,
        startDestination = NavHostRoute,
        enterTransition = { NavigateAnimation.Horizontal.enter },
        exitTransition = { NavigateAnimation.Horizontal.exit },
        popEnterTransition = { NavigateAnimation.Horizontal.popEnter },
        popExitTransition = { NavigateAnimation.Horizontal.popExit },
    ) {
        navHostScreen(
            navigateToFanbox = navController::navigateToFanbox,
            navigateToFantia = { /* TODO */ }
        )

        fanboxScreen(
            terminate = navController::popBackStack
        )
    }
}

object NavigateAnimation {
    val decelerateEasing = CubicBezierEasing(0.0f, 0.0f, 0.0f, 1.0f)

    object Horizontal {
        private const val DURATION_FADE = 300
        private const val DURATION_SLIDE = 300

        val enter = fadeIn(tween(DURATION_FADE)) + slideIn(
            animationSpec = tween(DURATION_SLIDE, 0, decelerateEasing),
            initialOffset = { IntOffset((it.width * 0.1).toInt(), 0) },
        )

        val popExit = fadeOut(tween(DURATION_FADE)) + slideOut(
            animationSpec = tween(DURATION_SLIDE, 0, decelerateEasing),
            targetOffset = { IntOffset((it.width * 0.1).toInt(), 0) },
        )

        val popEnter = fadeIn(tween(DURATION_FADE)) + slideIn(
            animationSpec = tween(DURATION_SLIDE, 0, decelerateEasing),
            initialOffset = { IntOffset((-it.width * 0.1).toInt(), 0) },
        )

        val exit = fadeOut(tween(DURATION_FADE)) + slideOut(
            animationSpec = tween(DURATION_SLIDE, 0, decelerateEasing),
            targetOffset = { IntOffset((-it.width * 0.1).toInt(), 0) },
        )
    }
}
