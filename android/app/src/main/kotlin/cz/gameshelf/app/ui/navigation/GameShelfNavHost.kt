package cz.gameshelf.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import cz.gameshelf.app.ui.auth.login.LoginRoute
import cz.gameshelf.app.ui.auth.register.RegisterRoute
import cz.gameshelf.app.ui.games.detail.GameDetailRoute
import cz.gameshelf.app.ui.games.edit.GameEditRoute
import cz.gameshelf.app.ui.games.list.GameListRoute
import cz.gameshelf.app.ui.profile.ProfileRoute

@Composable
fun GameShelfNavHost(
    navController: NavHostController,
    startDestination: Any,
) {
    NavHost(navController = navController, startDestination = startDestination) {
        navigation<AuthGraph>(startDestination = Login) {
            composable<Login> { entry ->
                LoginRoute(onRegisterClick = { entry.ifResumed { navController.navigate(Register) } })
            }
            composable<Register> { entry ->
                RegisterRoute(onBack = { entry.ifResumed { navController.popBackStack() } })
            }
        }
        navigation<MainGraph>(startDestination = GameList) {
            composable<GameList> { entry ->
                GameListRoute(
                    onGameClick = { id -> entry.ifResumed { navController.navigate(GameDetail(id)) } },
                    onAddGame = { entry.ifResumed { navController.navigate(GameEdit()) } },
                    // Not `ifResumed`: the result arrives while the scanner activity is still closing.
                    onAddScannedGame = { barcode -> navController.navigate(GameEdit(barcode = barcode)) },
                    onOpenProfile = { entry.ifResumed { navController.navigate(Profile) } },
                )
            }
            composable<GameDetail> { entry ->
                GameDetailRoute(
                    onBack = { entry.ifResumed { navController.popBackStack() } },
                    onEdit = { id -> entry.ifResumed { navController.navigate(GameEdit(id)) } },
                )
            }
            composable<GameEdit> { entry ->
                GameEditRoute(onClose = { entry.ifResumed { navController.popBackStack() } })
            }
            composable<Profile> { entry ->
                ProfileRoute(onBack = { entry.ifResumed { navController.popBackStack() } })
            }
        }
    }
}

/** Ignores repeated taps that would otherwise navigate twice while a transition is running. */
private inline fun NavBackStackEntry.ifResumed(action: () -> Unit) {
    if (lifecycle.currentState == Lifecycle.State.RESUMED) action()
}
