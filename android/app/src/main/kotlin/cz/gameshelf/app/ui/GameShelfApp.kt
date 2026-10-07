package cz.gameshelf.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import cz.gameshelf.app.data.auth.SessionState
import cz.gameshelf.app.ui.navigation.AuthGraph
import cz.gameshelf.app.ui.navigation.GameShelfNavHost
import cz.gameshelf.app.ui.navigation.MainGraph
import kotlinx.coroutines.flow.StateFlow

/**
 * Root composable. The session decides the start destination; afterwards every sign-in switches
 * to the main graph and every sign-out (logout, deleted account, failed token refresh) returns to
 * the login screen with the back stack cleared.
 */
@Composable
fun GameShelfApp(session: StateFlow<SessionState>) {
    val sessionState by session.collectAsStateWithLifecycle()
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // The splash screen stays up while the stored session is being read.
        if (sessionState == SessionState.Loading) return@Box

        val isSignedIn = sessionState is SessionState.SignedIn
        val navController = rememberNavController()
        val startDestination = remember { if (isSignedIn) MainGraph else AuthGraph }
        GameShelfNavHost(navController, startDestination)

        LaunchedEffect(isSignedIn) { navController.syncWithSession(isSignedIn) }
    }
}

private fun NavHostController.syncWithSession(isSignedIn: Boolean) {
    val destination = currentBackStackEntry?.destination ?: return
    val inMainGraph = destination.hierarchy.any { it.hasRoute<MainGraph>() }
    if (isSignedIn == inMainGraph) return
    navigate(if (isSignedIn) MainGraph else AuthGraph) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}
