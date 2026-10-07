package cz.gameshelf.app.ui.navigation

import kotlinx.serialization.Serializable

/** Login and registration; start destination while signed out. */
@Serializable
data object AuthGraph

@Serializable
data object Login

@Serializable
data object Register

/** Everything behind authentication. */
@Serializable
data object MainGraph

@Serializable
data object GameList

@Serializable
data class GameDetail(val gameId: String)

/** Add (`gameId == null`) or edit a game. */
@Serializable
data class GameEdit(val gameId: String? = null)

@Serializable
data object Profile
