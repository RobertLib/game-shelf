package cz.gameshelf.app

import androidx.activity.ComponentActivity

/** The release API is a public host; no local-network access is needed (see the debug variant). */
@Suppress("UnusedReceiverParameter")
internal fun ComponentActivity.requestLocalNetworkAccessIfNeeded() = Unit
