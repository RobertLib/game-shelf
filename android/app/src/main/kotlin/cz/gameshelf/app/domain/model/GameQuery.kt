package cz.gameshelf.app.domain.model

/** Everything that determines which games the collection list shows and in which order. */
data class GameQuery(
    val search: String = "",
    val filter: GameFilter = GameFilter(),
    val sort: GameSort = GameSort(),
)
