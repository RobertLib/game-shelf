package cz.gameshelf.app.ui.games.edit.search

import cz.gameshelf.app.domain.model.Platform

/** How the platform of a game picked in the database search is decided. */
sealed interface PlatformChoice {
    /** Fill in [platform]; `null` leaves the form's Platform empty. */
    data class Decided(val platform: Platform?) : PlatformChoice

    /** The game came out on several platforms: ask which one the user's copy is for. */
    data class Ask(val options: List<Platform>) : PlatformChoice

    companion object {
        /**
         * A platform already chosen in the form stays; otherwise the game's only platform is taken, and
         * with several the user is asked. A game without platforms leaves Platform empty.
         */
        fun of(formPlatform: Platform?, gamePlatforms: List<Platform>): PlatformChoice = when {
            formPlatform != null -> Decided(formPlatform)
            gamePlatforms.size > 1 -> Ask(gamePlatforms)
            else -> Decided(gamePlatforms.singleOrNull())
        }
    }
}
