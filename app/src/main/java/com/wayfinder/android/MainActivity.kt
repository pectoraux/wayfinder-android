package com.wayfinder.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.wayfinder.android.feature.auth.LoginScreen
import com.wayfinder.android.feature.outcome.OutcomeScreen
import com.wayfinder.android.feature.strategy.StrategyScreen
import com.wayfinder.android.ui.theme.WayfinderTheme

/**
 * Single-activity Compose host.
 *
 * Routes:
 *  - "login"               → email + password sign-in
 *  - "strategy"            → adopted strategy + blockers + actions
 *  - "outcome/{strategyId}" → expected / observed / evaluation
 *
 * The app is a thin client; no intelligence is computed locally.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WayfinderTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    NavHost(
                        navController = navController,
                        startDestination = "login"
                    ) {
                        composable("login") {
                            LoginScreen(
                                onLoggedIn = {
                                    navController.navigate("strategy") {
                                        popUpTo("login") { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }
                        composable("strategy") {
                            StrategyScreen(
                                onViewOutcome = { strategyId ->
                                    navController.navigate("outcome/$strategyId")
                                },
                                onLoggedOut = {
                                    navController.navigate("login") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("outcome/{strategyId}") { backStackEntry ->
                            val strategyId =
                                backStackEntry.arguments?.getString("strategyId").orEmpty()
                            OutcomeScreen(
                                strategyId = strategyId,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
