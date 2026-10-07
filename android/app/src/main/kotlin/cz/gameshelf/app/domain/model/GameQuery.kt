package cz.gameshelf.app.domain.model

/** Everything that determines which games the collection list shows and in which order. */
data class GameQuery(
    val search: String = "",
    val filter: GameFilter = GameFilter(),
    val sort: GameSort = GameSort(),
)

/** The games of one platform, a section of the collection list grouped by platform. */
data class PlatformSection(
    val platform: Platform,
    val games: List<Game>,
)
