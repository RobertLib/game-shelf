package cz.gameshelf.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import cz.gameshelf.app.data.auth.SessionState
import cz.gameshelf.app.ui.GameShelfApp
import cz.gameshelf.app.ui.theme.GameShelfTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val session = (application as GameShelfApplication).container.sessionManager.state
        // Keep the splash until we know whether to show the login or the collection.
        splashScreen.setKeepOnScreenCondition { session.value == SessionState.Loading }

        if (savedInstanceState == null) requestLocalNetworkAccessIfNeeded()

        setContent {
            GameShelfTheme {
                GameShelfApp(session)
            }
        }
    }
}
